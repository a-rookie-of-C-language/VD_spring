package site.arookieofc.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.arookieofc.configuration.RabbitConfig;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityStatusListener {
    private final ActivityMapper activityMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = RabbitConfig.UPDATE_QUEUE)
    public void onMessage(byte[] payload) {
        try {
            ActivityStatusUpdateMessage msg = objectMapper
                    .readValue(payload, ActivityStatusUpdateMessage.class);
            log.info("Received status update message: {}", msg);
            handle(msg);
        } catch (Exception e) {
            log.error("Failed to process message payload: {}", new String(payload), e);
            try {
                String s = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                String[] parts = s.split("\\|");
                if (parts.length == 2) {
                    handle(new ActivityStatusUpdateMessage(parts[0]
                            , ActivityStatus.valueOf(parts[1])));
                }
            } catch (Exception ex) {
                log.error("Failed to process fallback message format", ex);
            }
        }
    }

    @Transactional
    private void handle(ActivityStatusUpdateMessage msg) {
        if (msg == null) return;
        
        Activity a = activityMapper.getById(msg.getActivityId());
        if (a == null) {
            log.warn("Activity not found for status update: {}", msg.getActivityId());
            return;
        }
        
        ActivityStatus currentStatus = a.getStatus();
        ActivityStatus targetStatus = msg.getTargetStatus();
        
        // Idempotency check: if status is already the target, this is a duplicate/idempotent message
        if (currentStatus == targetStatus) {
            log.info("Activity {} already has status {}, message is idempotent - skipping update", 
                    a.getId(), targetStatus);
            return;
        }
        
        // Terminal state protection: cannot modify activities that have ended or failed review
        if (currentStatus.isTerminalState()) {
            log.warn("Cannot update activity {} from terminal state {} to {}", 
                    a.getId(), currentStatus, targetStatus);
            return;
        }
        
        // Protected state check: review states should only be changed through review API
        if (currentStatus.isProtectedState()) {
            log.warn("Skipping status update for activity {} as it is in protected review state: {}", 
                    a.getId(), currentStatus);
            return;
        }
        
        // State machine validation: check if transition is valid
        if (!currentStatus.canTransitionTo(targetStatus)) {
            log.error("Invalid state transition for activity {}: {} -> {}. Message rejected.", 
                    a.getId(), currentStatus, targetStatus);
            return;
        }
        
        // All validations passed - perform the status update
        log.info("Updating activity {} status from {} to {}", a.getId(), currentStatus, targetStatus);
        int rows = activityMapper.updateStatus(a.getId(), targetStatus);
        log.info("Status update affected rows: {}", rows);
    }
}
