package site.arookieofc.service.monitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.arookieofc.controller.VO.DeveloperMetricsVO;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeveloperMonitorServiceTest {

    @Test
    void pushMetricsDoesNotPropagateJsonSerializationFailure() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("json bad") { });
        SystemMetricsWebSocketHandler webSocketHandler = webSocketHandler();
        when(webSocketHandler.getClientCount()).thenReturn(1);
        when(webSocketHandler.getMaxConnections()).thenReturn(10);
        MetricsSseBroadcaster sseBroadcaster = sseBroadcaster();
        when(sseBroadcaster.getClientCount()).thenReturn(0);
        DeveloperMonitorService service = newService(
                okDataSource(),
                okRabbitTemplate(),
                okHttpClient(),
                objectMapper,
                webSocketHandler,
                sseBroadcaster);

        assertDoesNotThrow(service::pushMetrics);
    }

    @Test
    void mysqlHealthCheckReportsDownForSqlFailure() throws Exception {
        DataSource dataSource = dataSource();
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));
        DeveloperMonitorService service = newService(dataSource, okRabbitTemplate(), okHttpClient());

        DeveloperMetricsVO.MiddlewareStatusVO status = service.snapshot().getMysql();

        assertEquals("DOWN", status.getStatus());
        assertTrue(status.getDetail().contains("db down"));
    }

    @Test
    void rabbitHealthCheckReportsDownForAmqpFailure() throws Exception {
        RabbitTemplate rabbitTemplate = rabbitTemplate();
        when(rabbitTemplate.execute(any())).thenThrow(new AmqpException("mq down"));
        DeveloperMonitorService service = newService(okDataSource(), rabbitTemplate, okHttpClient());

        DeveloperMetricsVO.MiddlewareStatusVO status = service.snapshot().getRabbitmq();

        assertEquals("DOWN", status.getStatus());
        assertTrue(status.getDetail().contains("mq down"));
    }

    @Test
    void elasticsearchHealthCheckRestoresInterruptedFlag() throws Exception {
        HttpClient httpClient = httpClient();
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler()))
                .thenThrow(new InterruptedException("stop"));
        DeveloperMonitorService service = newService(okDataSource(), okRabbitTemplate(), httpClient);

        try {
            DeveloperMetricsVO.MiddlewareStatusVO status = service.snapshot().getElasticsearch();

            assertEquals("DOWN", status.getStatus());
            assertTrue(status.getDetail().contains("stop"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private DeveloperMonitorService newService(DataSource dataSource, RabbitTemplate rabbitTemplate) {
        return newService(dataSource, rabbitTemplate, okHttpClient());
    }

    private DeveloperMonitorService newService(DataSource dataSource, RabbitTemplate rabbitTemplate, HttpClient httpClient) {
        return newService(
                dataSource,
                rabbitTemplate,
                httpClient,
                new ObjectMapper(),
                webSocketHandler(),
                sseBroadcaster());
    }

    private DeveloperMonitorService newService(DataSource dataSource,
                                               RabbitTemplate rabbitTemplate,
                                               HttpClient httpClient,
                                               ObjectMapper objectMapper,
                                               SystemMetricsWebSocketHandler webSocketHandler,
                                               MetricsSseBroadcaster sseBroadcaster) {
        return new DeveloperMonitorService(
                requestMetricsCollector(),
                webSocketHandler,
                sseBroadcaster,
                objectMapper,
                dataSource,
                rabbitTemplate,
                httpClient);
    }

    private DataSource okDataSource() throws SQLException {
        DataSource dataSource = dataSource();
        Connection connection = connection();
        when(connection.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    private DataSource dataSource() {
        return mock(DataSource.class);
    }

    private Connection connection() {
        return mock(Connection.class);
    }

    private RabbitTemplate okRabbitTemplate() {
        RabbitTemplate rabbitTemplate = rabbitTemplate();
        when(rabbitTemplate.execute(any())).thenReturn(true);
        return rabbitTemplate;
    }

    private RabbitTemplate rabbitTemplate() {
        return mock(RabbitTemplate.class);
    }

    private SystemMetricsWebSocketHandler webSocketHandler() {
        return mock(SystemMetricsWebSocketHandler.class);
    }

    private MetricsSseBroadcaster sseBroadcaster() {
        return mock(MetricsSseBroadcaster.class);
    }

    private RequestMetricsCollector requestMetricsCollector() {
        return mock(RequestMetricsCollector.class);
    }

    private HttpClient okHttpClient() {
        HttpClient httpClient = httpClient();
        HttpResponse<String> response = response();
        when(response.statusCode()).thenReturn(200);
        try {
            when(httpClient.send(any(HttpRequest.class), stringBodyHandler()))
                    .thenReturn(response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return httpClient;
    }

    private HttpClient httpClient() {
        return mock(HttpClient.class);
    }

    private HttpResponse<String> response() {
        return mock(HttpResponse.class);
    }

    private HttpResponse.BodyHandler<String> stringBodyHandler() {
        return any();
    }
}
