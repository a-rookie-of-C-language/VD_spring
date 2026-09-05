package site.arookieofc.service.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import site.arookieofc.security.UserPrincipal;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SystemMetricsWebSocketHandler extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Value("${app.monitoring.ws.max-connections:2000}")
    private int maxConnections;

    public SystemMetricsWebSocketHandler() {
    }

    SystemMetricsWebSocketHandler(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Object principal = session.getAttributes().get("principal");
        if (!(principal instanceof UserPrincipal)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
            return;
        }
        if (sessions.size() >= Math.max(1, maxConnections)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("too many connections"));
            return;
        }
        sessions.add(session);
        log.info("Developer metrics websocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Developer metrics websocket disconnected: {}", session.getId());
    }

    public int getClientCount() {
        return sessions.size();
    }

    public int getMaxConnections() {
        return Math.max(1, maxConnections);
    }

    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        sessions.removeIf(s -> !s.isOpen());
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("Failed to send websocket metrics message: {}", e.getMessage());
            }
        }
    }
}
