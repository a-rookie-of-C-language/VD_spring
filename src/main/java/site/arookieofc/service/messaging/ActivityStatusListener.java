package site.arookieofc.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import site.arookieofc.configuration.RabbitConfig;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;

@Component
@RequiredArgsConstructor
public class ActivityStatusListener {
    private final ActivityMapper activityMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = RabbitConfig.UPDATE_QUEUE)
    public void onMessage(byte[] payload) {
        try {
            ActivityStatusUpdateMessage msg = objectMapper
                    .readValue(payload, ActivityStatusUpdateMessage.class);
            handle(msg);
        } catch (Exception e) {
            try {
                String s = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                String[] parts = s.split("\\|");
                if (parts.length == 2) {
                    handle(new ActivityStatusUpdateMessage(parts[0]
                            , ActivityStatus.valueOf(parts[1])));
                }
            } catch (Exception ignored) {}
        }
    }

    private void handle(ActivityStatusUpdateMessage msg) {
        if (msg == null) return;
        Activity a = activityMapper.getById(msg.getActivityId());
        if (a == null) return;
        if (a.getStatus() == ActivityStatus.UnderReview
                || a.getStatus() == ActivityStatus.FailReview) {
            return;
        }
        ActivityStatus target = msg.getTargetStatus();
        if (a.getStatus() != target) {
            a.setStatus(target);
            activityMapper.update(a);
        }
    }
}
