package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.PersonalHourRequestService;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.PersonalHourRequestDTO;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/api/activities")
public class PersonalHourRequestController {
    private final PersonalHourRequestService requestService;

    @PostMapping(value = "/request_hours", consumes = {"multipart/form-data"})
    public Result submitRequest(@AuthenticationPrincipal UserPrincipal principal,
                                @RequestParam("name") String name,
                                @RequestParam("functionary") String functionary,
                                @RequestParam("type") ActivityType type,
                                @RequestParam(value = "description", required = false) String description,
                                @RequestParam("startTime") String startTime,
                                @RequestParam("endTime") String endTime,
                                @RequestParam("duration") Double duration,
                                @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        try {
            PersonalHourRequestDTO dto = PersonalHourRequestDTO.builder()
                    .name(name)
                    .functionary(functionary)
                    .type(type)
                    .description(description)
                    .startTime(OffsetDateTime.parse(startTime))
                    .endTime(OffsetDateTime.parse(endTime))
                    .duration(duration)
                    .files(files)
                    .build();

            PersonalHourRequestDTO created = requestService.submitRequest(dto, principal.getStudentNo());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "申请已提交，等待审核");
            result.put("data", created);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/pending_requests")
    public Result getPendingRequests(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                     @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        String role = principal.getRole();
        if (!"admin".equals(role) && !"superAdmin".equals(role)) {
            return Result.of(403, "FORBIDDEN", null);
        }

        int total = requestService.countUnderViewRequests();
        List<PersonalHourRequestDTO> items = requestService.listPendingRequests(page, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);

        return Result.success(data);
    }

    @PostMapping("/review_request/{id}")
    public Result reviewRequest(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable("id") String id,
                                @RequestParam("approved") boolean approved,
                                @RequestParam(value = "reason", required = false) String reason) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        String role = principal.getRole();
        if (!"admin".equals(role) && !"superAdmin".equals(role)) {
            return Result.of(403, "FORBIDDEN", null);
        }

        // Validate: if rejected, reason is required
        if (!approved && (reason == null || reason.trim().isEmpty())) {
            return Result.of(400, "REASON_REQUIRED", null);
        }

        try {
            PersonalHourRequestDTO reviewed = requestService.reviewRequest(
                    id, approved, reason, principal.getStudentNo());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", reviewed);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("NOT_FOUND".equals(msg)) {
                return Result.of(404, "NOT_FOUND", null);
            }
            if ("ALREADY_REVIEWED".equals(msg)) {
                return Result.of(400, "ALREADY_REVIEWED", null);
            }
            return Result.error(msg);
        }
    }

    @GetMapping("/my_requests")
    public Result getMyRequests(@AuthenticationPrincipal UserPrincipal principal,
                                @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
                                @RequestParam(value = "status", required = false) ActivityStatus status) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        String studentNo = principal.getStudentNo();
        int total = requestService.countRequests(studentNo, null, status, null);
        List<PersonalHourRequestDTO> items = requestService.listRequestsPaged(
                studentNo, null, status, null, page, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);

        return Result.success(data);
    }

    @GetMapping("/request/{id}")
    public Result getRequestById(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable("id") String id) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        try {
            PersonalHourRequestDTO dto = requestService.getRequestById(id);

            // Check permission: only the applicant or admin can view
            String role = principal.getRole();
            if (!dto.getApplicantStudentNo().equals(principal.getStudentNo())
                    && !"admin".equals(role) && !"superAdmin".equals(role)) {
                return Result.of(403, "FORBIDDEN", null);
            }

            return Result.success(dto);
        } catch (IllegalArgumentException e) {
            if ("NOT_FOUND".equals(e.getMessage())) {
                return Result.of(404, "NOT_FOUND", null);
            }
            return Result.error(e.getMessage());
        }
    }

    /**
     * Delete a pending request (only by applicant)
     * URL: /api/activities/request/:id
     * Method: DELETE
     */
    @DeleteMapping("/request/{id}")
    public Result deleteRequest(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable("id") String id) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        try {
            requestService.deleteRequest(id, principal.getStudentNo());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "申请已撤销");
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("NOT_FOUND".equals(msg)) {
                return Result.of(404, "NOT_FOUND", null);
            }
            if ("FORBIDDEN".equals(msg)) {
                return Result.of(403, "FORBIDDEN", null);
            }
            if ("CANNOT_DELETE_REVIEWED".equals(msg)) {
                return Result.of(400, "CANNOT_DELETE_REVIEWED", null);
            }
            return Result.error(msg);
        }
    }
}

