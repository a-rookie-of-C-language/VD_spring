package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import site.arookieofc.controller.VO.*;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.MonitoringService;

/**
 * 监控大屏Controller
 * 仅SuperAdmin可访问
 */
@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/dashboard")
    public Result getDashboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "timeRange", required = false, defaultValue = "monthly") String timeRange) {

        log.info("获取监控大屏数据, timeRange: {}, user: {}", timeRange,
                principal != null ? principal.getStudentNo() : "anonymous");

        if (!isValidTimeRange(timeRange)) {
            return Result.of(400, "Invalid timeRange parameter. Valid values: daily, weekly, monthly, yearly", null);
        }

        try {
            MonitoringDashboardVO data = monitoringService.getDashboardData(timeRange);
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取监控大屏数据失败", e);
            return Result.error("Failed to fetch dashboard data: " + e.getMessage());
        }
    }

    private boolean isValidTimeRange(String timeRange) {
        return "daily".equals(timeRange)
                || "weekly".equals(timeRange)
                || "monthly".equals(timeRange)
                || "yearly".equals(timeRange);
    }

    @GetMapping("/filters")
    public Result getFilters(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("获取监控筛选选项, user: {}",
                principal != null ? principal.getStudentNo() : "anonymous");

        try {
            MonitoringFiltersVO filters = monitoringService.getFilters();
            return Result.success(filters);
        } catch (Exception e) {
            log.error("获取筛选选项失败", e);
            return Result.error("Failed to fetch filters: " + e.getMessage());
        }
    }

    @GetMapping("/overview")
    public Result getOverview(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "college", required = false) String college,
            @RequestParam(value = "grade", required = false) String grade,
            @RequestParam(value = "clazz", required = false) String clazz) {

        log.info("获取监控概览数据, college: {}, grade: {}, clazz: {}, user: {}",
                college, grade, clazz, principal != null ? principal.getStudentNo() : "anonymous");

        try {
            MonitoringOverviewVO overview = monitoringService.getOverview(college, grade, clazz);
            return Result.success(overview);
        } catch (Exception e) {
            log.error("获取概览数据失败", e);
            return Result.error("Failed to fetch overview: " + e.getMessage());
        }
    }

    /**
     * 获取用户统计详情（分页）
     * @param request 查询和分页参数
     * @return 用户统计分页数据
     */
    @PostMapping("/user-stats")
    public Result getUserStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) UserStatsRequestVO request) {

        // 如果request为null，使用默认值
        if (request == null) {
            request = UserStatsRequestVO.builder().build();
        }

        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        String college = request.getCollege();
        String grade = request.getGrade();
        String clazz = request.getClazz();
        String sortField = request.getSortField();
        String sortOrder = request.getSortOrder() != null ? request.getSortOrder() : "desc";

        log.info("获取用户统计详情, page: {}, pageSize: {}, college: {}, grade: {}, clazz: {}, sortField: {}, sortOrder: {}, user: {}",
                page, pageSize, college, grade, clazz, sortField, sortOrder,
                principal != null ? principal.getStudentNo() : "anonymous");

        try {
            UserStatPageVO userStats = monitoringService.getUserStats(
                    college, grade, clazz, sortField, sortOrder, page, pageSize);
            return Result.success(userStats);
        } catch (Exception e) {
            log.error("获取用户统计详情失败", e);
            return Result.error("Failed to fetch user stats: " + e.getMessage());
        }
    }
}

