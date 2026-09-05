package site.arookieofc.service.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;

import java.time.*;
import java.util.List;

@Slf4j
@Component
public class ActivityStartupSynchronizer {
    private final ActivityMapper activityMapper;
    private final ActivityStatusTaskService activityStatusTaskService;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    
    @Value("${app.messaging.dev-mode-trigger:false}")
    private boolean devModeTrigger;

    @Autowired
    public ActivityStartupSynchronizer(ActivityMapper activityMapper,
                                       ActivityStatusTaskService activityStatusTaskService) {
        this(activityMapper, activityStatusTaskService, false);
    }

    ActivityStartupSynchronizer(ActivityMapper activityMapper,
                                ActivityStatusTaskService activityStatusTaskService,
                                boolean devModeTrigger) {
        this.activityMapper = activityMapper;
        this.activityStatusTaskService = activityStatusTaskService;
        this.devModeTrigger = devModeTrigger;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeActivitiesOnStartup() {
        log.info("Application started. Synchronizing activity statuses... (Dev Mode Trigger: {})", devModeTrigger);
        try {
            List<Activity> activities = activityMapper.listAllBase();
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
        } catch (RuntimeException e) {
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
        if (est == null || eet == null || st == null || et == null) {
            return;
        }
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
        if (delayMs <= 0) return;
        activityStatusTaskService.scheduleStatusUpdate(id, status, when, "startup-sync");
        log.debug("Scheduled status task for activity {} to {} in {}ms", id, status, delayMs);
    }
}
