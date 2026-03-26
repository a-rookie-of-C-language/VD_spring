package site.arookieofc.configuration;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.util.JWTUtils;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketJwtAuthInterceptor implements HandshakeInterceptor {
    private final JWTUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        try {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) {
                return false;
            }

            String token = resolveToken(servletRequest);
            if (token == null || token.isBlank()) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }

            Claims claims = jwtUtils.parseToken(token);
            String studentNo = claims.getSubject();
            String role = claims.get("role", String.class);
            String username = claims.get("username", String.class);

            if (!isSuperAdmin(role)) {
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                return false;
            }

            attributes.put("principal", new UserPrincipal(studentNo, role, username));
            return true;
        } catch (Exception ex) {
            log.debug("Rejected websocket handshake due to invalid token: {}", ex.getMessage());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String resolveToken(ServletServerHttpRequest request) {
        String header = request.getServletRequest().getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return request.getServletRequest().getParameter("token");
    }

    private boolean isSuperAdmin(String role) {
        if (role == null) {
            return false;
        }
        return "superAdmin".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role);
    }
}
