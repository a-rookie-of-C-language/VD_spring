package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.audit.BusinessOperation;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.*;
import site.arookieofc.security.AuthorizationGuards;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.ActivityService;
import site.arookieofc.service.BatchImportService;
import site.arookieofc.service.FileUploadService;
import site.arookieofc.service.MyActivityService;
import site.arookieofc.service.UserService;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.dto.ActivityImportDTO;
import site.arookieofc.util.PaginationUtils;

import org.apache.commons.io.FilenameUtils;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/activities")
public class ActivityController {
    private final ActivityService activityService;
    private final UserService userService;
    private final site.arookieofc.service.PendingActivityService pendingActivityService;
    private final FileUploadService fileUploadService;
    private final BatchImportService batchImportService;
    private final MyActivityService myActivityService;

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

        int page = PaginationUtils.normalizePage(queryVO.getPage());
        int pageSize = PaginationUtils.normalizePageSize(queryVO.getPageSize());
        ActivityType type = queryVO.getType();
        ActivityStatus status = queryVO.getStatus();
        String functionary = queryVO.getFunctionary();
        String name = queryVO.getName();
        String startFrom = queryVO.getStartFrom();
        String startTo = queryVO.getStartTo();
        String cursorStartTime = queryVO.getCursorStartTime();
        String cursorId = queryVO.getCursorId();
        Boolean isFull = queryVO.getIsFull();

        OffsetDateTime sf = startFrom == null || startFrom.isEmpty() ? null : OffsetDateTime.parse(startFrom);
        OffsetDateTime st = startTo == null || startTo.isEmpty() ? null : OffsetDateTime.parse(startTo);
        OffsetDateTime cursorTime = cursorStartTime == null || cursorStartTime.isEmpty() ? null : OffsetDateTime.parse(cursorStartTime);
        boolean useCursor = cursorTime != null && cursorId != null && !cursorId.isBlank();

        String role = principal != null ? principal.getRole() : null;
        String studentNo = principal != null ? principal.getStudentNo() : null;
        boolean useAll = status != null || AuthorizationGuards.isAdmin(principal)
                || ("functionary".equals(role) && functionary != null && functionary.equals(studentNo));

        int total = useAll ? activityService.countActivitiesAll(type, status, functionary, name, sf, st, isFull)
                : activityService.countActivities(type, status, functionary, name, sf, st, isFull);
        int querySize = useCursor ? pageSize + 1 : pageSize;
        List<ActivityDTO> dtos = useAll
                ? (useCursor
                ? activityService.listActivitiesByCursorAll(type, status, functionary, name, sf, st, isFull, cursorTime, cursorId, querySize)
                : activityService.listActivitiesPagedAll(type, status, functionary, name, sf, st, isFull, page, querySize))
                : (useCursor
                ? activityService.listActivitiesByCursor(type, status, functionary, name, sf, st, isFull, cursorTime, cursorId, querySize)
                : activityService.listActivitiesPaged(type, status, functionary, name, sf, st, isFull, page, querySize));

        boolean hasMore = dtos.size() > pageSize;
        if (hasMore) {
            dtos = dtos.subList(0, pageSize);
        }

        List<ActivityVO> items = dtos.stream()
                .map(ActivityVO::fromDTO)
                .collect(java.util.stream.Collectors.toList());

        String nextCursorStartTime = null;
        String nextCursorId = null;
        if (hasMore && !dtos.isEmpty()) {
            ActivityDTO last = dtos.get(dtos.size() - 1);
            nextCursorStartTime = last.getStartTime() == null ? null : last.getStartTime().toString();
            nextCursorId = last.getId();
        }

        ActivityPageVO data = ActivityPageVO.builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .hasMore(hasMore)
                .nextCursorStartTime(nextCursorStartTime)
                .nextCursorId(nextCursorId)
                .build();

