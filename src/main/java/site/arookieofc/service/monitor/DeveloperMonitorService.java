package site.arookieofc.service.monitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import site.arookieofc.controller.VO.DeveloperMetricsVO;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class DeveloperMonitorService {
    private final RequestMetricsCollector requestMetricsCollector;
    private final SystemMetricsWebSocketHandler webSocketHandler;
    private final MetricsSseBroadcaster metricsSseBroadcaster;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final RabbitTemplate rabbitTemplate;
    private final HttpClient httpClient;

    @Value("${app.logging.es.scheme:http}")
    private String esScheme;

    @Value("${app.logging.es.host:localhost}")
    private String esHost;

    @Value("${app.logging.es.port:9200}")
    private int esPort;

    @Value("${app.monitoring.ws.sample-rate:1}")
    private int wsSampleRate;

    @Value("${app.monitoring.ws.degrade-threshold:1500}")
    private int wsDegradeThreshold;

    private final AtomicLong lastRequestTotal = new AtomicLong(0);
    private final AtomicLong lastCollectTs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong pushTicks = new AtomicLong(0);
    private final AtomicReference<DeveloperMetricsVO> latestMetrics = new AtomicReference<>();

    // Health check cache (15s TTL to avoid opening connections every 2s)
    private static final long HEALTH_CACHE_TTL_MS = 15_000;
    private volatile DeveloperMetricsVO.MiddlewareStatusVO cachedMysql;
    private volatile DeveloperMetricsVO.MiddlewareStatusVO cachedRabbit;
    private volatile DeveloperMetricsVO.MiddlewareStatusVO cachedEs;
    private volatile long healthCacheExpiresAt;

    @Autowired
    public DeveloperMonitorService(RequestMetricsCollector requestMetricsCollector,
                                   SystemMetricsWebSocketHandler webSocketHandler,
                                   MetricsSseBroadcaster metricsSseBroadcaster,
                                   ObjectMapper objectMapper,
                                   DataSource dataSource,
                                   RabbitTemplate rabbitTemplate) {
        this(requestMetricsCollector, webSocketHandler, metricsSseBroadcaster, objectMapper, dataSource, rabbitTemplate,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build());
    }

    DeveloperMonitorService(RequestMetricsCollector requestMetricsCollector,
                            SystemMetricsWebSocketHandler webSocketHandler,
                            MetricsSseBroadcaster metricsSseBroadcaster,
                            ObjectMapper objectMapper,
                            DataSource dataSource,
                            RabbitTemplate rabbitTemplate,
                            HttpClient httpClient) {
        this(requestMetricsCollector, webSocketHandler, metricsSseBroadcaster, objectMapper, dataSource, rabbitTemplate,
                httpClient, "http", "localhost", 9200, 1, 1500);
    }

    DeveloperMonitorService(RequestMetricsCollector requestMetricsCollector,
                            SystemMetricsWebSocketHandler webSocketHandler,
                            MetricsSseBroadcaster metricsSseBroadcaster,
                            ObjectMapper objectMapper,
                            DataSource dataSource,
                            RabbitTemplate rabbitTemplate,
                            HttpClient httpClient,
                            String esScheme,
                            String esHost,
                            int esPort,
                            int wsSampleRate,
                            int wsDegradeThreshold) {
        this.requestMetricsCollector = requestMetricsCollector;
        this.webSocketHandler = webSocketHandler;
        this.metricsSseBroadcaster = metricsSseBroadcaster;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
        this.rabbitTemplate = rabbitTemplate;
        this.httpClient = httpClient;
        this.esScheme = esScheme;
        this.esHost = esHost;
        this.esPort = esPort;
        this.wsSampleRate = wsSampleRate;
        this.wsDegradeThreshold = wsDegradeThreshold;
    }

    public DeveloperMetricsVO snapshot() {
        long now = System.currentTimeMillis();
        long currentTotal = requestMetricsCollector.getTotalRequests();
        long prevTotal = lastRequestTotal.getAndSet(currentTotal);
        long prevTs = lastCollectTs.getAndSet(now);
        double seconds = Math.max(0.001, (now - prevTs) / 1000.0);
        double qps = (currentTotal - prevTotal) / seconds;

        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();

        double jvmMemoryUsage = heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0;
        double systemCpu = 0;
        double processCpu = 0;
        double systemMemoryUsage = 0;

        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof OperatingSystemMXBean bean) {
            systemCpu = safePercent(bean.getCpuLoad() * 100.0);
            processCpu = safePercent(bean.getProcessCpuLoad() * 100.0);
            long totalPhysical = bean.getTotalMemorySize();
            long freePhysical = bean.getFreeMemorySize();
            if (totalPhysical > 0) {
                systemMemoryUsage = safePercent((totalPhysical - freePhysical) * 100.0 / totalPhysical);
            }
        }

        return DeveloperMetricsVO.builder()
                .timestamp(OffsetDateTime.now().toString())
                .backendStatus("UP")
                .systemCpuUsage(systemCpu)
                .processCpuUsage(processCpu)
                .jvmMemoryUsage(safePercent(jvmMemoryUsage))
                .systemMemoryUsage(systemMemoryUsage)
                .heapUsedMb(heapUsed / 1024 / 1024)
                .heapMaxMb(heapMax / 1024 / 1024)
                .qps(round2(qps))
                .totalRequests(currentTotal)
                .websocketClients(webSocketHandler.getClientCount())
                .websocketMaxConnections(webSocketHandler.getMaxConnections())
                .sseClients(metricsSseBroadcaster.getClientCount())
                .pushMode(webSocketHandler.getClientCount() > Math.max(1, wsDegradeThreshold) ? "SSE_OR_POLLING" : "WEBSOCKET")
                .mysql(cachedCheckMysql())
                .rabbitmq(cachedCheckRabbitMq())
                .elasticsearch(cachedCheckElasticsearch())
                .build();
    }

    public DeveloperMetricsVO latestOrSnapshot() {
        DeveloperMetricsVO cached = latestMetrics.get();
        return cached != null ? cached : snapshot();
    }

    public SseEmitter openSseStream() {
        return metricsSseBroadcaster.subscribe();
    }

    @Scheduled(fixedRate = 2000)
    public void pushMetrics() {
        int sseClients = metricsSseBroadcaster.getClientCount();
        int wsClients = webSocketHandler.getClientCount();
        // Skip expensive middleware probes (MySQL/RabbitMQ/ES) when no one is listening
        if (sseClients == 0 && wsClients == 0) {
            updateQpsCounters();
            return;
        }
        try {
            DeveloperMetricsVO metrics = snapshot();
            latestMetrics.set(metrics);
            metricsSseBroadcaster.broadcast(metrics);

            if (wsClients > Math.max(1, wsDegradeThreshold)) return;
            long tick = pushTicks.incrementAndGet();
            if (tick % Math.max(1, wsSampleRate) != 0) return;
            webSocketHandler.broadcast(objectMapper.writeValueAsString(metrics));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Failed to push developer metrics: {}", e.getMessage());
        }
    }

    private void updateQpsCounters() {
        long now = System.currentTimeMillis();
        long currentTotal = requestMetricsCollector.getTotalRequests();
        lastRequestTotal.set(currentTotal);
        lastCollectTs.set(now);
    }

    private DeveloperMetricsVO.MiddlewareStatusVO cachedCheckMysql() {
        if (cachedMysql != null && System.currentTimeMillis() < healthCacheExpiresAt) return cachedMysql;
        cachedMysql = checkMysql();
        healthCacheExpiresAt = System.currentTimeMillis() + HEALTH_CACHE_TTL_MS;
        return cachedMysql;
    }

    private DeveloperMetricsVO.MiddlewareStatusVO cachedCheckRabbitMq() {
        if (cachedRabbit != null && System.currentTimeMillis() < healthCacheExpiresAt) return cachedRabbit;
        cachedRabbit = checkRabbitMq();
        return cachedRabbit;
    }

    private DeveloperMetricsVO.MiddlewareStatusVO cachedCheckElasticsearch() {
        if (cachedEs != null && System.currentTimeMillis() < healthCacheExpiresAt) return cachedEs;
        cachedEs = checkElasticsearch();
        return cachedEs;
    }

    private DeveloperMetricsVO.MiddlewareStatusVO checkMysql() {
        try (Connection connection = dataSource.getConnection()) {
            boolean ok = connection.isValid(2);
            return status(ok, ok ? "MySQL connection valid" : "MySQL validation failed");
        } catch (SQLException e) {
            return status(false, "MySQL error: " + safeMsg(e));
        }
    }

    private DeveloperMetricsVO.MiddlewareStatusVO checkRabbitMq() {
        try {
            Boolean open = rabbitTemplate.execute(channel -> channel.isOpen());
            boolean ok = Boolean.TRUE.equals(open);
            return status(ok, ok ? "RabbitMQ channel open" : "RabbitMQ channel unavailable");
        } catch (AmqpException e) {
            return status(false, "RabbitMQ error: " + safeMsg(e));
        }
    }

    private DeveloperMetricsVO.MiddlewareStatusVO checkElasticsearch() {
        String endpoint = String.format(Locale.ROOT, "%s://%s:%d", esScheme, esHost, esPort);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = res.statusCode() >= 200 && res.statusCode() < 400;
            return status(ok, "Elasticsearch HTTP " + res.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return status(false, "Elasticsearch error: " + safeMsg(e));
        } catch (IOException | IllegalArgumentException e) {
            return status(false, "Elasticsearch error: " + safeMsg(e));
        }
    }

    private DeveloperMetricsVO.MiddlewareStatusVO status(boolean ok, String detail) {
        return DeveloperMetricsVO.MiddlewareStatusVO.builder()
                .status(ok ? "UP" : "DOWN")
                .detail(detail)
                .build();
    }

    private String safeMsg(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    private double safePercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            return 0;
        }
        return round2(value);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
