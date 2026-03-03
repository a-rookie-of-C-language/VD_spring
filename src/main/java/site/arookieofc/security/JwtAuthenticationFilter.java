package site.arookieofc.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import site.arookieofc.util.JWTUtils;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JWTUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (!token.isEmpty()) {
                try {
                    Claims claims = jwtUtils.parseToken(token);
                    String studentNo = claims.getSubject();
                    String role = claims.get("role", String.class);
                    String username = claims.get("username", String.class);
                    
                    UserPrincipal principal = new UserPrincipal(studentNo, role, username);
                    SecurityContextHolder.getContext().setAuthentication(principal);
                } catch (Exception e) {
                    log.debug("Invalid JWT token: {}", e.getMessage());
                }
                }
            }
        } catch (Exception e) {
            log.error("Error processing authentication", e);
        }
        
        filterChain.doFilter(request, response);
    }
}
