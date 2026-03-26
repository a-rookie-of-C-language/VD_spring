package site.arookieofc.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import site.arookieofc.service.monitor.RequestMetricsCollector;

import java.io.IOException;

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
        filterChain.doFilter(request, response);
    }
}
