package site.arookieofc.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import site.arookieofc.security.JwtAuthenticationFilter;
import site.arookieofc.controller.VO.Result;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    public static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:8080",
            "https://arookieofc.site",
            "http://arookieofc.site",
            "https://unscreenable-cathrine-unprejudicially.ngrok-free.dev"
    );

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                        .requestMatchers("/assets/**", "/standard/**").permitAll()
                        .requestMatchers("/user/login", "/user/verifyToken").permitAll()
                        .requestMatchers("/ws/system-metrics").permitAll()
                        .requestMatchers("/covers/**", "/attachments/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/activities/*/review").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/pending-activities/*/approve", "/pending-activities/*/reject").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/pending-activities/batch-import/*/approve", "/pending-activities/batch-import/*/reject").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/activities/refreshStatuses").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/activities/attachment", "/activities/attachment/info").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/user/listAll").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/monitoring/**", "/api/monitoring/**").hasRole("SUPERADMIN")
                        .requestMatchers("/suggestions/*/reply").hasAnyRole("ADMIN", "SUPERADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e ->
                        e.authenticationEntryPoint((req, res, ex) ->
                                writeJsonError(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED"))
                         .accessDeniedHandler((req, res, ex) ->
                                writeJsonError(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeJsonError(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Result.of(status, message, null)));
        } catch (Exception ignored) {
            response.setStatus(status);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(ALLOWED_ORIGINS);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
