package site.arookieofc.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.util.JWTUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketJwtAuthInterceptorTest {

    @Test
    void malformedJwtRejectsHandshakeWithUnauthorized() {
        JWTUtils jwtUtils = jwtUtils();
        UserMapper userMapper = userMapper();
        when(jwtUtils.parseToken("bad")).thenThrow(new MalformedJwtException("bad token"));
        WebSocketJwtAuthInterceptor interceptor = newInterceptor(jwtUtils, userMapper);
        RecordingServerHttpResponse response = new RecordingServerHttpResponse();

        boolean allowed = interceptor.beforeHandshake(
                requestWithBearer("bad"), response, mock(WebSocketHandler.class), Map.of());

        assertFalse(allowed);
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode);
    }

    @Test
    void userLookupFailureIsNotSwallowedAsInvalidToken() {
        JWTUtils jwtUtils = jwtUtils();
        UserMapper userMapper = userMapper();
        Claims claims = mock(Claims.class);
        when(jwtUtils.parseToken("valid")).thenReturn(claims);
        when(jwtUtils.isTokenType(claims, JWTUtils.TYPE_ACCESS)).thenReturn(true);
        String subject = "s1";
        when(claims.getSubject()).thenReturn(subject);
        when(userMapper.getUserByStudentNo(subject)).thenThrow(new IllegalStateException("db down"));
        WebSocketJwtAuthInterceptor interceptor = newInterceptor(jwtUtils, userMapper);

        assertThrows(IllegalStateException.class, () -> interceptor.beforeHandshake(
                requestWithBearer("valid"),
                new RecordingServerHttpResponse(),
                mock(WebSocketHandler.class),
                Map.of()));
        verify(userMapper).getUserByStudentNo(subject);
    }

    private JWTUtils jwtUtils() {
        return mock(JWTUtils.class);
    }

    private UserMapper userMapper() {
        return mock(UserMapper.class);
    }

    private WebSocketJwtAuthInterceptor newInterceptor(JWTUtils jwtUtils, UserMapper userMapper) {
        return new WebSocketJwtAuthInterceptor(jwtUtils, userMapper);
    }

    private ServletServerHttpRequest requestWithBearer(String token) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + token);
        return new ServletServerHttpRequest(servletRequest);
    }

    private static class RecordingServerHttpResponse implements ServerHttpResponse {
        private final HttpHeaders headers = new HttpHeaders();
        private HttpStatusCode statusCode;

        @Override
        public void setStatusCode(HttpStatusCode status) {
            this.statusCode = status;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public OutputStream getBody() throws IOException {
            return OutputStream.nullOutputStream();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
