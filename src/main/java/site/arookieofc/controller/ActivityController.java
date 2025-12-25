package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.controller.VO.*;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.ActivityService;
import site.arookieofc.service.UserService;
import site.arookieofc.service.FileUploadService;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.dto.ActivityImportDTO;
import site.arookieofc.service.dto.UserDTO;
import org.apache.commons.io.FilenameUtils;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/activities")
public class ActivityController {
    private final ActivityService activityService;
    private final UserService userService;
    private final site.arookieofc.service.PendingActivityService pendingActivityService;
    private final FileUploadService fileUploadService;

    /**
     * 新的查询接口 - 使用 POST + RequestBody
     * 推荐使用这个接口替代旧的 GET 接口,参数更清晰
     */
    @PostMapping("/query")
    public Result queryActivities(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestBody(required = false) ActivityQueryVO queryVO) {
        if (queryVO == null) {
            queryVO = ActivityQueryVO.builder().build();
        }

        int page = queryVO.getPage() != null ? queryVO.getPage() : 1;
        int pageSize = queryVO.getPageSize() != null ? queryVO.getPageSize() : 10;
        ActivityType type = queryVO.getType();
        ActivityStatus status = queryVO.getStatus();
        String functionary = queryVO.getFunctionary();
        String name = queryVO.getName();
        String startFrom = queryVO.getStartFrom();
        String startTo = queryVO.getStartTo();
        Boolean isFull = queryVO.getIsFull();

        OffsetDateTime sf = startFrom == null || startFrom.isEmpty() ? null : OffsetDateTime.parse(startFrom);
        OffsetDateTime st = startTo == null || startTo.isEmpty() ? null : OffsetDateTime.parse(startTo);

        String role = principal != null ? principal.getRole() : null;
        String studentNo = principal != null ? principal.getStudentNo() : null;
        boolean useAll = status != null || (("admin".equals(role) || "superAdmin".equals(role)))
                || ("functionary".equals(role) && functionary != null && functionary.equals(studentNo));

        int total = useAll ? activityService.countActivitiesAll(type, status, functionary, name, sf, st, isFull)
                : activityService.countActivities(type, status, functionary, name, sf, st, isFull);
        List<ActivityDTO> dtos = useAll
                ? activityService.listActivitiesPagedAll(type, status, functionary, name, sf, st, isFull, page, pageSize)
                : activityService.listActivitiesPaged(type, status, functionary, name, sf, st, isFull, page, pageSize);

        List<ActivityVO> items = dtos.stream()
                .map(ActivityVO::fromDTO)
                .collect(java.util.stream.Collectors.toList());

        ActivityPageVO data = ActivityPageVO.builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();

        return Result.success(data);
    }

