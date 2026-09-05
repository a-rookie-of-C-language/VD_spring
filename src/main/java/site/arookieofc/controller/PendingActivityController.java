package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.audit.BusinessOperation;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.PendingActivityQueryVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.AuthorizationGuards;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.BatchImportService;
import site.arookieofc.service.PendingActivityService;
import site.arookieofc.service.dto.BatchImportResultDTO;
import site.arookieofc.service.dto.PendingActivityDTO;
import site.arookieofc.util.PaginationUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/pending-activities")
public class PendingActivityController {
    private final PendingActivityService pendingActivityService;
    private final BatchImportService batchImportService;

    @PostMapping("/query")
    public Result queryPendingActivities(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody(required = false) PendingActivityQueryVO queryVO) {
        if (queryVO == null) {
            queryVO = PendingActivityQueryVO.builder().build();
        }

        int page = PaginationUtils.normalizePage(queryVO.getPage());
        int pageSize = PaginationUtils.normalizePageSize(queryVO.getPageSize());
        ActivityType type = queryVO.getType();
        String functionary = queryVO.getFunctionary();
        String name = queryVO.getName();
        String submittedBy = queryVO.getSubmittedBy();
        String studentNo = AuthorizationGuards.requireStudentNo(principal);

        if (!AuthorizationGuards.isAdmin(principal)) {
            submittedBy = studentNo;
        }

        int total = pendingActivityService.countPendingActivities(type, functionary, name, submittedBy);
        List<PendingActivityDTO> items = pendingActivityService.listPendingActivitiesPaged(
                type, functionary, name, submittedBy, page, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return Result.success(data);
    }

    @Deprecated
    @GetMapping
    public Result list(@AuthenticationPrincipal UserPrincipal principal,
                       @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                       @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
                       @RequestParam(value = "type", required = false) ActivityType type,
                       @RequestParam(value = "functionary", required = false) String functionary,
                       @RequestParam(value = "name", required = false) String name,
                       @RequestParam(value = "submittedBy", required = false) String submittedBy) {
        page = PaginationUtils.normalizePage(page);
        pageSize = PaginationUtils.normalizePageSize(pageSize);
        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        if (!AuthorizationGuards.isAdmin(principal)) {
            submittedBy = studentNo;
        }
        int total = pendingActivityService.countPendingActivities(type, functionary, name, submittedBy);
        List<PendingActivityDTO> items = pendingActivityService.listPendingActivitiesPaged(
                type, functionary, name, submittedBy, page, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result getById(@AuthenticationPrincipal UserPrincipal principal, @PathVariable("id") String id) {
        PendingActivityDTO dto = pendingActivityService.getPendingActivityById(id);
        AuthorizationGuards.requireSelfOrAdmin(principal, dto.getSubmittedBy());
        return Result.success(dto);
    }

    @PostMapping("/{id}/approve")
    @BusinessOperation(action = "APPROVE_PENDING_ACTIVITY", targetType = "pending-activity", targetIdParam = "id", detail = "approve pending activity")
    public Result approve(@AuthenticationPrincipal UserPrincipal principal, @PathVariable("id") String id) {
        AuthorizationGuards.requireAdmin(principal);
        String activityId = pendingActivityService.approvePendingActivity(id, AuthorizationGuards.requireStudentNo(principal));
        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        return Result.success(data);
    }

    @PostMapping("/{id}/reject")
    @BusinessOperation(action = "REJECT_PENDING_ACTIVITY", targetType = "pending-activity", targetIdParam = "id", detail = "reject pending activity")
    public Result reject(@AuthenticationPrincipal UserPrincipal principal,
                         @PathVariable("id") String id,
                         @RequestParam(value = "reason", required = false) String reason) {
        AuthorizationGuards.requireAdmin(principal);
        pendingActivityService.rejectPendingActivity(id, reason, AuthorizationGuards.requireStudentNo(principal));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable("id") String id) {
        PendingActivityDTO dto = pendingActivityService.getPendingActivityById(id);
        AuthorizationGuards.requireSelfOrAdmin(principal, dto.getSubmittedBy());
        if (dto.getStatus() != null && dto.getStatus() != site.arookieofc.service.BO.ActivityStatus.UnderReview) {
            throw BusinessException.badRequest("CANNOT_DELETE_PROCESSED_PENDING_ACTIVITY");
        }
        pendingActivityService.deletePendingActivity(id);
        return Result.success();
    }

    @PostMapping("/batch-import")
    public Result batchImport(@AuthenticationPrincipal UserPrincipal principal,
                              @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Please upload an Excel file");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null
                || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            throw BusinessException.badRequest("Only .xlsx/.xls is supported");
        }

        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        BatchImportResultDTO result = batchImportService.batchImport(file, studentNo, AuthorizationGuards.isAdmin(principal));
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", result.getBatchId());
        data.put("totalRecords", result.getTotalRecords());
        data.put("validRecords", result.getValidRecords());
        data.put("invalidRecords", result.getInvalidRecords());
        data.put("newUsersCreated", result.getNewUsersCreated());
        data.put("newActivitiesCreated", result.getNewActivitiesCreated());
        data.put("participantsAdded", result.getParticipantsAdded());
        data.put("hoursGranted", result.getHoursGranted());
        data.put("createdUserStudentNos", result.getCreatedUserStudentNos());
        data.put("createdActivityNames", result.getCreatedActivityNames());
        data.put("errors", result.getErrors());
        data.put("status", "PENDING_REVIEW");

        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            return Result.of(200, "Precheck finished with errors. Please fix and resubmit.", data);
        }
        return Result.success(data);
    }

    @GetMapping("/batch-import")
    public Result listPendingBatchImports(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String submittedBy,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        page = PaginationUtils.normalizePage(page);
        pageSize = PaginationUtils.normalizePageSize(pageSize);
        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        if (!AuthorizationGuards.isAdmin(principal)) {
            submittedBy = studentNo;
        }
        var list = batchImportService.listPendingBatchImports(status, submittedBy, page, pageSize);
        int total = batchImportService.countPendingBatchImports(status, submittedBy);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return Result.success(data);
    }

    @GetMapping("/batch-import/{batchId}")
    public Result getPendingBatchImport(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String batchId) {
        var dto = batchImportService.getPendingBatchImport(batchId);
        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        if (!AuthorizationGuards.isAdmin(principal) && !studentNo.equals(dto.getSubmittedBy())) {
            throw BusinessException.forbidden("FORBIDDEN");
        }
        return Result.success(dto);
    }

    @PostMapping("/batch-import/{batchId}/approve")
    @BusinessOperation(action = "APPROVE_BATCH_IMPORT", targetType = "batch-import", targetIdParam = "batchId", detail = "approve batch import")
    public Result approveBatchImport(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String batchId) {
        AuthorizationGuards.requireAdmin(principal);
        BatchImportResultDTO result = batchImportService.approveBatchImport(batchId, AuthorizationGuards.requireStudentNo(principal));
        Map<String, Object> data = new HashMap<>();
        data.put("totalRecords", result.getTotalRecords());
        data.put("validRecords", result.getValidRecords());
        data.put("invalidRecords", result.getInvalidRecords());
        data.put("newUsersCreated", result.getNewUsersCreated());
        data.put("newActivitiesCreated", result.getNewActivitiesCreated());
        data.put("participantsAdded", result.getParticipantsAdded());
        data.put("hoursGranted", result.getHoursGranted());
        data.put("createdUserStudentNos", result.getCreatedUserStudentNos());
        data.put("createdActivityNames", result.getCreatedActivityNames());
        data.put("errors", result.getErrors());
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            return Result.of(200, "Approved with partial errors", data);
        }
        return Result.success(data);
    }

    @PostMapping("/batch-import/{batchId}/reject")
    @BusinessOperation(action = "REJECT_BATCH_IMPORT", targetType = "batch-import", targetIdParam = "batchId", detail = "reject batch import")
    public Result rejectBatchImport(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable String batchId,
                                    @RequestParam(required = false) String reason) {
        AuthorizationGuards.requireAdmin(principal);
        batchImportService.rejectBatchImport(batchId, reason, AuthorizationGuards.requireStudentNo(principal));
        return Result.success("Rejected");
    }

    @DeleteMapping("/batch-import/{batchId}")
    public Result deletePendingBatchImport(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String batchId) {
        var dto = batchImportService.getPendingBatchImport(batchId);
        boolean isAdmin = AuthorizationGuards.isAdmin(principal);
        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        if (!isAdmin) {
            if (!studentNo.equals(dto.getSubmittedBy())) {
                throw BusinessException.forbidden("FORBIDDEN");
            }
            if (!"PENDING".equals(dto.getStatus())) {
                throw BusinessException.badRequest("CANNOT_DELETE_NON_PENDING");
            }
        }
        batchImportService.deletePendingBatchImport(batchId);
        return Result.success("Deleted");
    }
}
