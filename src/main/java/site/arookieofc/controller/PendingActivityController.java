package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import site.arookieofc.controller.VO.PendingActivityQueryVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.PendingActivityService;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.PendingActivityDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/api/pending-activities")
public class PendingActivityController {
    private final PendingActivityService pendingActivityService;

    /**
     * 新的查询接口 - 使用 POST + RequestBody
     * 推荐使用这个接口替代旧的 GET 接口
     */
    @PostMapping("/query")
    public Result queryPendingActivities(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody(required = false) PendingActivityQueryVO queryVO) {
        if (queryVO == null) {
            queryVO = PendingActivityQueryVO.builder().build();
        }

        int page = queryVO.getPage() != null ? queryVO.getPage() : 1;
        int pageSize = queryVO.getPageSize() != null ? queryVO.getPageSize() : 10;
        ActivityType type = queryVO.getType();
        String functionary = queryVO.getFunctionary();
        String name = queryVO.getName();
        String submittedBy = queryVO.getSubmittedBy();

        String role = principal != null ? principal.getRole() : null;
        String studentNo = principal != null ? principal.getStudentNo() : null;

        // For functionary, only show their own submitted pending activities
        if ("functionary".equals(role) && submittedBy == null) {
            submittedBy = studentNo;
        }

        int total = pendingActivityService.countPendingActivities(type, functionary, name, submittedBy);
        List<PendingActivityDTO> dtos = pendingActivityService.listPendingActivitiesPaged(
                type, functionary, name, submittedBy, page, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("items", dtos);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);

        return Result.success(data);
    }

    /**
     * 旧的查询接口 - 保持向后兼容
     * @deprecated 推荐使用 POST /api/pending-activities/query
     */
    @Deprecated
    @GetMapping
    public Result list(@AuthenticationPrincipal UserPrincipal principal,
                       @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                       @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
                       @RequestParam(value = "type", required = false) ActivityType type,
                       @RequestParam(value = "functionary", required = false) String functionary,
                       @RequestParam(value = "name", required = false) String name,
                       @RequestParam(value = "submittedBy", required = false) String submittedBy) {

        String role = principal != null ? principal.getRole() : null;
        String studentNo = principal != null ? principal.getStudentNo() : null;

        // For functionary, only show their own submitted pending activities
        if ("functionary".equals(role) && submittedBy == null) {
            submittedBy = studentNo;
        }

        int total = pendingActivityService.countPendingActivities(type, functionary, name, submittedBy);
        List<PendingActivityDTO> dtos = pendingActivityService.listPendingActivitiesPaged(
                type, functionary, name, submittedBy, page, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("items", dtos);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);

        return Result.success(data);
    }

    /**
     * Get pending activity by ID
     */
    @GetMapping("/{id}")
    public Result getById(@PathVariable("id") String id) {
        try {
            PendingActivityDTO dto = pendingActivityService.getPendingActivityById(id);
            return Result.success(dto);
        } catch (IllegalArgumentException e) {
            if ("NOT_FOUND".equals(e.getMessage())) {
                return Result.of(404, "NOT_FOUND", null);
            }
            return Result.error(e.getMessage());
        }
    }

    /**
     * Approve pending activity (admin only)
     */
    @PostMapping("/{id}/approve")
    public Result approve(@AuthenticationPrincipal UserPrincipal principal,
                         @PathVariable("id") String id) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        try {
            String activityId = pendingActivityService.approvePendingActivity(id, principal.getStudentNo());
            Map<String, Object> result = new HashMap<>();
            result.put("activityId", activityId);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            if ("NOT_FOUND".equals(e.getMessage())) {
                return Result.of(404, "NOT_FOUND", null);
            }
            return Result.error(e.getMessage());
        }
    }

    /**
     * Reject pending activity (admin only)
     */
    @PostMapping("/{id}/reject")
    public Result reject(@AuthenticationPrincipal UserPrincipal principal,
                        @PathVariable("id") String id,
                        @RequestParam(value = "reason", required = false) String reason) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        try {
            pendingActivityService.rejectPendingActivity(id, reason, principal.getStudentNo());
            return Result.success();
        } catch (IllegalArgumentException e) {
            if ("NOT_FOUND".equals(e.getMessage())) {
                return Result.of(404, "NOT_FOUND", null);
            }
            return Result.error(e.getMessage());
        }
    }

    /**
     * Delete pending activity (submitter or admin)
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") String id) {
        try {
            pendingActivityService.deletePendingActivity(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}

