package site.arookieofc.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import site.arookieofc.service.monitor.RequestMetricsCollector;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestMetricsFilter extends OncePerRequestFilter {
    private final RequestMetricsCollector requestMetricsCollector;

    public RequestMetricsFilter(RequestMetricsCollector requestMetricsCollector) {
        this.requestMetricsCollector = requestMetricsCollector;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        requestMetricsCollector.recordRequest();
        String requestId = resolveRequestId(request);
        request.setAttribute("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        filterChain.doFilter(request, response);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String fromHeader = request.getHeader("X-Request-ID");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader.trim();
        }
        return UUID.randomUUID().toString();
    }
}
