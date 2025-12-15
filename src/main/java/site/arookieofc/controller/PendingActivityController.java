package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.PendingActivityService;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.PendingActivityDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing pending activities awaiting admin approval
 */
@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/api/pending-activities")
public class PendingActivityController {
    private final PendingActivityService pendingActivityService;

    /**
     * List all pending activities (admin only)
     */
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
    public Result approve(@PathVariable("id") String id) {
        try {
            String activityId = pendingActivityService.approvePendingActivity(id);
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
    public Result reject(@PathVariable("id") String id,
                        @RequestParam(value = "reason", required = false) String reason) {
        try {
            pendingActivityService.rejectPendingActivity(id, reason);
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

