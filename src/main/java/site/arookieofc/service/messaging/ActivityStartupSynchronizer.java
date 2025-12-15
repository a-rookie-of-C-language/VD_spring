package site.arookieofc.service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.arookieofc.configuration.RabbitConfig;

import java.time.*;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStartupSynchronizer {
    private final ActivityMapper activityMapper;
    private final RabbitTemplate rabbitTemplate;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    
    @Value("${app.messaging.dev-mode-trigger:false}")
    private boolean devModeTrigger;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeActivitiesOnStartup() {
        log.info("Application started. Synchronizing activity statuses... (Dev Mode Trigger: {})", devModeTrigger);
        try {
            List<Activity> activities = activityMapper.listAll();
            int updated = 0;
            int scheduled = 0;
            
            for (Activity activity : activities) {
                // Skip activities that are finished or failed review
                if (activity.getStatus() == ActivityStatus.ActivityEnded 
                        || activity.getStatus() == ActivityStatus.FailReview) {
                    continue;
                }
                
                // Skip activities under review (they will be handled when approved)
                if (activity.getStatus() == ActivityStatus.UnderReview) {
                    continue;
                }
                
                // Calculate the correct status based on current time
                ActivityStatus oldStatus = activity.getStatus();
                refreshStatus(activity);
                
                if (oldStatus != activity.getStatus()) {
                    // Always update the database directly, regardless of mode
                    activityMapper.update(activity);
                    log.info("Updated activity {} status from {} to {}", 
                            activity.getId(), oldStatus, activity.getStatus());
                    updated++;
                }
                
                // In dev mode, skip message scheduling since we've already updated the status
                // In production mode, schedule future status change messages
                if (!devModeTrigger) {
                    scheduleStatusMessages(activity);
                    scheduled++;
                }
            }
            
            if (devModeTrigger) {
                log.info("Activity synchronization completed (Dev Mode). Updated: {}", updated);
            } else {
                log.info("Activity synchronization completed. Updated: {}, Scheduled: {}", updated, scheduled);
            }
        } catch (Exception e) {
            log.error("Failed to synchronize activities on startup", e);
        }
    }

    private void refreshStatus(Activity a) {
        changeStatus(a, ZONE);
    }

    public static void changeStatus(Activity a, ZoneId zone) {
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime est = a.getEnrollmentStartTime();
        LocalDateTime eet = a.getEnrollmentEndTime();
        LocalDateTime st = a.getStartTime();
        LocalDateTime et = a.getExpectedEndTime();
        if (now.isBefore(est)) {
            a.setStatus(ActivityStatus.EnrollmentNotStart);
        } else if (now.isBefore(eet)) {
            a.setStatus(ActivityStatus.EnrollmentStarted);
        } else if (now.isBefore(st)) {
            a.setStatus(ActivityStatus.EnrollmentEnded);
        } else if (now.isBefore(et)) {
            a.setStatus(ActivityStatus.ActivityStarted);
        } else {
            a.setStatus(ActivityStatus.ActivityEnded);
        }
    }

    private void scheduleStatusMessages(Activity entity) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        scheduleOne(entity.getId(),
                entity.getEnrollmentStartTime(),
                ActivityStatus.EnrollmentStarted,
                now);
        scheduleOne(entity.getId(),
                entity.getEnrollmentEndTime(),
                ActivityStatus.EnrollmentEnded,
                now);
        scheduleOne(entity.getId(),
                entity.getStartTime(),
                ActivityStatus.ActivityStarted,
                now);
        scheduleOne(entity.getId(),
                entity.getEndTime(),
                ActivityStatus.ActivityEnded,
                now);
    }

    private void scheduleOne(String id, LocalDateTime when, ActivityStatus status, ZonedDateTime now) {
        if (when == null) return;
        ZonedDateTime target = when.atZone(ZONE);
        long delayMs = Duration.between(now, target).toMillis();
        
        // Only schedule messages for future times
        if (delayMs <= 0) return;
        
        ActivityStatusUpdateMessage msg = new ActivityStatusUpdateMessage(id, status);
        MessageProperties props = new MessageProperties();
        props.setHeader("x-delay", delayMs);
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        byte[] body;
        try {
            body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(msg);
        } catch (Exception e) {
            body = (id + "|" + status.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        Message amqpMsg = new Message(body, props);
        rabbitTemplate.send(RabbitConfig.DELAY_EXCHANGE, RabbitConfig.DELAY_ROUTING_KEY, amqpMsg);
        
        log.debug("Scheduled status update for activity {} to {} in {}ms", id, status, delayMs);
    }
}
