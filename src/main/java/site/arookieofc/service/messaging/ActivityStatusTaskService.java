package site.arookieofc.service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import com.rabbitmq.client.AMQP;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                .nextRetryAt(executeAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        taskMapper.insert(task);
        if (!executeAt.isAfter(now)) {
            dispatch(task);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverPendingTasks() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<ActivityStatusTask> tasks = taskMapper.listDispatchable(DISPATCH_BATCH_SIZE, now, now.minusMinutes(10));
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

            rabbitTemplate.execute(channel -> {
                try {
                    AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                            .contentType(MessageProperties.CONTENT_TYPE_JSON)
                            .headers(Map.of(
                                    "x-event-id", task.getEventId(),
                                    "x-attempt", nextAttempt))
                            .build();
                    channel.basicPublish(RabbitConfig.UPDATE_EXCHANGE, RabbitConfig.UPDATE_ROUTING_KEY, props, body);
                    channel.waitForConfirmsOrDie(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AmqpException("publish confirm interrupted", e);
                }
                return null;
            });
            taskMapper.markSent(task.getEventId(), LocalDateTime.now(ZONE));
        } catch (JsonProcessingException | AmqpException e) {
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
