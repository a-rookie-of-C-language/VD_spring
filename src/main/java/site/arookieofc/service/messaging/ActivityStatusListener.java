package site.arookieofc.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.arookieofc.configuration.RabbitConfig;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.VolunteerHourGrantService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class ActivityStatusListener {
    private static final int MAX_CONSUME_RETRY = 5;

    private final ActivityMapper activityMapper;
    private final VolunteerHourGrantService volunteerHourGrantService;
    private final ActivityStatusTaskService taskService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private ActivityStatusListener self;

    public ActivityStatusListener(ActivityMapper activityMapper,
                                  VolunteerHourGrantService volunteerHourGrantService,
                                  ActivityStatusTaskService taskService,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper) {
        this.activityMapper = activityMapper;
        this.volunteerHourGrantService = volunteerHourGrantService;
        this.taskService = taskService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.self = this;
    }

    @RabbitListener(queues = RabbitConfig.UPDATE_QUEUE)
    public void onMessage(Message amqpMessage,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        ActivityStatusUpdateMessage msg = null;
        try {
            msg = parseMessage(amqpMessage.getBody());
            if (msg == null) {
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            if (msg.getEventId() != null && taskService.isDone(msg.getEventId())) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            self.handle(msg);
            if (msg.getEventId() != null) {
                taskService.markDone(msg.getEventId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (IOException | RuntimeException e) {
            int nextAttempt = nextAttempt(msg, amqpMessage);
            String eventId = msg == null ? null : msg.getEventId();

            if (nextAttempt <= MAX_CONSUME_RETRY && msg != null) {
                try {
                    publishRetry(msg, nextAttempt);
                    if (eventId != null) {
                        taskService.markConsumeFailed(eventId, nextAttempt, e.getMessage());
                    }
                    channel.basicAck(deliveryTag, false);
                    log.warn("Retried activity status message. eventId={}, attempt={}, error={}",
                            eventId, nextAttempt, e.getMessage());
                } catch (IOException | RuntimeException republishError) {
                    if (eventId != null) {
                        taskService.markConsumeFailed(eventId, MAX_CONSUME_RETRY, republishError.getMessage());
                    }
                    channel.basicNack(deliveryTag, false, false);
                    log.error("Retry publish failed, dead-lettering message. eventId={}", eventId, republishError);
                }
            } else {
                if (eventId != null) {
                    taskService.markConsumeFailed(eventId, MAX_CONSUME_RETRY, e.getMessage());
                }
                channel.basicNack(deliveryTag, false, false);
                log.error("Activity status message exceeded retry limit. eventId={}", eventId, e);
            }
        }
    }

    private ActivityStatusUpdateMessage parseMessage(byte[] payload) {
        try {
            ActivityStatusUpdateMessage msg = objectMapper.readValue(payload, ActivityStatusUpdateMessage.class);
            log.info("Received status update message: {}", msg);
            return msg;
        } catch (IOException e) {
            log.error("Failed to process message payload: {}", new String(payload, StandardCharsets.UTF_8), e);
            try {
                String s = new String(payload, StandardCharsets.UTF_8);
                String[] parts = s.split("\\|");
                if (parts.length == 2) {
                    return new ActivityStatusUpdateMessage(
                            null,
                            parts[0],
                            ActivityStatus.valueOf(parts[1]),
                            1,
                            null,
                            "legacy"
                    );
                }
            } catch (IllegalArgumentException ex) {
                log.error("Failed to process fallback message format", ex);
            }
            return null;
        }
    }

    private int nextAttempt(ActivityStatusUpdateMessage msg, Message amqpMessage) {
        if (msg != null && msg.getAttempt() != null && msg.getAttempt() > 0) {
            return msg.getAttempt() + 1;
        }
        Object headerAttempt = amqpMessage.getMessageProperties().getHeaders().get("x-attempt");
        if (headerAttempt instanceof Number n && n.intValue() > 0) {
            return n.intValue() + 1;
        }
        return 1;
    }

    private void publishRetry(ActivityStatusUpdateMessage msg, int attempt) throws IOException {
        msg.setAttempt(attempt);
        byte[] body = objectMapper.writeValueAsBytes(msg);

        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setHeader("x-event-id", msg.getEventId());
        props.setHeader("x-attempt", attempt);
        props.setExpiration(String.valueOf(backoffMillis(attempt)));

        Message retryMessage = new Message(body, props);
        rabbitTemplate.send(RabbitConfig.UPDATE_RETRY_EXCHANGE, RabbitConfig.UPDATE_RETRY_ROUTING_KEY, retryMessage);
    }

    private long backoffMillis(int attempt) {
        long value = (long) Math.pow(2, Math.max(0, attempt - 1)) * 3000L;
        return Math.min(value, 60_000L);
    }

    @Transactional
    public void handle(ActivityStatusUpdateMessage msg) {
        if (msg == null) {
            return;
        }

        Activity a = activityMapper.getById(msg.getActivityId());
        if (a == null) {
            log.warn("Activity not found for status update: {}", msg.getActivityId());
            return;
        }

        ActivityStatus currentStatus = a.getStatus();
        ActivityStatus targetStatus = msg.getTargetStatus();
        if (currentStatus == null || targetStatus == null) {
            log.warn("Skipping status update for activity {} because current or target status is missing. current={}, target={}",
                    a.getId(), currentStatus, targetStatus);
            return;
        }

        if (currentStatus == targetStatus) {
            log.info("Activity {} already has status {}, message is idempotent - skipping update",
                    a.getId(), targetStatus);
            return;
        }

        if (currentStatus.isTerminalState()) {
            log.warn("Cannot update activity {} from terminal state {} to {}",
                    a.getId(), currentStatus, targetStatus);
            return;
        }

        if (currentStatus.isProtectedState()) {
            log.warn("Skipping status update for activity {} as it is in protected review state: {}",
                    a.getId(), currentStatus);
            return;
        }

        if (!currentStatus.canTransitionTo(targetStatus)) {
            log.error("Invalid state transition for activity {}: {} -> {}. Message rejected.",
                    a.getId(), currentStatus, targetStatus);
            return;
        }

        log.info("Updating activity {} status from {} to {}", a.getId(), currentStatus, targetStatus);
        int rows = activityMapper.updateStatus(a.getId(), targetStatus);
        log.info("Status update affected rows: {}", rows);

        if (targetStatus == ActivityStatus.ActivityEnded && rows > 0) {
            try {
                int granted = volunteerHourGrantService.grantHoursForCompletedActivity(a.getId());
                log.info("Auto-granted volunteer hours for completed activity {}: {} participants",
                        a.getId(), granted);
            } catch (RuntimeException e) {
                log.error("Failed to auto-grant volunteer hours for activity {}", a.getId(), e);
            }
        }
    }
}
