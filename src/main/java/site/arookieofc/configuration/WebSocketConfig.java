package site.arookieofc.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import site.arookieofc.service.monitor.SystemMetricsWebSocketHandler;

import static site.arookieofc.configuration.SecurityConfig.ALLOWED_ORIGINS;

@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final SystemMetricsWebSocketHandler systemMetricsWebSocketHandler;
    private final WebSocketJwtAuthInterceptor webSocketJwtAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(systemMetricsWebSocketHandler, "/ws/system-metrics")
                .addInterceptors(webSocketJwtAuthInterceptor)
                .setAllowedOrigins(ALLOWED_ORIGINS.toArray(String[]::new));
        log.info("WebSocket system metrics endpoint registered at /ws/system-metrics");
    }
}
