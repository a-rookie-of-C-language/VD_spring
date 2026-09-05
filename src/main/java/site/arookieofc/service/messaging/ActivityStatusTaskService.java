package site.arookieofc.service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.arookieofc.configuration.RabbitConfig;
import site.arookieofc.dao.entity.ActivityStatusTask;
import site.arookieofc.dao.mapper.ActivityStatusTaskMapper;
import site.arookieofc.service.BO.ActivityStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityStatusTaskService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_ATTEMPT = 8;
    private static final int DISPATCH_BATCH_SIZE = 100;

    private final ActivityStatusTaskMapper taskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void scheduleStatusUpdate(String activityId,
                                     ActivityStatus targetStatus,
                                     LocalDateTime executeAt,
                                     String source) {
        if (activityId == null || targetStatus == null || executeAt == null) {
            return;
        }

        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZONE);
        ActivityStatusTask task = ActivityStatusTask.builder()
                .eventId(eventId)
                .activityId(activityId)
                .targetStatus(targetStatus.name())
                .source(source)
                .executeAt(executeAt)
                .status("PENDING")
                .attempt(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        taskMapper.insert(task);
        dispatch(task);
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverPendingTasks() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime sentBefore = now.minusMinutes(10);
        List<ActivityStatusTask> tasks = taskMapper.listDispatchable(DISPATCH_BATCH_SIZE, now, sentBefore);
        if (tasks.isEmpty()) {
            return;
        }

        log.info("Recovering activity status tasks: {}", tasks.size());
        for (ActivityStatusTask task : tasks) {
            dispatch(task);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void reportBacklogHealth() {
        Map<String, Long> stats = getTaskStatusStats();
        long dead = stats.getOrDefault("DEAD", 0L);
        long failed = stats.getOrDefault("FAILED", 0L);
        if (dead > 0 || failed > 0) {
            log.warn("Activity status task backlog detected. DEAD={}, FAILED={}, stats={}", dead, failed, stats);
        }
    }

    @Transactional
    public void markDone(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        taskMapper.markDone(eventId, LocalDateTime.now(ZONE));
    }

    public boolean isDone(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        ActivityStatusTask task = taskMapper.getByEventId(eventId);
        return task != null && "DONE".equals(task.getStatus());
    }

    @Transactional
    public void markConsumeFailed(String eventId, int attempt, String error) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (attempt >= MAX_ATTEMPT) {
            taskMapper.markDead(eventId, attempt, normalizeError(error), now);
            return;
        }
        LocalDateTime nextRetry = now.plusSeconds(backoffSeconds(attempt));
        taskMapper.markFailed(eventId, attempt, normalizeError(error), nextRetry, now);
    }

    public Map<String, Long> getTaskStatusStats() {
        List<Map<String, Object>> rows = taskMapper.countByStatus();
        Map<String, Long> stats = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            Object cnt = row.get("cnt");
            long count = cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
            stats.put(status, count);
        }
        return stats;
    }

    @Transactional
    public int replayDeadTasks(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        return taskMapper.replayDeadTasks(boundedLimit, LocalDateTime.now(ZONE));
    }

    private void dispatch(ActivityStatusTask task) {
        if (task == null) {
            return;
        }

        int nextAttempt = (task.getAttempt() == null ? 0 : task.getAttempt()) + 1;
        if (nextAttempt > MAX_ATTEMPT) {
            taskMapper.markDead(task.getEventId(), nextAttempt, "exceeded max publish attempts", LocalDateTime.now(ZONE));
            return;
        }

        ActivityStatus targetStatus = parseTargetStatus(task);
        if (targetStatus == null) {
            taskMapper.markDead(task.getEventId(), nextAttempt,
                    normalizeError("invalid target status: " + task.getTargetStatus()), LocalDateTime.now(ZONE));
            return;
        }

        try {
            ActivityStatusUpdateMessage payload = new ActivityStatusUpdateMessage(
                    task.getEventId(),
                    task.getActivityId(),
                    targetStatus,
                    nextAttempt,
                    task.getExecuteAt(),
                    task.getSource()
            );

            byte[] body = objectMapper.writeValueAsBytes(payload);
            LocalDateTime now = LocalDateTime.now(ZONE);
            long delayMs = computeDelayMs(now, task.getExecuteAt());

            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setHeader("x-delay", delayMs);
            props.setHeader("x-event-id", task.getEventId());
            props.setHeader("x-attempt", nextAttempt);
            Message message = new Message(body, props);

            CorrelationData correlationData = new CorrelationData(task.getEventId());
            rabbitTemplate.send(RabbitConfig.DELAY_EXCHANGE, RabbitConfig.DELAY_ROUTING_KEY, message, correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(3, TimeUnit.SECONDS);
            if (confirm != null && confirm.isAck()) {
                taskMapper.markSent(task.getEventId(), LocalDateTime.now(ZONE));
            } else {
                String reason = confirm == null ? "publisher confirm missing" : normalizeError(confirm.getReason());
                markPublishFailed(task.getEventId(), nextAttempt, reason);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markPublishFailed(task.getEventId(), nextAttempt, e.getMessage());
        } catch (JsonProcessingException | AmqpException | ExecutionException | TimeoutException e) {
            markPublishFailed(task.getEventId(), nextAttempt, e.getMessage());
        }
    }

    private ActivityStatus parseTargetStatus(ActivityStatusTask task) {
        String targetStatus = task.getTargetStatus();
        if (targetStatus == null || targetStatus.isBlank()) {
            return null;
        }
        try {
            return ActivityStatus.valueOf(targetStatus.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void markPublishFailed(String eventId, int attempt, String reason) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (attempt >= MAX_ATTEMPT) {
            taskMapper.markDead(eventId, attempt, normalizeError(reason), now);
            log.error("Activity status task dead-lettered after publish failures. eventId={}, attempt={}, reason={}",
                    eventId, attempt, normalizeError(reason));
            return;
        }

        LocalDateTime nextRetryAt = now.plusSeconds(backoffSeconds(attempt));
        taskMapper.markFailed(eventId, attempt, normalizeError(reason), nextRetryAt, now);
        log.warn("Activity status task publish failed. eventId={}, attempt={}, nextRetryAt={}, reason={}",
                eventId, attempt, nextRetryAt, normalizeError(reason));
    }

    private long computeDelayMs(LocalDateTime now, LocalDateTime executeAt) {
        ZonedDateTime n = now.atZone(ZONE);
        ZonedDateTime t = executeAt.atZone(ZONE);
        long delayMs = Duration.between(n, t).toMillis();
        return Math.max(0, delayMs);
    }

    private long backoffSeconds(int attempt) {
        long seconds = (long) Math.pow(2, Math.max(0, attempt - 1)) * 5L;
        return Math.min(seconds, 300L);
    }

    private String normalizeError(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 900) {
            return trimmed.substring(0, 900);
        }
        return trimmed;
    }
}
