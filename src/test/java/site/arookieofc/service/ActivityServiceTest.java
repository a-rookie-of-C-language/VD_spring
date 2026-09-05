package site.arookieofc.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.messaging.ActivityStatusTaskService;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityServiceTest {

    @Test
    void updateActivityResubmitsFailedReviewAndClearsReviewFields() {
        ActivityMapper activityMapper = activityMapper();
        ActivityService service = newService(activityMapper);
        Activity current = activity("Old", ActivityStatus.FailReview)
                .functionary("owner")
                .rejectedReason("bad")
                .isFull(false)
                .build();
        Activity updated = activity("Updated", ActivityStatus.UnderReview)
                .functionary("owner")
                .isFull(false)
                .build();
        when(activityMapper.getById("a1")).thenReturn(current, updated);

        service.updateActivity("a1", updateRequest().build());

        ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
        verify(activityMapper).update(activityCaptor.capture());
        Activity saved = activityCaptor.getValue();

        assertSame(ActivityStatus.UnderReview, saved.getStatus());
        assertNull(saved.getRejectedReason());
        assertNull(saved.getReviewedAt());
        assertNull(saved.getReviewedBy());
    }

    @Test
    void createActivityDoesNotScheduleStatusTasksBeforeReviewApproval() {
        ActivityMapper activityMapper = activityMapper();
        ActivityStatusTaskService taskService = taskService();
        ActivityService service = newService(activityMapper, taskService);
        Activity created = activity("New", ActivityStatus.UnderReview)
                .isFull(false)
                .build();
        OffsetDateTime now = OffsetDateTime.now();
        when(activityMapper.getById("a1")).thenReturn(created);

        service.createActivity(ActivityDTO.builder()
                .id("a1")
                .name("New")
                .type(ActivityType.COMMUNITY_SERVICE)
                .enrollmentStartTime(now.plusDays(1))
                .enrollmentEndTime(now.plusDays(2))
                .startTime(now.plusDays(3))
                .endTime(now.plusDays(4))
                .build());

        verify(taskService, never()).scheduleStatusUpdate(anyString(), any(), any(), anyString());
    }

    @Test
    void createActivityStopsBeforeInsertWhenCoverUploadFails() throws IOException {
        ActivityMapper activityMapper = activityMapper();
        FileUploadService fileUploadService = fileUploadService();
        MultipartFile coverFile = coverFile();
        ActivityService service = newService(activityMapper, fileUploadService);
        when(fileUploadService.uploadCoverImage(coverFile)).thenThrow(new IOException("disk"));

        assertThrows(IllegalArgumentException.class, () -> service.createActivity(activityRequest("New", coverFile)
                .id("a1")
                .build()));

        verify(activityMapper, never()).insert(any(Activity.class));
    }

    @Test
    void updateActivityStopsBeforeUpdateWhenCoverUploadFails() throws IOException {
        ActivityMapper activityMapper = activityMapper();
        FileUploadService fileUploadService = fileUploadService();
        MultipartFile coverFile = coverFile();
        ActivityService service = newService(activityMapper, fileUploadService);
        when(activityMapper.getById("a1")).thenReturn(activity("Old", ActivityStatus.UnderReview).build());
        when(fileUploadService.uploadCoverImage(coverFile)).thenThrow(new IOException("disk"));

        assertThrows(IllegalArgumentException.class, () -> service.updateActivity("a1", activityRequest("Updated", coverFile)
                .build()));

        verify(activityMapper, never()).update(any(Activity.class));
    }

    @Test
    void updateActivityKeepsFunctionaryAndFiltersBlankParticipantsWhenReplacingParticipants() {
        ActivityMapper activityMapper = activityMapper();
        ActivityService service = newService(activityMapper);
        Activity current = activity("Old", ActivityStatus.UnderReview)
                .functionary("owner")
                .isFull(false)
                .build();
        Activity updated = activity("Updated", ActivityStatus.UnderReview)
                .functionary("owner")
                .isFull(false)
                .participants(List.of("owner", "student1"))
                .build();
        when(activityMapper.getById("a1")).thenReturn(current, updated);

        service.updateActivity("a1", updateRequest()
                .participants(Arrays.asList("student1", " ", null, "owner", "student1"))
                .build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(activityMapper).insertParticipants(eq("a1"), participantsCaptor.capture());

        assertEquals(List.of("owner", "student1"), participantsCaptor.getValue());
    }

    private ActivityService newService(ActivityMapper activityMapper) {
        return newService(activityMapper, fileUploadService(), taskService());
    }

    private MultipartFile coverFile() {
        MultipartFile coverFile = mock(MultipartFile.class);
        when(coverFile.isEmpty()).thenReturn(false);
        return coverFile;
    }

    private ActivityMapper activityMapper() {
        return mock(ActivityMapper.class);
    }

    private FileUploadService fileUploadService() {
        return mock(FileUploadService.class);
    }

    private ActivityStatusTaskService taskService() {
        return mock(ActivityStatusTaskService.class);
    }

    private ApplicationEventPublisher eventPublisher() {
        return mock(ApplicationEventPublisher.class);
    }

    private ActivityDTO.ActivityDTOBuilder activityRequest(String name, MultipartFile coverFile) {
        return ActivityDTO.builder()
                .name(name)
                .type(ActivityType.COMMUNITY_SERVICE)
                .coverFile(coverFile);
    }

    private ActivityDTO.ActivityDTOBuilder updateRequest() {
        return ActivityDTO.builder()
                .functionary("owner")
                .name("Updated")
                .type(ActivityType.COMMUNITY_SERVICE);
    }

    private ActivityService newService(ActivityMapper activityMapper, FileUploadService fileUploadService) {
        return newService(activityMapper, fileUploadService, taskService());
    }

    private ActivityService newService(ActivityMapper activityMapper, ActivityStatusTaskService taskService) {
        return newService(activityMapper, fileUploadService(), taskService);
    }

    private ActivityService newService(ActivityMapper activityMapper,
                                       FileUploadService fileUploadService,
                                       ActivityStatusTaskService taskService) {
        return new ActivityService(
                activityMapper,
                fileUploadService,
                taskService,
                eventPublisher());
    }

    private Activity.ActivityBuilder activity(String name, ActivityStatus status) {
        return Activity.builder()
                .id("a1")
                .name(name)
                .type(ActivityType.COMMUNITY_SERVICE)
                .status(status);
    }
}
