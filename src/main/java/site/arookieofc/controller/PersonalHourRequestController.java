package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.audit.BusinessOperation;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.AuthorizationGuards;
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
@RequestMapping("/activities")
public class PersonalHourRequestController {
    private final PersonalHourRequestService requestService;

    @PostMapping(value = "/request_hours", consumes = {"multipart/form-data"})
    @BusinessOperation(action = "提交时长申请", targetType = "hour-request", detail = "用户提交个人时长申请")
    public Result submitRequest(@AuthenticationPrincipal UserPrincipal principal,
                                @RequestParam("name") String name,
                                @RequestParam("functionary") String functionary,
                                @RequestParam("type") ActivityType type,
                                @RequestParam(value = "description", required = false) String description,
                                @RequestParam("startTime") String startTime,
                                @RequestParam("endTime") String endTime,
                                @RequestParam("duration") Double duration,
                                @RequestParam(value = "files", required = false) List<MultipartFile> files) {
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
    }

    @GetMapping("/pending_requests")
    public Result getPendingRequests(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                     @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        AuthorizationGuards.requireAdmin(principal);

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
    @BusinessOperation(action = "审核时长申请", targetType = "hour-request", targetIdParam = "id", detail = "管理员审核个人时长申请")
    public Result reviewRequest(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable("id") String id,
                                @RequestParam("approved") boolean approved,
                                @RequestParam(value = "reason", required = false) String reason) {
        AuthorizationGuards.requireAdmin(principal);

        if (!approved && (reason == null || reason.trim().isEmpty())) {
            throw BusinessException.badRequest("REASON_REQUIRED");
        }

        PersonalHourRequestDTO reviewed = requestService.reviewRequest(
                id, approved, reason, principal.getStudentNo());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", reviewed);
        return Result.success(result);
    }

    @GetMapping("/my_requests")
    public Result getMyRequests(@AuthenticationPrincipal UserPrincipal principal,
                                @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
                                @RequestParam(value = "status", required = false) ActivityStatus status) {
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
        PersonalHourRequestDTO dto = requestService.getRequestById(id);
        AuthorizationGuards.requireSelfOrAdmin(principal, dto.getApplicantStudentNo());
        return Result.success(dto);
    }

    @DeleteMapping("/request/{id}")
    public Result deleteRequest(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable("id") String id) {
        requestService.deleteRequest(id, principal.getStudentNo());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "申请已撤销");
        return Result.success(result);
    }
}
