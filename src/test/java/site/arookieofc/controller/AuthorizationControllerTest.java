package site.arookieofc.controller;

import org.junit.jupiter.api.Test;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.ActivityService;
import site.arookieofc.service.BatchImportService;
import site.arookieofc.service.FileUploadService;
import site.arookieofc.service.MyActivityService;
import site.arookieofc.service.PendingActivityService;
import site.arookieofc.service.UserService;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.dto.PendingActivityDTO;
import site.arookieofc.service.dto.UserDTO;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationControllerTest {

    @Test
    void userLookupAllowsSelfButRejectsOthers() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService, null);

        UserDTO user = UserDTO.builder().studentNo("20240001").username("Alice").build();
        when(userService.getUserByStudentNo("20240001")).thenReturn(Optional.of(user));

        Result selfResult = controller.getUserByStudentNo(new UserPrincipal("20240001", "user", "Alice"), "20240001");
        assertEquals(200, selfResult.getCode());

        assertThrows(BusinessException.class, () ->
                controller.getUserByStudentNo(new UserPrincipal("20240002", "user", "Bob"), "20240001"));
    }

    @Test
    void pendingActivityQueryForcesSubmittedByForNonAdmin() {
        PendingActivityService pendingActivityService = mock(PendingActivityService.class);
        BatchImportService batchImportService = mock(BatchImportService.class);
        PendingActivityController controller = new PendingActivityController(pendingActivityService, batchImportService);

        controller.queryPendingActivities(new UserPrincipal("20240001", "user", "Alice"), null);

        verify(pendingActivityService).countPendingActivities(null, null, null, "20240001");
        verify(pendingActivityService).listPendingActivitiesPaged(null, null, null, "20240001", 1, 10);
    }

    @Test
    void pendingActivityDetailRejectsNonOwner() {
        PendingActivityService pendingActivityService = mock(PendingActivityService.class);
        BatchImportService batchImportService = mock(BatchImportService.class);
        PendingActivityController controller = new PendingActivityController(pendingActivityService, batchImportService);

        PendingActivityDTO dto = PendingActivityDTO.builder()
                .id("p1")
                .submittedBy("owner")
                .status(ActivityStatus.UnderReview)
                .build();
        when(pendingActivityService.getPendingActivityById("p1")).thenReturn(dto);

        assertThrows(BusinessException.class, () ->
                controller.getById(new UserPrincipal("other", "user", "Bob"), "p1"));
    }

    @Test
    void activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo() {
        ActivityService activityService = mock(ActivityService.class);
        ActivityController controller = new ActivityController(
                activityService,
                mock(UserService.class),
                mock(PendingActivityService.class),
                mock(FileUploadService.class),
                mock(BatchImportService.class),
                mock(MyActivityService.class)
        );

        ActivityDTO request = ActivityDTO.builder()
                .functionary("spoofed")
                .name("Test")
                .type(ActivityType.COMMUNITY_SERVICE)
                .description("desc")
                .enrollmentStartTime(OffsetDateTime.now())
                .enrollmentEndTime(OffsetDateTime.now().plusHours(1))
                .startTime(OffsetDateTime.now().plusHours(2))
                .expectedEndTime(OffsetDateTime.now().plusHours(3))
                .build();

        assertThrows(BusinessException.class, () ->
                controller.create(new UserPrincipal("20240001", "user", "Alice"), request));

        ActivityDTO created = ActivityDTO.builder()
                .id("a1")
                .functionary("20249999")
                .name("Test")
                .type(ActivityType.COMMUNITY_SERVICE)
                .description("desc")
                .attachment(Collections.emptyList())
                .participants(Collections.emptyList())
                .build();
        when(activityService.createActivity(request)).thenReturn(created);

        Result result = controller.create(new UserPrincipal("20249999", "functionary", "Leader"), request);
        assertEquals(200, result.getCode());
        assertEquals("20249999", request.getFunctionary());
    }

    @Test
    void activityDeleteRejectsNonOwner() {
        ActivityService activityService = mock(ActivityService.class);
        ActivityController controller = new ActivityController(
                activityService,
                mock(UserService.class),
                mock(PendingActivityService.class),
                mock(FileUploadService.class),
                mock(BatchImportService.class),
                mock(MyActivityService.class)
        );

        ActivityDTO existing = ActivityDTO.builder()
                .id("a1")
                .functionary("owner")
                .build();
        when(activityService.getActivityById("a1")).thenReturn(existing);

        assertThrows(BusinessException.class, () ->
                controller.delete(new UserPrincipal("other", "functionary", "Other"), "a1"));

        controller.delete(new UserPrincipal("owner", "functionary", "Owner"), "a1");
        verify(activityService).deleteActivity("a1");
    }
}
