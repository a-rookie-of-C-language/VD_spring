package site.arookieofc.service.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import site.arookieofc.controller.VO.DeveloperMetricsVO;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeveloperMonitorService {
    private final RequestMetricsCollector requestMetricsCollector;
    private final SystemMetricsWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.logging.es.scheme:http}")
    private String esScheme;

    @Value("${app.logging.es.host:localhost}")
    private String esHost;

    @Value("${app.logging.es.port:9200}")
    private int esPort;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final AtomicLong lastRequestTotal = new AtomicLong(0);
    private final AtomicLong lastCollectTs = new AtomicLong(System.currentTimeMillis());

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
                .mysql(checkMysql())
                .rabbitmq(checkRabbitMq())
                .elasticsearch(checkElasticsearch())
                .build();
    }

    @Scheduled(fixedRate = 2000)
    public void pushMetrics() {
        try {
            DeveloperMetricsVO metrics = snapshot();
            String payload = objectMapper.writeValueAsString(metrics);
            webSocketHandler.broadcast(payload);
        } catch (Exception e) {
            log.warn("Failed to push developer metrics: {}", e.getMessage());
        }
    }

    private DeveloperMetricsVO.MiddlewareStatusVO checkMysql() {
        try (Connection connection = dataSource.getConnection()) {
            boolean ok = connection.isValid(2);
            return status(ok, ok ? "MySQL connection valid" : "MySQL validation failed");
        } catch (Exception e) {
            return status(false, "MySQL error: " + safeMsg(e));
        }
    }

    private DeveloperMetricsVO.MiddlewareStatusVO checkRabbitMq() {
        try {
            Boolean open = rabbitTemplate.execute(channel -> channel.isOpen());
            boolean ok = Boolean.TRUE.equals(open);
            return status(ok, ok ? "RabbitMQ channel open" : "RabbitMQ channel unavailable");
        } catch (Exception e) {
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
        } catch (Exception e) {
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
