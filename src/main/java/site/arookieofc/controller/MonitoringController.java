package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import site.arookieofc.controller.VO.MonitoringDashboardVO;
import site.arookieofc.controller.VO.MonitoringFiltersVO;
import site.arookieofc.controller.VO.MonitoringOverviewVO;
import site.arookieofc.controller.VO.UserStatPageVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.MonitoringService;

/**
 * 监控大屏Controller
 * 仅SuperAdmin可访问
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final MonitoringService monitoringService;

    /**
     * 获取监控大屏数据
     * @param timeRange 时间范围: daily(当天), weekly(本周), monthly(本月), yearly(本年)
     * @return 监控大屏数据
     */
    @GetMapping("/dashboard")
    public Result getDashboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "timeRange", required = false, defaultValue = "monthly") String timeRange) {

        log.info("获取监控大屏数据, timeRange: {}, user: {}", timeRange,
                principal != null ? principal.getStudentNo() : "anonymous");

        // 验证timeRange参数
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

    /**
     * 验证timeRange参数
     */
    private boolean isValidTimeRange(String timeRange) {
        return "daily".equals(timeRange)
                || "weekly".equals(timeRange)
                || "monthly".equals(timeRange)
                || "yearly".equals(timeRange);
    }

    /**
     * 获取筛选选项
     * @return 学院、年级、班级列表
     */
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

    /**
     * 获取监控概览数据（支持筛选）
     * @param college 学院（可选）
     * @param grade 年级（可选）
     * @param clazz 班级（可选）
     * @return 概览统计数据
     */
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
     * @param page 页码（默认1）
     * @param pageSize 每页大小（默认10）
     * @param college 学院（可选）
     * @param grade 年级（可选）
     * @param clazz 班级（可选）
     * @param sortField 排序字段（可选：duration, activityCount）
     * @param sortOrder 排序方式（可选：asc, desc）
     * @return 用户统计分页数据
     */
    @GetMapping("/user-stats")
    public Result getUserStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
            @RequestParam(value = "college", required = false) String college,
            @RequestParam(value = "grade", required = false) String grade,
            @RequestParam(value = "clazz", required = false) String clazz,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "desc") String sortOrder) {

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