    /**
     * 旧的查询接口 - 保持向后兼容
     * @deprecated 推荐使用 POST /api/activities/query
     */
    @Deprecated
    public Result list(@AuthenticationPrincipal UserPrincipal principal,
                       @RequestBody(required = false) ActivityListRequestVO request) {
        // 如果request为null，使用默认值
        if (request == null) {
            request = ActivityListRequestVO.builder().build();
        }

        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        ActivityType type = request.getType();
        ActivityStatus status = request.getStatus();
        String functionary = request.getFunctionary();
        String name = request.getName();
        String startFrom = request.getStartFrom();
        String startTo = request.getStartTo();
        Boolean isFull = request.getIsFull();

        OffsetDateTime sf = startFrom == null || startFrom.isEmpty() ? null : OffsetDateTime.parse(startFrom);
        OffsetDateTime st = startTo == null || startTo.isEmpty() ? null : OffsetDateTime.parse(startTo);

        String role = principal != null ? principal.getRole() : null;
        String studentNo = principal != null ? principal.getStudentNo() : null;
        boolean useAll = status != null || (("admin".equals(role) || "superAdmin".equals(role))) || ("functionary".equals(role) && functionary != null && functionary.equals(studentNo));
        int total = useAll ? activityService.countActivitiesAll(type, status, functionary, name, sf, st, isFull)
                : activityService.countActivities(type, status, functionary, name, sf, st, isFull);
        List<ActivityDTO> dtos = useAll ? activityService.listActivitiesPagedAll(type, status, functionary, name, sf, st, isFull, page, pageSize)
                : activityService.listActivitiesPaged(type, status, functionary, name, sf, st, isFull, page, pageSize);
        List<ActivityVO> items = dtos.stream().map(ActivityVO::fromDTO).collect(java.util.stream.Collectors.toList());
        ActivityPageVO data = ActivityPageVO.builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();
        return Result.success(data);
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public Result create(@ModelAttribute ActivityDTO dto) {
        ActivityDTO created = activityService.createActivity(dto);
        return Result.success(ActivityVO.fromDTO(created));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public Result update(@PathVariable("id") String id,
                         @AuthenticationPrincipal UserPrincipal principal,
                         @ModelAttribute ActivityDTO dto) {
        try {
            dto.setFunctionary(principal.getStudentNo());
            ActivityDTO updated = activityService.updateActivity(id, dto);
            return Result.success(ActivityVO.fromDTO(updated));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("NOT_FOUND".equals(msg)) {
                return Result.of(404, "NOT_FOUND", null);
            }
            if ("REVIEW_PASSED".equals(msg)) {
                return Result.of(400, "REVIEW_PASSED", null);
            }
            return Result.error(msg);
        }
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable("id") String id) {
        try {
            ActivityDTO dto = activityService.getActivityById(id);
            return Result.success(ActivityVO.fromDTO(dto));
        } catch (IllegalArgumentException e) {
            if ("NOT_FOUND".equals(e.getMessage())) {
                return Result.of(404, "NOT_FOUND", null);
            }
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") String id) {
        activityService.deleteActivity(id);
        return Result.success();
    }

    @PostMapping("/refreshStatuses")
    public Result refreshStatuses() {
        int updated = activityService.refreshStatusesAndUpdate();
        java.util.Map<String,Object> data = new java.util.HashMap<>();
        data.put("updated", updated);
        return Result.success(data);
    }

    @PostMapping("/{id}/enroll")
    public Result enroll(@PathVariable("id") String id,
                         @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }
        String studentNo = principal.getStudentNo();
        String code = activityService.enroll(id, studentNo);
        if ("OK".equals(code)) {
            return Result.success();
        }
        if ("NOT_FOUND".equals(code)) {
            return Result.of(404, "NOT_FOUND", null);
        }
        if ("CAPACITY_FULL".equals(code)) {
            return Result.of(409, "CAPACITY_FULL", null);
        }
        if ("ALREADY_ENROLLED".equals(code)) {
            return Result.of(409, "ALREADY_ENROLLED", null);
        }
        return Result.error("UNKNOWN_ERROR");
    }

    @PostMapping("/{id}/unenroll")
    public Result unenroll(@PathVariable("id") String id,
                           @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }
        String studentNo = principal.getStudentNo();
        String code = activityService.unenroll(id, studentNo);
        if ("OK".equals(code)) {
            return Result.success();
        }
        if ("NOT_FOUND".equals(code)) {
            return Result.of(404, "NOT_FOUND", null);
        }
        if ("FUNCTIONARY_CANNOT_UNENROLL".equals(code)) {
            return Result.of(403, "FUNCTIONARY_CANNOT_UNENROLL", null);
        }
        if ("ENROLLMENT_ENDED".equals(code)) {
            return Result.of(400, "ENROLLMENT_ENDED", null);
        }
        if ("NOT_ENROLLED".equals(code)) {
            return Result.of(409, "NOT_ENROLLED", null);
        }
        return Result.error("UNKNOWN_ERROR");
    }

    @PostMapping("/{id}/review")
    public Result review(@PathVariable("id") String id,
                         @RequestParam("approve") boolean approve,
                         @RequestParam(value = "reason", required = false) String reason) {
        try {
            ActivityDTO dto = activityService.reviewActivity(id, approve, reason);
            return Result.success(ActivityVO.fromDTO(dto));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("NOT_FOUND".equals(msg)) {
                return Result.of(404, "NOT_FOUND", null);
            }
            if ("INVALID_TIME".equals(msg) || "ENROLLMENT_PASSED".equals(msg)) {
                return Result.of(400, msg, null);
            }
            return Result.error(msg);
        }
    }

    @GetMapping("/MyActivities")
    public Result getMyActivities(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                  @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        // Authentication handled by Spring Security
        String studentNo = principal.getStudentNo();
        int total = activityService.countActivitiesAll(null, null, studentNo, null, null, null, null);
        List<ActivityDTO> dtos = activityService
                .listActivitiesPagedAll(null, null, studentNo,
                        null, null, null, null, page, pageSize);
        List<ActivityVO> items = dtos
                .stream()
                .map(ActivityVO::fromDTO)
                .collect(Collectors.toList());
        ActivityPageVO data = ActivityPageVO.builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();
        return Result.success(data);
    }

    @PostMapping("/import")
    public Result importActivity(@AuthenticationPrincipal UserPrincipal principal,
                                 @ModelAttribute ActivityImportDTO dto) {
        if (principal == null) {
            return Result.of(401, "UNAUTHORIZED", null);
        }

        String role = principal.getRole();
        boolean isAdmin = "admin".equals(role) || "superAdmin".equals(role);

        try {
            String activityId = pendingActivityService
                    .importActivity(dto, principal.getStudentNo(), isAdmin);
            Map<String, Object> result = new HashMap<>();
            result.put("id", activityId);
            result.put("status", isAdmin ? "APPROVED" : "PENDING_REVIEW");
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/MyStatus")
    public Result getMyStatus(@AuthenticationPrincipal UserPrincipal principal) {
        // Authentication handled by Spring Security
        String studentNo = principal.getStudentNo();

        List<ActivityDTO> participatedActivities = activityService.getActivitiesByStudentNo(studentNo);

        double totalDuration = userService.getUserByStudentNo(studentNo)
                .map(UserDTO::getTotalHours)
                .orElse(0.0);

        // Count total activities
        int totalActivities = participatedActivities.size();

        // Convert to VO
        List<ActivityVO> activityList = participatedActivities.stream()
                .map(ActivityVO::fromDTO)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("totalDuration", totalDuration);
        data.put("totalActivities", totalActivities);
        data.put("activities", activityList);

        return Result.success(data);
    }

    /**
     * 上传附件
     * 支持文档、图片、压缩包等多种格式
     */
    @PostMapping("/upload/attachment")
    public Result uploadAttachment(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String description) {
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            // 上传文件
            String filePath = fileUploadService.uploadAttachment(file);

            // 获取文件信息
            String originalFilename = file.getOriginalFilename();
            String fileType = FilenameUtils.getExtension(originalFilename != null ? originalFilename : "");

            // 构造返回值
            AttachmentVO attachmentVO = AttachmentVO.builder()
                    .fileName(originalFilename)
                    .filePath(filePath)
                    .fileType(fileType)
                    .fileSize(file.getSize())
                    .description(description)
                    .build();

            return Result.success(attachmentVO);
        } catch (IllegalArgumentException e) {
            log.error("文件验证失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除附件
     */
    @DeleteMapping("/attachment")
    public Result deleteAttachment(@RequestParam("filePath") String filePath) {
        try {
            boolean deleted = fileUploadService.deleteAttachment(filePath);
            if (deleted) {
                return Result.success("附件删除成功");
            } else {
                return Result.error("附件删除失败或文件不存在");
            }
        } catch (Exception e) {
            log.error("删除附件失败", e);
            return Result.error("删除附件失败: " + e.getMessage());
        }
    }

    /**
     * 获取附件信息
     */
    @GetMapping("/attachment/info")
    public Result getAttachmentInfo(@RequestParam("filePath") String filePath) {
        try {
            Map<String, Object> info = fileUploadService.getFileInfo(filePath);
            if (info != null) {
                return Result.success(info);
            } else {
                return Result.of(404, "附件不存在", null);
            }
        } catch (Exception e) {
            log.error("获取附件信息失败", e);
            return Result.error("获取附件信息失败: " + e.getMessage());
        }
    }
}