        return Result.success(data);
    }


    @PostMapping(consumes = {"multipart/form-data"})
    @BusinessOperation(action = "发布活动", targetType = "activity", detail = "负责人发布活动")
    public Result create(@AuthenticationPrincipal UserPrincipal principal,
                         @ModelAttribute ActivityDTO dto) {
        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        String role = principal.getRole();
        boolean canCreate = "functionary".equals(role) || AuthorizationGuards.isAdmin(principal);
        if (!canCreate) {
            throw BusinessException.forbidden("FORBIDDEN");
        }
        dto.setFunctionary(studentNo);
        ActivityDTO created = activityService.createActivity(dto);
        return Result.success(ActivityVO.fromDTO(created));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public Result update(@PathVariable("id") String id,
                         @AuthenticationPrincipal UserPrincipal principal,
                         @ModelAttribute ActivityDTO dto) {
        ActivityDTO existing = activityService.getActivityById(id);
        AuthorizationGuards.requireSelfOrAdmin(principal, existing.getFunctionary());
        dto.setFunctionary(existing.getFunctionary());
        ActivityDTO updated = activityService.updateActivity(id, dto);
        return Result.success(ActivityVO.fromDTO(updated));
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable("id") String id) {
        ActivityDTO dto = activityService.getActivityById(id);
        return Result.success(ActivityVO.fromDTO(dto));
    }

    @DeleteMapping("/{id}")
    public Result delete(@AuthenticationPrincipal UserPrincipal principal,
                         @PathVariable("id") String id) {
        ActivityDTO dto = activityService.getActivityById(id);
        AuthorizationGuards.requireSelfOrAdmin(principal, dto.getFunctionary());
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
        activityService.enroll(id, AuthorizationGuards.requireStudentNo(principal));
        return Result.success();
    }

    @PostMapping("/{id}/unenroll")
    public Result unenroll(@PathVariable("id") String id,
                           @AuthenticationPrincipal UserPrincipal principal) {
        activityService.unenroll(id, AuthorizationGuards.requireStudentNo(principal));
        return Result.success();
    }

    @PostMapping("/{id}/review")
    @BusinessOperation(action = "审核活动", targetType = "activity", targetIdParam = "id", detail = "管理员审核活动")
    public Result review(@AuthenticationPrincipal UserPrincipal principal,
                         @PathVariable("id") String id,
                         @RequestParam("approve") boolean approve,
                         @RequestParam(value = "reason", required = false) String reason) {
        AuthorizationGuards.requireAdmin(principal);
        ActivityDTO dto = activityService.reviewActivity(id, approve, reason, AuthorizationGuards.requireStudentNo(principal));
        return Result.success(ActivityVO.fromDTO(dto));
    }

    @GetMapping("/MyActivities")
    public Result getMyActivities(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                  @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        MyActivityPageVO data = myActivityService.getMyActivities(AuthorizationGuards.requireStudentNo(principal), page, pageSize);
        return Result.success(data);
    }

    @PostMapping("/import")
    @BusinessOperation(action = "导入活动", targetType = "activity", detail = "负责人导入活动")
    public Result importActivity(@AuthenticationPrincipal UserPrincipal principal,
                                 @ModelAttribute ActivityImportDTO dto) {
        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        boolean isAdmin = AuthorizationGuards.isAdmin(principal);

        String activityId = pendingActivityService
                .importActivity(dto, studentNo, isAdmin);
        Map<String, Object> result = new HashMap<>();
        result.put("id", activityId);
        result.put("status", isAdmin ? "APPROVED" : "PENDING_REVIEW");
        return Result.success(result);
    }

    @GetMapping("/MyStatus")
    public Result getMyStatus(@AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> data = myActivityService.getMyStatus(AuthorizationGuards.requireStudentNo(principal));
        return Result.success(data);
    }

    /**
     * 上传附件
     * 支持文档、图片、压缩包等多种格式
     */
    @PostMapping("/upload/attachment")
    public Result uploadAttachment(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String description) {
        if (file.isEmpty()) {
            throw BusinessException.badRequest("文件不能为空");
        }

        try {
            String filePath = fileUploadService.uploadAttachment(file);
            String originalFilename = file.getOriginalFilename();
            String fileType = FilenameUtils.getExtension(originalFilename != null ? originalFilename : "");

            AttachmentVO attachmentVO = AttachmentVO.builder()
                    .fileName(originalFilename)
                    .filePath(filePath)
                    .fileType(fileType)
                    .fileSize(file.getSize())
                    .description(description)
                    .build();

            return Result.success(attachmentVO);
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(e.getMessage());
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new IllegalStateException("ATTACHMENT_UPLOAD_FAILED", e);
        }
    }

    /**
     * 删除附件
     */
    @DeleteMapping("/attachment")
    public Result deleteAttachment(@RequestParam("filePath") String filePath) {
        boolean deleted = fileUploadService.deleteAttachment(filePath);
        if (!deleted) {
            throw BusinessException.notFound("附件删除失败或文件不存在");
        }
        return Result.success("附件删除成功");
    }

    /**
     * 获取附件信息
     */
    @GetMapping("/attachment/info")
    public Result getAttachmentInfo(@RequestParam("filePath") String filePath) {
        Map<String, Object> info = fileUploadService.getFileInfo(filePath);
        if (info == null) {
            throw BusinessException.notFound("附件不存在");
        }
        return Result.success(info);
    }
}
