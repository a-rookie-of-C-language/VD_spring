package site.arookieofc.service.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import site.arookieofc.controller.VO.DeveloperMetricsVO;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MetricsSseBroadcaster {
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    @Value("${app.monitoring.sse.max-connections:500}")
    private int maxConnections;

    public SseEmitter subscribe() {
        if (emitters.size() >= Math.max(1, maxConnections)) {
            throw new IllegalStateException("SSE_CONNECTION_LIMIT_REACHED");
        }
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcast(DeveloperMetricsVO metrics) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("metrics")
                        .data(metrics));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public int getClientCount() {
        return emitters.size();
    }
}
