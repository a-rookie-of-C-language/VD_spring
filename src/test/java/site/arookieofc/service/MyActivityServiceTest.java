package site.arookieofc.service;

import org.junit.jupiter.api.Test;
import site.arookieofc.controller.VO.MyActivityItemVO;
import site.arookieofc.controller.VO.MyActivityPageVO;
import site.arookieofc.dao.mapper.MyActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyActivityServiceTest {

    @Test
    void getMyActivitiesNormalizesEnumStringsFromDatabaseRows() {
        MyActivityMapper mapper = myActivityMapper();
        MyActivityService service = newService(mapper);
        String studentNo = "s1";
        when(mapper.countMyActivities(studentNo)).thenReturn(1);
        when(mapper.listMyActivitiesPaged(studentNo, 10, 0)).thenReturn(List.of(Map.of(
                "source", "ACTIVITY",
                "id", "a1",
                "name", "Activity",
                "status", " enrollmentstarted ",
                "type", " community_service "
        )));

        MyActivityPageVO page = service.getMyActivities(studentNo, 1, 10);

        MyActivityItemVO item = page.getItems().get(0);
        assertEquals(ActivityStatus.EnrollmentStarted, item.getStatus());
        assertEquals(ActivityType.COMMUNITY_SERVICE, item.getActivityType());
    }

    private MyActivityService newService(MyActivityMapper mapper) {
        return new MyActivityService(
                mapper,
                activityService(),
                userService());
    }

    private MyActivityMapper myActivityMapper() {
        return mock(MyActivityMapper.class);
    }

    private ActivityService activityService() {
        return mock(ActivityService.class);
    }

    private UserService userService() {
        return mock(UserService.class);
    }
}
