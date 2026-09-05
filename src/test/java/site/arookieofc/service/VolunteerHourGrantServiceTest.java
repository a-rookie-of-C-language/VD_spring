package site.arookieofc.service;

import org.junit.jupiter.api.Test;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.entity.PersonalHourRequest;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.entity.VolunteerHourGrantRecord;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.dao.mapper.PersonalHourRequestMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.dao.mapper.VolunteerHourGrantRecordMapper;
import site.arookieofc.service.BO.ActivityStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VolunteerHourGrantServiceTest {

    @Test
    void grantHoursForImportedActivityNormalizesParticipantsBeforeGranting() {
        UserMapper userMapper = userMapper();
        VolunteerHourGrantRecordMapper grantRecordMapper = grantRecordMapper();
        VolunteerHourGrantService service = newService(userMapper, grantRecordMapper);
        String activityId = "a1";
        String firstStudentNo = "s1";
        String secondStudentNo = "s2";
        when(userMapper.getUserByStudentNo(firstStudentNo)).thenReturn(user(firstStudentNo, 1.0));
        when(userMapper.getUserByStudentNo(secondStudentNo)).thenReturn(user(secondStudentNo, 2.0));
        when(userMapper.incrementTotalHours(any(), eq(2.5))).thenReturn(1);

        int granted = service.grantHoursForImportedActivity(activityId,
                List.of(" " + firstStudentNo + " ", firstStudentNo, " ", secondStudentNo), 2.5, "Import");

        assertEquals(2, granted);
        verify(userMapper, times(1)).getUserByStudentNo(firstStudentNo);
        verify(userMapper, times(1)).getUserByStudentNo(secondStudentNo);
        verify(userMapper, never()).getUserByStudentNo(" ");
        verify(grantRecordMapper, times(2)).insert(any(VolunteerHourGrantRecord.class));
    }

    @Test
    void grantHoursForMissingCompletedActivityThrowsBusinessNotFound() {
        ActivityMapper activityMapper = activityMapper();
        VolunteerHourGrantService service = newService(activityMapper);
        when(activityMapper.getById("missing")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.grantHoursForCompletedActivity("missing"));
    }

    @Test
    void grantHoursForMissingPersonalRequestThrowsBusinessNotFound() {
        PersonalHourRequestMapper requestMapper = requestMapper();
        VolunteerHourGrantService service = newService(requestMapper);
        when(requestMapper.getById("missing")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.grantHoursForApprovedRequest("missing"));
    }

    @Test
    void grantHoursForCompletedActivitySkipsNonEndedActivity() {
        ActivityMapper activityMapper = activityMapper();
        VolunteerHourGrantService service = newService(activityMapper);
        String activityId = "a1";
        String participantStudentNo = "s1";
        when(activityMapper.getById(activityId)).thenReturn(Activity.builder()
                .id(activityId)
                .status(ActivityStatus.EnrollmentStarted)
                .participants(List.of(participantStudentNo))
                .duration(1.0)
                .build());

        assertEquals(0, service.grantHoursForCompletedActivity(activityId));
    }

    private VolunteerHourGrantService newService(ActivityMapper activityMapper) {
        return newService(
                userMapper(),
                activityMapper,
                requestMapper(),
                grantRecordMapper());
    }

    private VolunteerHourGrantService newService(PersonalHourRequestMapper requestMapper) {
        return newService(
                userMapper(),
                activityMapper(),
                requestMapper,
                grantRecordMapper());
    }

    private VolunteerHourGrantService newService(
            UserMapper userMapper,
            VolunteerHourGrantRecordMapper grantRecordMapper) {
        return newService(
                userMapper,
                activityMapper(),
                requestMapper(),
                grantRecordMapper);
    }

    private UserMapper userMapper() {
        return mock(UserMapper.class);
    }

    private User user(String studentNo, double totalHours) {
        return User.builder()
                .studentNo(studentNo)
                .totalHours(totalHours)
                .build();
    }

    private ActivityMapper activityMapper() {
        return mock(ActivityMapper.class);
    }

    private PersonalHourRequestMapper requestMapper() {
        return mock(PersonalHourRequestMapper.class);
    }

    private VolunteerHourGrantRecordMapper grantRecordMapper() {
        return mock(VolunteerHourGrantRecordMapper.class);
    }

    private VolunteerHourGrantService newService(
            UserMapper userMapper,
            ActivityMapper activityMapper,
            PersonalHourRequestMapper requestMapper,
            VolunteerHourGrantRecordMapper grantRecordMapper) {
        return new VolunteerHourGrantService(userMapper, activityMapper, requestMapper, grantRecordMapper);
    }
}
