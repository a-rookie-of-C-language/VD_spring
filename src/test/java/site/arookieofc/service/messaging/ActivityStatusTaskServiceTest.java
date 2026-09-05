package site.arookieofc.service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.AmqpException;
import org.junit.jupiter.api.Test;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.arookieofc.dao.entity.ActivityStatusTask;
import site.arookieofc.dao.mapper.ActivityStatusTaskMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityStatusTaskServiceTest {

    private static final LocalDateTime EXECUTE_AT = LocalDateTime.of(2026, 5, 31, 12, 0);

    @Test
    void recoverPendingTasksMarksInvalidTargetStatusDeadWithoutPublishing() {
        ActivityStatusTaskMapper taskMapper = taskMapper();
        RabbitTemplate rabbitTemplate = rabbitTemplate();
        ActivityStatusTaskService service = newService(taskMapper, rabbitTemplate);
        String eventId = "event-1";
        ActivityStatusTask task = statusTask(eventId, "activity-1", "BadStatus", 2);
        when(taskMapper.listDispatchable(eq(100), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(task));

        service.recoverPendingTasks();

        verify(taskMapper).markDead(eq(eventId), eq(3), eq("invalid target status: BadStatus"), any(LocalDateTime.class));
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(), any());
    }

    @Test
    void recoverPendingTasksMarksFailedWhenPublisherThrowsAmqpException() {
        ActivityStatusTaskMapper taskMapper = taskMapper();
        RabbitTemplate rabbitTemplate = rabbitTemplate();
        ActivityStatusTaskService service = newService(taskMapper, rabbitTemplate);
        String eventId = "event-2";
        ActivityStatusTask task = statusTask(eventId, "activity-2", "EnrollmentStarted", 0);
        when(taskMapper.listDispatchable(eq(100), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(task));
        when(rabbitTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.ChannelCallback<Object> callback = invocation.getArgument(0);
            Channel channel = mock(Channel.class);
            org.mockito.Mockito.doThrow(new AmqpException("stop"))
                    .when(channel).waitForConfirmsOrDie(5000);
            return callback.doInRabbit(channel);
        });

        service.recoverPendingTasks();

        verify(taskMapper).markFailed(eq(eventId), eq(1), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void recoverPendingTasksUsesInjectedObjectMapperForPayloadSerialization() throws Exception {
        ActivityStatusTaskMapper taskMapper = taskMapper();
        RabbitTemplate rabbitTemplate = rabbitTemplate();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ActivityStatusTaskService service = newService(taskMapper, rabbitTemplate, objectMapper);
        String eventId = "event-3";
        ActivityStatusTask task = statusTask(eventId, "activity-3", "EnrollmentStarted", 0);
        when(taskMapper.listDispatchable(eq(100), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(task));
        when(objectMapper.writeValueAsBytes(any(ActivityStatusUpdateMessage.class)))
                .thenThrow(new JsonProcessingException("serialize failed") {});

        service.recoverPendingTasks();

        verify(objectMapper).writeValueAsBytes(any(ActivityStatusUpdateMessage.class));
        verify(taskMapper).markFailed(eq(eventId), eq(1), eq("serialize failed"), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(), any());
    }

    private ActivityStatusTaskMapper taskMapper() {
        return mock(ActivityStatusTaskMapper.class);
    }

    private RabbitTemplate rabbitTemplate() {
        return mock(RabbitTemplate.class);
    }

    private ActivityStatusTask statusTask(String eventId, String activityId, String targetStatus, int attempt) {
        return ActivityStatusTask.builder()
                .eventId(eventId)
                .activityId(activityId)
                .targetStatus(targetStatus)
                .executeAt(EXECUTE_AT)
                .attempt(attempt)
                .build();
    }

    private ActivityStatusTaskService newService(ActivityStatusTaskMapper taskMapper, RabbitTemplate rabbitTemplate) {
        return newService(
                taskMapper,
                rabbitTemplate,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private ActivityStatusTaskService newService(
            ActivityStatusTaskMapper taskMapper,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {
        return new ActivityStatusTaskService(
                taskMapper,
                rabbitTemplate,
                objectMapper);
    }
}
