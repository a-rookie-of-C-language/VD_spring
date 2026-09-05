package site.arookieofc.service.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import site.arookieofc.security.UserPrincipal;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemMetricsWebSocketHandlerTest {

    @Test
    void afterConnectionEstablishedClosesUnauthorizedSessionWithoutRegisteringIt() throws IOException {
        SystemMetricsWebSocketHandler handler = newHandler();
        String sessionId = "s1";
        WebSocketSession session = mockSession(sessionId, Map.of());

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
        assertEquals(0, handler.getClientCount());
    }

    @Test
    void afterConnectionEstablishedClosesWhenConnectionLimitIsReached() throws IOException {
        SystemMetricsWebSocketHandler handler = newHandler(1);
        String firstSessionId = "s1";
        String secondSessionId = "s2";
        WebSocketSession first = mockSession(firstSessionId, authenticatedAttributes());
        WebSocketSession second = mockSession(secondSessionId, authenticatedAttributes());

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        verify(first, never()).close(CloseStatus.POLICY_VIOLATION.withReason("too many connections"));
        verify(second).close(CloseStatus.POLICY_VIOLATION.withReason("too many connections"));
        assertEquals(1, handler.getClientCount());
    }

    private SystemMetricsWebSocketHandler newHandler() {
        return new SystemMetricsWebSocketHandler();
    }

    private SystemMetricsWebSocketHandler newHandler(int maxConnections) {
        return new SystemMetricsWebSocketHandler(maxConnections);
    }

    private WebSocketSession mockSession(String id, Map<String, Object> attributes) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    private Map<String, Object> authenticatedAttributes() {
        String principalStudentNo = "s1";
        return Map.of("principal", new UserPrincipal(principalStudentNo, "admin", "Admin"));
    }
}
