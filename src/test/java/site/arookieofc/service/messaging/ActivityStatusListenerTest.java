package site.arookieofc.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.VolunteerHourGrantService;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityStatusListenerTest {

    @Test
    void onMessageRejectsInvalidPayload() throws Exception {
        ActivityMapper activityMapper = activityMapper();
        ActivityStatusListener listener = newListener(activityMapper);
        Channel channel = channel();

        listener.onMessage(
                amqpMessage("{not-json"),
                channel,
                42L);

        verify(channel).basicNack(42L, false, false);
        verify(activityMapper, never()).updateStatus(anyString(), any());
    }

    @Test
    void onMessageSupportsLegacyPipeFormat() throws Exception {
        ActivityMapper activityMapper = activityMapper();
        ActivityStatusTaskService taskService = taskService();
        ActivityStatusListener listener = newListener(activityMapper, taskService);
        String activityId = "a1";
        when(activityMapper.getById(activityId)).thenReturn(activityWithStatus(ActivityStatus.EnrollmentNotStart));
        when(activityMapper.updateStatus(activityId, ActivityStatus.EnrollmentStarted)).thenReturn(1);
        Channel channel = channel();

        listener.onMessage(
                amqpMessage(activityId + "|EnrollmentStarted"),
                channel,
                43L);

        verify(activityMapper).updateStatus(activityId, ActivityStatus.EnrollmentStarted);
        verify(taskService, never()).markDone(anyString());
        verify(channel).basicAck(43L, false);
    }

    @Test
    void onMessageUsesInjectedObjectMapper() throws Exception {
        ActivityMapper activityMapper = activityMapper();
        ActivityStatusTaskService taskService = taskService();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        String eventId = "event-1";
        String activityId = "a1";
        ActivityStatusUpdateMessage mapped = updateMessage(eventId, activityId, ActivityStatus.EnrollmentStarted);
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        when(objectMapper.readValue(payload, ActivityStatusUpdateMessage.class)).thenReturn(mapped);
        when(activityMapper.getById(activityId)).thenReturn(activityWithStatus(ActivityStatus.EnrollmentNotStart));
        ActivityStatusListener listener = new ActivityStatusListener(
                activityMapper,
                grantService(),
                taskService,
                rabbitTemplate(),
                objectMapper);
        Channel channel = channel();

        listener.onMessage(amqpMessage(payload), channel, 44L);

        verify(objectMapper).readValue(payload, ActivityStatusUpdateMessage.class);
        verify(activityMapper).updateStatus(activityId, ActivityStatus.EnrollmentStarted);
        verify(taskService).markDone(eventId);
        verify(channel).basicAck(44L, false);
    }

    @Test
    void handleSkipsUpdateWhenCurrentStatusIsMissing() {
        ActivityMapper activityMapper = activityMapper();
        ActivityStatusListener listener = newListener(activityMapper);
        when(activityMapper.getById("a1")).thenReturn(activityWithStatus(null));

        listener.handle(updateMessage(ActivityStatus.EnrollmentStarted));

        verify(activityMapper, never()).updateStatus(anyString(), any());
    }

    @Test
    void handleSkipsUpdateWhenTargetStatusIsMissing() {
        ActivityMapper activityMapper = activityMapper();
        ActivityStatusListener listener = newListener(activityMapper);
        when(activityMapper.getById("a1")).thenReturn(activityWithStatus(ActivityStatus.EnrollmentNotStart));

        listener.handle(updateMessage(null));

        verify(activityMapper, never()).updateStatus(anyString(), any());
    }

    @Test
    void handleDoesNotPropagateAutoGrantRuntimeFailureAfterStatusUpdate() {
        ActivityMapper activityMapper = activityMapper();
        VolunteerHourGrantService grantService = grantService();
        ActivityStatusListener listener = newListener(activityMapper, grantService);
        String activityId = "a1";
        when(activityMapper.getById(activityId)).thenReturn(activityWithStatus(ActivityStatus.ActivityStarted));
        when(activityMapper.updateStatus(activityId, ActivityStatus.ActivityEnded)).thenReturn(1);
        doThrow(new IllegalStateException("grant failed"))
                .when(grantService).grantHoursForCompletedActivity(activityId);

        listener.handle(updateMessage(ActivityStatus.ActivityEnded));

        verify(activityMapper).updateStatus(activityId, ActivityStatus.ActivityEnded);
        verify(grantService).grantHoursForCompletedActivity(activityId);
    }

    @Test
    void onMessageRetriesWhenHandleThrowsRuntimeFailure() throws Exception {
        ActivityMapper activityMapper = activityMapper();
        RabbitTemplate rabbitTemplate = rabbitTemplate();
        ActivityStatusTaskService taskService = taskService();
        ActivityStatusListener listener = new ActivityStatusListener(
                activityMapper,
                grantService(),
                taskService,
                rabbitTemplate,
                new ObjectMapper());
        String eventId = "event-1";
        String activityId = "a1";
        when(activityMapper.getById(activityId)).thenThrow(new IllegalStateException("db down"));
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-attempt", 1);
        String payload = """
                {"eventId":"%s","activityId":"%s","targetStatus":"EnrollmentStarted","attempt":1,"source":"test"}
                """.formatted(eventId, activityId);
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
        Channel channel = channel();

        listener.onMessage(message, channel, 42L);

        verify(rabbitTemplate).send(anyString(), anyString(), any(Message.class));
        verify(taskService).markConsumeFailed(eventId, 2, "db down");
        verify(channel).basicAck(42L, false);
    }

    private ActivityStatusListener newListener(ActivityMapper activityMapper) {
        return newListener(activityMapper, grantService());
    }

    private Channel channel() {
        return mock(Channel.class);
    }

    private ActivityMapper activityMapper() {
        return mock(ActivityMapper.class);
    }

    private ActivityStatusTaskService taskService() {
        return mock(ActivityStatusTaskService.class);
    }

    private VolunteerHourGrantService grantService() {
        return mock(VolunteerHourGrantService.class);
    }

    private RabbitTemplate rabbitTemplate() {
        return mock(RabbitTemplate.class);
    }

    private ActivityStatusListener newListener(ActivityMapper activityMapper,
                                              VolunteerHourGrantService grantService) {
        return new ActivityStatusListener(
                activityMapper,
                grantService,
                taskService(),
                rabbitTemplate(),
                new ObjectMapper());
    }

    private ActivityStatusListener newListener(ActivityMapper activityMapper,
                                              ActivityStatusTaskService taskService) {
        return new ActivityStatusListener(
                activityMapper,
                grantService(),
                taskService,
                rabbitTemplate(),
                new ObjectMapper());
    }

    private Message amqpMessage(String body) {
        return amqpMessage(body.getBytes(StandardCharsets.UTF_8));
    }

    private Message amqpMessage(byte[] body) {
        return new Message(body, new MessageProperties());
    }

    private Activity activityWithStatus(ActivityStatus status) {
        return Activity.builder()
                .id("a1")
                .status(status)
                .build();
    }

    private ActivityStatusUpdateMessage updateMessage(ActivityStatus targetStatus) {
        return updateMessage("event-1", "a1", targetStatus);
    }

    private ActivityStatusUpdateMessage updateMessage(String eventId, String activityId, ActivityStatus targetStatus) {
        return new ActivityStatusUpdateMessage(eventId, activityId, targetStatus, 1, null, "test");
    }
}
