package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.PendingActivityQueryVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BatchImportService;
import site.arookieofc.service.PendingActivityService;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.BatchImportResultDTO;
import site.arookieofc.service.dto.PendingActivityDTO;

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
        PendingActivityDTO dto = pendingActivityService.getPendingActivityById(id);
        return Result.success(dto);
    }

    /**
     * Approve pending activity (admin only)
     */
    @PostMapping("/{id}/approve")
    public Result approve(@AuthenticationPrincipal UserPrincipal principal,
                         @PathVariable("id") String id) {
        String activityId = pendingActivityService.approvePendingActivity(id, principal.getStudentNo());
        Map<String, Object> result = new HashMap<>();
        result.put("activityId", activityId);
        return Result.success(result);
    }

    /**
     * Reject pending activity (admin only)
     */
    @PostMapping("/{id}/reject")
    public Result reject(@AuthenticationPrincipal UserPrincipal principal,
                        @PathVariable("id") String id,
                        @RequestParam(value = "reason", required = false) String reason) {
        pendingActivityService.rejectPendingActivity(id, reason, principal.getStudentNo());
        return Result.success();
    }

    /**
     * Delete pending activity (submitter or admin)
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") String id) {
        pendingActivityService.deletePendingActivity(id);
        return Result.success();
    }

    // ==================== 批量导入相关接口 ====================

    /**
     * 批量导入Excel数据
     * Excel格式：姓名、性别、学院、年级、学号、联系方式、服务时长（小时）、活动名称
     * 功能：
     * - 管理员/负责人直接导入：立即生效
     * - 普通用户提交审核：需要管理员审核通过后才生效
     * - 对于学号存在的用户直接添加到活动中
     * - 学号不存在的用户则创建用户
     * - 活动名称去重（去除后面的小时数标记，如"xxx(2小时)"视为"xxx"）
     * - 如果活动存在就加入，不存在则创建（标记imported=true，状态为结束）
     */
    @PostMapping("/batch-import")
    public Result batchImport(@AuthenticationPrincipal UserPrincipal principal,
                              @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请上传Excel文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null ||
            (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            return Result.error("请上传.xlsx或.xls格式的Excel文件");
        }

        String role = principal.getRole();
        boolean isAdmin = "admin".equals(role) || "superAdmin".equals(role);

        BatchImportResultDTO result = batchImportService.batchImport(file, principal.getStudentNo(), isAdmin);

        Map<String, Object> data = new HashMap<>();
        data.put("batchId", result.getBatchId());
        data.put("totalRecords", result.getTotalRecords());
        data.put("newUsersCreated", result.getNewUsersCreated());
        data.put("newActivitiesCreated", result.getNewActivitiesCreated());
        data.put("participantsAdded", result.getParticipantsAdded());
        data.put("hoursGranted", result.getHoursGranted());
        data.put("createdUserStudentNos", result.getCreatedUserStudentNos());
        data.put("createdActivityNames", result.getCreatedActivityNames());
        data.put("errors", result.getErrors());
        data.put("status", isAdmin ? "APPROVED" : "PENDING_REVIEW");

        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            return Result.of(200, isAdmin ? "部分数据导入成功，存在错误" : "已提交审核，部分数据存在错误", data);
        }

        return Result.success(data);
    }

    /**
     * 获取待审核批量导入列表（分页）
     */
    @GetMapping("/batch-import")
    public Result listPendingBatchImports(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String submittedBy,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        String role = principal.getRole();
        boolean isAdmin = "admin".equals(role) || "superAdmin".equals(role);

        // 非管理员只能查看自己提交的
        if (!isAdmin) {
            submittedBy = principal.getStudentNo();
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

    /**
     * 获取待审核批量导入详情
     */
    @GetMapping("/batch-import/{batchId}")
    public Result getPendingBatchImport(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable String batchId) {
        var dto = batchImportService.getPendingBatchImport(batchId);

        String role = principal.getRole();
        boolean isAdmin = "admin".equals(role) || "superAdmin".equals(role);

        // 非管理员只能查看自己提交的
        if (!isAdmin && !principal.getStudentNo().equals(dto.getSubmittedBy())) {
            throw BusinessException.forbidden("FORBIDDEN");
        }

        return Result.success(dto);
    }

    /**
     * 审核通过批量导入
     */
    @PostMapping("/batch-import/{batchId}/approve")
    public Result approveBatchImport(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable String batchId) {
        String role = principal.getRole();
        if (!"admin".equals(role) && !"superAdmin".equals(role)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }

        BatchImportResultDTO result = batchImportService.approveBatchImport(batchId, principal.getStudentNo());

        Map<String, Object> data = new HashMap<>();
        data.put("totalRecords", result.getTotalRecords());
        data.put("newUsersCreated", result.getNewUsersCreated());
        data.put("newActivitiesCreated", result.getNewActivitiesCreated());
        data.put("participantsAdded", result.getParticipantsAdded());
        data.put("hoursGranted", result.getHoursGranted());
        data.put("createdUserStudentNos", result.getCreatedUserStudentNos());
        data.put("createdActivityNames", result.getCreatedActivityNames());
        data.put("errors", result.getErrors());

        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            return Result.of(200, "审核通过，部分数据处理存在错误", data);
        }

        return Result.success(data);
    }

    /**
     * 拒绝批量导入
     */
    @PostMapping("/batch-import/{batchId}/reject")
    public Result rejectBatchImport(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable String batchId,
                                    @RequestParam(required = false) String reason) {
        String role = principal.getRole();
        if (!"admin".equals(role) && !"superAdmin".equals(role)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }

        batchImportService.rejectBatchImport(batchId, reason, principal.getStudentNo());
        return Result.success("已拒绝");
    }

    /**
     * 删除待审核批量导入
     */
    @DeleteMapping("/batch-import/{batchId}")
    public Result deletePendingBatchImport(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable String batchId) {
        var dto = batchImportService.getPendingBatchImport(batchId);

        String role = principal.getRole();
        boolean isAdmin = "admin".equals(role) || "superAdmin".equals(role);

        // 非管理员只能删除自己提交的且状态为PENDING的
        if (!isAdmin) {
            if (!principal.getStudentNo().equals(dto.getSubmittedBy())) {
                throw BusinessException.forbidden("FORBIDDEN");
            }
            if (!"PENDING".equals(dto.getStatus())) {
                throw BusinessException.badRequest("CANNOT_DELETE_NON_PENDING");
            }
        }

        batchImportService.deletePendingBatchImport(batchId);
        return Result.success("已删除");
    }
}
