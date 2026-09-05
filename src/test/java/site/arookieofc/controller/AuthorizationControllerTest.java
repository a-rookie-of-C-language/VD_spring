package site.arookieofc.controller;

import org.junit.jupiter.api.Test;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.ActivityQueryVO;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationControllerTest {

    @Test
    void userLookupAllowsSelfButRejectsOthers() {
        UserService userService = userService();
        UserController controller = new UserController(userService, null);
        String studentNo = "20240001";
        String otherStudentNo = "20240002";
        String username = "Alice";
        String otherUsername = "Bob";
        String userRole = "user";

        UserDTO user = UserDTO.builder().studentNo(studentNo).username(username).build();
        when(userService.getUserByStudentNo(studentNo)).thenReturn(Optional.of(user));

        Result selfResult = controller.getUserByStudentNo(principal(studentNo, userRole, username), studentNo);
        assertEquals(200, selfResult.getCode());

        assertThrows(BusinessException.class, () ->
                controller.getUserByStudentNo(principal(otherStudentNo, userRole, otherUsername), studentNo));
    }

    @Test
    void pendingActivityQueryForcesSubmittedByForNonAdmin() {
        PendingActivityService pendingActivityService = pendingActivityService();
        PendingActivityController controller = newPendingActivityController(pendingActivityService);
        String studentNo = "20240001";
        String username = "Alice";
        String userRole = "user";

        controller.queryPendingActivities(principal(studentNo, userRole, username), null);

        verify(pendingActivityService).countPendingActivities(null, null, null, studentNo);
        verify(pendingActivityService).listPendingActivitiesPaged(null, null, null, studentNo, 1, 10);
    }

    @Test
    void pendingActivityQueryRejectsMissingPrincipalBeforeServiceCall() {
        PendingActivityService pendingActivityService = pendingActivityService();
        PendingActivityController controller = newPendingActivityController(pendingActivityService);

        assertThrows(BusinessException.class, () -> controller.queryPendingActivities(null, null));

        verify(pendingActivityService, never()).countPendingActivities(null, null, null, null);
    }

    @Test
    void pendingActivityDetailRejectsNonOwner() {
        PendingActivityService pendingActivityService = pendingActivityService();
        PendingActivityController controller = newPendingActivityController(pendingActivityService);
        String pendingActivityId = "p1";
        String ownerStudentNo = "owner";
        String otherUsername = "Bob";
        String userRole = "user";

        PendingActivityDTO dto = PendingActivityDTO.builder()
                .id(pendingActivityId)
                .submittedBy(ownerStudentNo)
                .status(ActivityStatus.UnderReview)
                .build();
        when(pendingActivityService.getPendingActivityById(pendingActivityId)).thenReturn(dto);

        assertThrows(BusinessException.class, () ->
                controller.getById(principal("other", userRole, otherUsername), pendingActivityId));
    }

    @Test
    void activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo() {
        ActivityService activityService = activityService();
        ActivityController controller = newActivityController(activityService);
        String studentNo = "20240001";
        String functionaryStudentNo = "20249999";
        String activityId = "a1";
        String userRole = "user";
        String functionaryRole = "functionary";
        String username = "Alice";
        String functionaryUsername = "Leader";
        String activityName = "Test";
        String activityDescription = "desc";
        ActivityType activityType = ActivityType.COMMUNITY_SERVICE;

        OffsetDateTime now = OffsetDateTime.now();
        ActivityDTO request = ActivityDTO.builder()
                .functionary("spoofed")
                .name(activityName)
                .type(activityType)
                .description(activityDescription)
                .enrollmentStartTime(now)
                .enrollmentEndTime(now.plusHours(1))
                .startTime(now.plusHours(2))
                .expectedEndTime(now.plusHours(3))
                .build();

        assertThrows(BusinessException.class, () ->
                controller.create(principal(studentNo, userRole, username), request));

        ActivityDTO created = ActivityDTO.builder()
                .id(activityId)
                .functionary(functionaryStudentNo)
                .name(activityName)
                .type(activityType)
                .description(activityDescription)
                .attachment(List.of())
                .participants(List.of())
                .build();
        when(activityService.createActivity(request)).thenReturn(created);

        Result result = controller.create(principal(functionaryStudentNo, functionaryRole, functionaryUsername), request);
        assertEquals(200, result.getCode());
        assertEquals(functionaryStudentNo, request.getFunctionary());
    }

    @Test
    void activityCreateRejectsMissingPrincipalBeforeServiceCall() {
        ActivityService activityService = activityService();
        ActivityController controller = newActivityController(activityService);

        assertThrows(BusinessException.class, () -> controller.create(null, ActivityDTO.builder().build()));

        verify(activityService, never()).createActivity(any(ActivityDTO.class));
    }

    @Test
    void activityEnrollRejectsMissingPrincipalBeforeServiceCall() {
        ActivityService activityService = activityService();
        ActivityController controller = newActivityController(activityService);
        String activityId = "a1";

        assertThrows(BusinessException.class, () -> controller.enroll(activityId, null));

        verify(activityService, never()).enroll(anyString(), anyString());
    }

    @Test
    void activityReviewRequiresAdminBeforeServiceCall() {
        ActivityService activityService = activityService();
        ActivityController controller = newActivityController(activityService);
        String studentNo = "20240001";
        String activityId = "a1";
        String userRole = "user";
        String username = "Alice";

        assertThrows(BusinessException.class, () ->
                controller.review(principal(studentNo, userRole, username), activityId, true, null));

        verify(activityService, never()).reviewActivity(anyString(), anyBoolean(), any(), anyString());
    }

    @Test
    void activityQueryWithStatusDoesNotExposeHiddenRowsForPlainUser() {
        ActivityService activityService = activityService();
        ActivityController controller = newActivityController(activityService);
        ActivityQueryVO query = ActivityQueryVO.builder()
                .status(ActivityStatus.UnderReview)
                .build();

        controller.queryActivities(principal("20240001", "user", "Alice"), query);

        verify(activityService).countActivities(null, ActivityStatus.UnderReview, null, null, null, null, null);
        verify(activityService).listActivitiesPaged(null, ActivityStatus.UnderReview, null, null, null, null, null, 1, 10);
        verify(activityService, never()).countActivitiesAll(any(), any(), any(), any(), any(), any(), any());
        verify(activityService, never()).listActivitiesPagedAll(any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void activityDeleteRejectsNonOwner() {
        ActivityService activityService = activityService();
        ActivityController controller = newActivityController(activityService);
        String activityId = "a1";
        String ownerStudentNo = "owner";
        String otherStudentNo = "other";
        String functionaryRole = "functionary";
        String otherUsername = "Other";
        String ownerUsername = "Owner";

        ActivityDTO existing = activity(activityId, ownerStudentNo);
        when(activityService.getActivityById(activityId)).thenReturn(existing);

        assertThrows(BusinessException.class, () ->
                controller.delete(principal(otherStudentNo, functionaryRole, otherUsername), activityId));

        controller.delete(principal(ownerStudentNo, functionaryRole, ownerUsername), activityId);
        verify(activityService).deleteActivity(activityId);
    }

    @Test
    void activityUpdateRejectsNonOwnerAndPreservesExistingFunctionary() {
        ActivityService activityService = activityService();
        ActivityController controller = newActivityController(activityService);
        String activityId = "a1";
        String ownerStudentNo = "owner";
        String otherStudentNo = "other";
        String updatedActivityName = "Updated";
        ActivityType updatedActivityType = ActivityType.COMMUNITY_SERVICE;

        ActivityDTO existing = activity(activityId, ownerStudentNo);
        ActivityDTO request = ActivityDTO.builder()
                .functionary("spoofed")
                .name(updatedActivityName)
                .type(updatedActivityType)
                .build();
        ActivityDTO updated = activity(activityId, ownerStudentNo, updatedActivityName);
        when(activityService.getActivityById(activityId)).thenReturn(existing);
        when(activityService.updateActivity(activityId, request)).thenReturn(updated);

        assertThrows(BusinessException.class, () ->
                controller.update(activityId, principal(otherStudentNo, "functionary", "Other"), request));

        Result result = controller.update(activityId, principal(ownerStudentNo, "functionary", "Owner"), request);
        assertEquals(200, result.getCode());
        assertEquals(ownerStudentNo, request.getFunctionary());
        verify(activityService).updateActivity(activityId, request);
    }

    private PendingActivityService pendingActivityService() {
        return mock(PendingActivityService.class);
    }

    private ActivityService activityService() {
        return mock(ActivityService.class);
    }

    private UserService userService() {
        return mock(UserService.class);
    }

    private FileUploadService fileUploadService() {
        return mock(FileUploadService.class);
    }

    private MyActivityService myActivityService() {
        return mock(MyActivityService.class);
    }

    private BatchImportService batchImportService() {
        return mock(BatchImportService.class);
    }

    private UserPrincipal principal(String studentNo, String role, String username) {
        return new UserPrincipal(studentNo, role, username);
    }

    private ActivityDTO activity(String id, String functionary) {
        return activity(id, functionary, null);
    }

    private ActivityDTO activity(String id, String functionary, String name) {
        return ActivityDTO.builder()
                .id(id)
                .functionary(functionary)
                .name(name)
                .build();
    }

    private PendingActivityController newPendingActivityController(PendingActivityService pendingActivityService) {
        return new PendingActivityController(pendingActivityService, batchImportService());
    }

    private ActivityController newActivityController(ActivityService activityService) {
        return new ActivityController(
                activityService,
                userService(),
                pendingActivityService(),
                fileUploadService(),
                batchImportService(),
                myActivityService());
    }
}
