package site.arookieofc.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.util.JWTUtils;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketJwtAuthInterceptor implements HandshakeInterceptor {
    private final JWTUtils jwtUtils;
    private final UserMapper userMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        try {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) {
                return false;
            }

            String token = resolveTokenFromHeader(servletRequest);
            if (token == null || token.isBlank()) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }

            Claims claims = jwtUtils.parseToken(token);
            if (!jwtUtils.isTokenType(claims, JWTUtils.TYPE_ACCESS)) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            String studentNo = claims.getSubject();
            String role = claims.get("role", String.class);
            String username = claims.get("username", String.class);
            User user = studentNo == null ? null : userMapper.getUserByStudentNo(studentNo);
            int currentTokenVersion = user != null && user.getTokenVersion() != null ? user.getTokenVersion() : 0;
            if (user == null || jwtUtils.getTokenVersion(claims) != currentTokenVersion) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }

            if (!isSuperAdmin(role)) {
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                return false;
            }

            attributes.put("principal", new UserPrincipal(studentNo, role, username));
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
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

    private String resolveTokenFromHeader(ServletServerHttpRequest request) {
        String header = request.getServletRequest().getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private boolean isSuperAdmin(String role) {
        if (role == null) {
            return false;
        }
        return "superAdmin".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role);
    }
}
