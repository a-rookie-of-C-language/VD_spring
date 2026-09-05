package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import site.arookieofc.common.audit.BusinessOperation;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.*;
import site.arookieofc.security.AuthorizationGuards;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BusinessOperationLogService;
import site.arookieofc.service.monitor.DeveloperMonitorService;
import site.arookieofc.service.MonitoringService;
import site.arookieofc.service.messaging.ActivityStatusTaskService;
import site.arookieofc.util.PaginationUtils;

@RestController
@RequestMapping({"/monitoring", "/api/monitoring"})
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {
    private static final int DEFAULT_LOG_SIZE = 50;
    private static final int MIN_LOG_SIZE = 1;
    private static final int MAX_LOG_SIZE = 200;
    private static final int DEFAULT_REPLAY_LIMIT = 100;
    private static final int MIN_REPLAY_LIMIT = 1;
    private static final int MAX_REPLAY_LIMIT = 500;

    private final MonitoringService monitoringService;
    private final DeveloperMonitorService developerMonitorService;
    private final BusinessOperationLogService businessOperationLogService;
    private final ActivityStatusTaskService activityStatusTaskService;

    @GetMapping("/dashboard")
    public Result getDashboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "timeRange", required = false, defaultValue = "monthly") String timeRange) {

        if (!isValidTimeRange(timeRange)) {
            throw BusinessException.badRequest("Invalid timeRange parameter. Valid values: daily, weekly, monthly, yearly");
        }

        MonitoringDashboardVO data = monitoringService.getDashboardData(timeRange);
        return Result.success(data);
    }

    private boolean isValidTimeRange(String timeRange) {
        return "daily".equals(timeRange)
                || "weekly".equals(timeRange)
                || "monthly".equals(timeRange)
                || "yearly".equals(timeRange);
    }

    @GetMapping("/filters")
    public Result getFilters(@AuthenticationPrincipal UserPrincipal principal) {
        MonitoringFiltersVO filters = monitoringService.getFilters();
        return Result.success(filters);
    }

    @GetMapping("/overview")
    public Result getOverview(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "college", required = false) String college,
            @RequestParam(value = "grade", required = false) String grade,
            @RequestParam(value = "clazz", required = false) String clazz) {

        MonitoringOverviewVO overview = monitoringService.getOverview(college, grade, clazz);
        return Result.success(overview);
    }

    @PostMapping("/user-stats")
    public Result getUserStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) UserStatsRequestVO request) {

        if (request == null) {
            request = UserStatsRequestVO.builder().build();
        }

        int page = PaginationUtils.normalizePage(request.getPage());
        int pageSize = PaginationUtils.normalizePageSize(request.getPageSize());
        String college = request.getCollege();
        String grade = request.getGrade();
        String clazz = request.getClazz();
        String sortField = request.getSortField();
        String sortOrder = request.getSortOrder() != null ? request.getSortOrder() : "desc";

        UserStatPageVO userStats = monitoringService.getUserStats(
                college, grade, clazz, sortField, sortOrder, page, pageSize);
        return Result.success(userStats);
    }

    @GetMapping("/logs")
    public Result getLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "size", required = false, defaultValue = "50") Integer size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        List<MonitoringLogVO> logs = monitoringService.getRecentLogs(normalizeLogSize(size), keyword);
        return Result.success(logs);
    }

    @GetMapping("/developer-metrics")
    public Result getDeveloperMetrics(@AuthenticationPrincipal UserPrincipal principal) {
        DeveloperMetricsVO metrics = developerMonitorService.latestOrSnapshot();
        return Result.success(metrics);
    }

    @GetMapping("/developer-metrics/sse")
    public SseEmitter streamDeveloperMetrics(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            return developerMonitorService.openSseStream();
        } catch (IllegalStateException e) {
            throw BusinessException.conflict("SSE_CONNECTION_LIMIT_REACHED");
        }
    }

    @GetMapping("/business-logs")
    public Result getBusinessLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "size", required = false, defaultValue = "50") Integer size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        List<BusinessOperationLogVO> logs = businessOperationLogService.queryRecent(normalizeLogSize(size), keyword);
        return Result.success(logs);
    }

    @GetMapping("/mq-task-stats")
    public Result getMqTaskStats(@AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Long> stats = activityStatusTaskService.getTaskStatusStats();
        long pending = stats.getOrDefault("PENDING", 0L);
        long sent = stats.getOrDefault("SENT", 0L);
        long failed = stats.getOrDefault("FAILED", 0L);
        long dead = stats.getOrDefault("DEAD", 0L);
        long done = stats.getOrDefault("DONE", 0L);
        ActivityStatusTaskMetricsVO vo = ActivityStatusTaskMetricsVO.builder()
                .pending(pending)
                .sent(sent)
                .failed(failed)
                .dead(dead)
                .done(done)
                .total(pending + sent + failed + dead + done)
                .build();
        return Result.success(vo);
    }

    @PostMapping("/mq-task-replay-dead")
    @BusinessOperation(
            action = "MQ_REPLAY_DEAD_TASKS",
            targetType = "MQ_ACTIVITY_STATUS_TASK",
            targetIdParam = "limit",
            targetNameParam = "limit",
            detail = "manual replay of dead mq activity status tasks"
    )
    public Result replayDeadTasks(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        ensureSuperAdmin(principal);
        int normalizedLimit = normalizeReplayLimit(limit);
        int replayed = activityStatusTaskService.replayDeadTasks(normalizedLimit);
        Map<String, Object> data = new HashMap<>();
        data.put("replayed", replayed);
        data.put("limit", normalizedLimit);
        return Result.success(data);
    }

    private int normalizeReplayLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_REPLAY_LIMIT;
        }
        return Math.max(MIN_REPLAY_LIMIT, Math.min(limit, MAX_REPLAY_LIMIT));
    }

    private int normalizeLogSize(Integer size) {
        if (size == null) {
            return DEFAULT_LOG_SIZE;
        }
        return Math.max(MIN_LOG_SIZE, Math.min(size, MAX_LOG_SIZE));
    }

    private void ensureSuperAdmin(UserPrincipal principal) {
        AuthorizationGuards.requireSuperAdmin(principal, SecurityContextHolder.getContext().getAuthentication());
    }
}
