package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import site.arookieofc.controller.VO.*;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.MonitoringService;

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

        if (!isValidTimeRange(timeRange)) {
            return Result.of(400, "Invalid timeRange parameter. Valid values: daily, weekly, monthly, yearly", null);
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

        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        String college = request.getCollege();
        String grade = request.getGrade();
        String clazz = request.getClazz();
        String sortField = request.getSortField();
        String sortOrder = request.getSortOrder() != null ? request.getSortOrder() : "desc";

        UserStatPageVO userStats = monitoringService.getUserStats(
                college, grade, clazz, sortField, sortOrder, page, pageSize);
        return Result.success(userStats);
    }
}
