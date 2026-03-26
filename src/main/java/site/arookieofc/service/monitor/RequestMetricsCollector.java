package site.arookieofc.service.monitor;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RequestMetricsCollector {
    private final AtomicLong totalRequests = new AtomicLong(0);

    public void recordRequest() {
        totalRequests.incrementAndGet();
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }
}
