package site.arookieofc.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.util.JWTUtils;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JWTUtils jwtUtils;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (!token.isEmpty()) {
                    Claims claims = jwtUtils.parseToken(token);
                    if (jwtUtils.isTokenType(claims, JWTUtils.TYPE_ACCESS)) {
                        String studentNo = claims.getSubject();
                        User user = studentNo == null ? null : userMapper.getUserByStudentNo(studentNo);
                        int tokenVersion = jwtUtils.getTokenVersion(claims);
                        int currentTokenVersion = user != null && user.getTokenVersion() != null ? user.getTokenVersion() : 0;
                        if (user != null && tokenVersion == currentTokenVersion) {
                            String role = claims.get("role", String.class);
                            String username = claims.get("username", String.class);
                            SecurityContextHolder.getContext().setAuthentication(new UserPrincipal(studentNo, role, username));
                        } else {
                            log.debug("JWT revoked or user missing for studentNo={}", studentNo);
                        }
                    }
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
