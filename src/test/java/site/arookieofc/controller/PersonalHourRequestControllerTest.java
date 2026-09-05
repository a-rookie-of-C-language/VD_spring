package site.arookieofc.controller;

import org.junit.jupiter.api.Test;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.PersonalHourRequestService;
import site.arookieofc.service.dto.PersonalHourRequestDTO;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PersonalHourRequestControllerTest {

    @Test
    void submitRequestRejectsMissingPrincipalBeforeServiceCall() {
        PersonalHourRequestService requestService = requestService();
        PersonalHourRequestController controller = newController(requestService);
        OffsetDateTime now = OffsetDateTime.now();

        assertThrows(BusinessException.class, () -> controller.submitRequest(
                null,
                "Activity",
                "Teacher",
                ActivityType.COMMUNITY_SERVICE,
                "desc",
                now.toString(),
                now.plusHours(1).toString(),
                1.0,
                null));

        verify(requestService, never()).submitRequest(any(PersonalHourRequestDTO.class), anyString());
    }

    @Test
    void reviewRequestRejectsAdminWithoutStudentNoBeforeServiceCall() {
        PersonalHourRequestService requestService = requestService();
        PersonalHourRequestController controller = newController(requestService);

        assertThrows(BusinessException.class, () ->
                controller.reviewRequest(new UserPrincipal(" ", "admin", "Admin"), "r1", true, null));

        verify(requestService, never()).reviewRequest(anyString(), anyBoolean(), any(), anyString());
    }

    @Test
    void getMyRequestsRejectsMissingPrincipalBeforeServiceCall() {
        PersonalHourRequestService requestService = requestService();
        PersonalHourRequestController controller = newController(requestService);

        assertThrows(BusinessException.class, () ->
                controller.getMyRequests(null, 1, 10, ActivityStatus.UnderReview));

        verify(requestService, never()).countRequests(anyString(), any(), any(), any());
        verify(requestService, never()).listRequestsPaged(anyString(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void deleteRequestRejectsMissingPrincipalBeforeServiceCall() {
        PersonalHourRequestService requestService = requestService();
        PersonalHourRequestController controller = newController(requestService);

        assertThrows(BusinessException.class, () -> controller.deleteRequest(null, "r1"));

        verify(requestService, never()).deleteRequest(anyString(), anyString());
    }

    private PersonalHourRequestController newController(PersonalHourRequestService requestService) {
        return new PersonalHourRequestController(requestService);
    }

    private PersonalHourRequestService requestService() {
        return mock(PersonalHourRequestService.class);
    }
}
