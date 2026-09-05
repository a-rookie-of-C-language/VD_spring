package site.arookieofc.service.messaging;

import org.junit.jupiter.api.Test;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityStartupSynchronizerTest {

    @Test
    void changeStatusLeavesActivityUnchangedWhenRequiredTimesAreMissing() {
        Activity activity = Activity.builder()
                .id("a1")
                .status(ActivityStatus.EnrollmentNotStart)
                .build();

        ActivityStartupSynchronizer.changeStatus(activity, ZoneId.of("Asia/Shanghai"));

        assertSame(ActivityStatus.EnrollmentNotStart, activity.getStatus());
    }

    @Test
    void startupSyncUsesBaseActivityQueryWithoutCollections() {
        ActivityMapper activityMapper = activityMapper();
        ActivityStartupSynchronizer synchronizer = newSynchronizer(activityMapper, true);
        when(activityMapper.listAllBase()).thenReturn(List.of());

        synchronizer.synchronizeActivitiesOnStartup();

        verify(activityMapper).listAllBase();
    }

    @Test
    void startupSyncDoesNotPropagateRuntimeMapperFailure() {
        ActivityMapper activityMapper = activityMapper();
        ActivityStartupSynchronizer synchronizer = newSynchronizer(activityMapper);
        when(activityMapper.listAllBase()).thenThrow(new IllegalStateException("db unavailable"));

        assertDoesNotThrow(synchronizer::synchronizeActivitiesOnStartup);
    }

    private ActivityMapper activityMapper() {
        return mock(ActivityMapper.class);
    }

    private ActivityStatusTaskService statusTaskService() {
        return mock(ActivityStatusTaskService.class);
    }

    private ActivityStartupSynchronizer newSynchronizer(ActivityMapper activityMapper) {
        return new ActivityStartupSynchronizer(activityMapper, statusTaskService());
    }

    private ActivityStartupSynchronizer newSynchronizer(ActivityMapper activityMapper, boolean devMode) {
        return new ActivityStartupSynchronizer(activityMapper, statusTaskService(), devMode);
    }
}
