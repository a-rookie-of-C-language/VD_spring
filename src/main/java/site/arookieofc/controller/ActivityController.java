package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.audit.BusinessOperation;
import site.arookieofc.controller.VO.*;
import site.arookieofc.dao.entity.PendingBatchImport;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.ActivityService;
import site.arookieofc.service.BatchImportService;
import site.arookieofc.service.UserService;
import site.arookieofc.service.FileUploadService;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.dto.ActivityImportDTO;
import site.arookieofc.service.dto.UserDTO;
import org.apache.commons.io.FilenameUtils;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private final BatchImportService batchImportService;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

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


    @PostMapping(consumes = {"multipart/form-data"})
    @BusinessOperation(action = "发布活动", targetType = "activity", detail = "负责人发布活动")
    public Result create(@ModelAttribute ActivityDTO dto) {
        ActivityDTO created = activityService.createActivity(dto);
        return Result.success(ActivityVO.fromDTO(created));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public Result update(@PathVariable("id") String id,
                         @AuthenticationPrincipal UserPrincipal principal,
                         @ModelAttribute ActivityDTO dto) {
        dto.setFunctionary(principal.getStudentNo());
        ActivityDTO updated = activityService.updateActivity(id, dto);
        return Result.success(ActivityVO.fromDTO(updated));
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable("id") String id) {
        ActivityDTO dto = activityService.getActivityById(id);
        return Result.success(ActivityVO.fromDTO(dto));
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
        activityService.enroll(id, principal.getStudentNo());
        return Result.success();
    }

    @PostMapping("/{id}/unenroll")
    public Result unenroll(@PathVariable("id") String id,
                           @AuthenticationPrincipal UserPrincipal principal) {
        activityService.unenroll(id, principal.getStudentNo());
        return Result.success();
    }

    @PostMapping("/{id}/review")
    @BusinessOperation(action = "审核活动", targetType = "activity", targetIdParam = "id", detail = "管理员审核活动")
    public Result review(@AuthenticationPrincipal UserPrincipal principal,
                         @PathVariable("id") String id,
                         @RequestParam("approve") boolean approve,
                         @RequestParam(value = "reason", required = false) String reason) {
        ActivityDTO dto = activityService.reviewActivity(id, approve, reason, principal.getStudentNo());
        return Result.success(ActivityVO.fromDTO(dto));
    }

    @GetMapping("/MyActivities")
    public Result getMyActivities(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                  @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        // Authentication handled by Spring Security
        String studentNo = principal.getStudentNo();

        // 获取普通活动（用户作为functionary创建的）
        List<ActivityDTO> activityDtos = activityService
                .listActivitiesPagedAll(null, null, studentNo,
                        null, null, null, null, 1, Integer.MAX_VALUE); // 获取所有，后面统一分页

        List<MyActivityItemVO> allItems = new ArrayList<>();

        // 添加普通活动
        allItems.addAll(activityDtos.stream()
                .map(ActivityVO::fromDTO)
                .map(MyActivityItemVO::fromActivityVO)
                .collect(Collectors.toList()));

        // 获取待审核的活动导入（PendingActivity）
        List<site.arookieofc.dao.entity.PendingActivity> pendingActivities =
                pendingActivityService.listAllPendingActivities().stream()
                        .filter(dto -> studentNo.equals(dto.getSubmittedBy()))
                        .map(dto -> {
                            // 从DTO转换回Entity用于MyActivityItemVO
                            return site.arookieofc.dao.entity.PendingActivity.builder()
                                    .id(dto.getId())
                                    .functionary(dto.getFunctionary())
                                    .name(dto.getName())
                                    .type(dto.getType())
                                    .description(dto.getDescription())
                                    .duration(dto.getDuration())
                                    .endTime(dto.getEndTime() == null ? null :
                                            dto.getEndTime().atZoneSameInstant(ZONE).toLocalDateTime())
                                    .coverPath(dto.getCoverPath())
                                    .createdAt(dto.getCreatedAt() == null ? null :
                                              dto.getCreatedAt().atZoneSameInstant(ZONE).toLocalDateTime())
                                    .submittedBy(dto.getSubmittedBy())
                                    .attachment(dto.getAttachment())
                                    .participants(dto.getParticipants())
                                    .status(dto.getStatus())
                                    .build();
                        })
                        .collect(Collectors.toList());

        // 添加待审核活动
        allItems.addAll(pendingActivities.stream()
                .map(pa -> MyActivityItemVO.fromPendingActivity(pa, ZONE))
                .collect(Collectors.toList()));

        // 获取批量导入项目（审核中和审核失败的）
        List<PendingBatchImport> pendingBatchImports = batchImportService
                .getPendingBatchImportsBySubmitter(studentNo);

        // 添加审核中和审核失败的批量导入
        allItems.addAll(pendingBatchImports.stream()
                .filter(bi -> "PENDING".equals(bi.getStatus()) || "REJECTED".equals(bi.getStatus()))
                .map(bi -> MyActivityItemVO.fromPendingBatchImport(bi, ZONE))
                .collect(Collectors.toList()));

        // 按创建时间倒序排序
        allItems.sort((a, b) -> {
            OffsetDateTime timeA = a.getCreatedAt() != null ? a.getCreatedAt() :
                                   (a.getStartTime() != null ? a.getStartTime() : OffsetDateTime.MIN);
            OffsetDateTime timeB = b.getCreatedAt() != null ? b.getCreatedAt() :
                                   (b.getStartTime() != null ? b.getStartTime() : OffsetDateTime.MIN);
            return timeB.compareTo(timeA); // 降序
        });

        // 手动分页
        int total = allItems.size();
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, total);

        List<MyActivityItemVO> pagedItems = startIndex < total ?
                allItems.subList(startIndex, endIndex) : new ArrayList<>();

        MyActivityPageVO data = MyActivityPageVO.builder()
                .items(pagedItems)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();

        return Result.success(data);
    }

    @PostMapping("/import")
    @BusinessOperation(action = "导入活动", targetType = "activity", detail = "负责人导入活动")
    public Result importActivity(@AuthenticationPrincipal UserPrincipal principal,
                                 @ModelAttribute ActivityImportDTO dto) {
        String role = principal.getRole();
        boolean isAdmin = "admin".equals(role) || "superAdmin".equals(role);

        String activityId = pendingActivityService
                .importActivity(dto, principal.getStudentNo(), isAdmin);
        Map<String, Object> result = new HashMap<>();
        result.put("id", activityId);
        result.put("status", isAdmin ? "APPROVED" : "PENDING_REVIEW");
        return Result.success(result);
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
