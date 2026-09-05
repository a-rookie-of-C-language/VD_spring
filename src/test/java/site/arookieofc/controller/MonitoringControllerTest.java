package site.arookieofc.controller;

import org.junit.jupiter.api.Test;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.controller.VO.MonitoringLogVO;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BusinessOperationLogService;
import site.arookieofc.service.MonitoringService;
import site.arookieofc.service.messaging.ActivityStatusTaskService;
import site.arookieofc.service.monitor.DeveloperMonitorService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void replayDeadTasksBoundsLimitBeforeServiceCallAndResponse() {
        ActivityStatusTaskService taskService = taskService();
        MonitoringController controller = newController(taskService);
        when(taskService.replayDeadTasks(1)).thenReturn(2);

        Result result = controller.replayDeadTasks(superAdmin(), -20);

        verify(taskService).replayDeadTasks(1);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(1, data.get("limit"));
        assertEquals(2, data.get("replayed"));
    }

    @Test
    void getLogsNormalizesNullSizeBeforeServiceCall() {
        MonitoringService monitoringService = monitoringService();
        MonitoringController controller = newController(monitoringService);
        when(monitoringService.getRecentLogs(50, "error")).thenReturn(List.of(
                MonitoringLogVO.builder().message("error").build()));

        controller.getLogs(superAdmin(), null, "error");

        verify(monitoringService).getRecentLogs(50, "error");
    }

    @Test
    void getBusinessLogsNormalizesOversizedSizeBeforeServiceCall() {
        BusinessOperationLogService logService = logService();
        MonitoringController controller = newController(logService);

        controller.getBusinessLogs(superAdmin(), 1000, null);

        verify(logService).queryRecent(200, null);
    }

    private UserPrincipal superAdmin() {
        return new UserPrincipal("root", "SUPERADMIN", "Root");
    }

    private MonitoringService monitoringService() {
        return mock(MonitoringService.class);
    }

    private BusinessOperationLogService logService() {
        return mock(BusinessOperationLogService.class);
    }

    private ActivityStatusTaskService taskService() {
        return mock(ActivityStatusTaskService.class);
    }

    private DeveloperMonitorService developerMonitorService() {
        return mock(DeveloperMonitorService.class);
    }

    private MonitoringController newController(MonitoringService monitoringService) {
        return newController(
                monitoringService,
                logService(),
                taskService());
    }

    private MonitoringController newController(BusinessOperationLogService logService) {
        return newController(
                monitoringService(),
                logService,
                taskService());
    }

    private MonitoringController newController(ActivityStatusTaskService taskService) {
        return newController(
                monitoringService(),
                logService(),
                taskService);
    }

    private MonitoringController newController(MonitoringService monitoringService,
                                               BusinessOperationLogService logService,
                                               ActivityStatusTaskService taskService) {
        return new MonitoringController(
                monitoringService,
                developerMonitorService(),
                logService,
                taskService);
    }
}
