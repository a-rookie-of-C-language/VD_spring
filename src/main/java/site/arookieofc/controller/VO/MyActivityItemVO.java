package site.arookieofc.controller.VO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import site.arookieofc.dao.entity.PendingActivity;
import site.arookieofc.dao.entity.PendingBatchImport;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.PendingActivityDTO;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 统一的"我的活动"项VO
 * 可以表示普通活动、待审核活动或批量导入
 */
@Data
@Builder
public class MyActivityItemVO {
    private String id;
    private String itemType; // "ACTIVITY" 或 "BATCH_IMPORT" 或 "PENDING_ACTIVITY"
    private String functionary;
    private String name;
    private ActivityType activityType;
    private String description;
    @JsonProperty("EnrollmentStartTime")
    private OffsetDateTime enrollmentStartTime;
    @JsonProperty("EnrollmentEndTime")
    private OffsetDateTime enrollmentEndTime;
    private OffsetDateTime startTime;
    private OffsetDateTime expectedEndTime;
    private OffsetDateTime endTime;
    @JsonProperty("CoverPath")
    private String coverPath;
    @JsonProperty("CoverImage")
    private String coverImage;
    @JsonProperty("maxParticipants")
    private Integer maxParticipants;
    @JsonProperty("Attachment")
    private List<String> attachment;
    private List<String> participants;
    private ActivityStatus status;
    private Boolean isFull;
    private Double duration;

    // 批量导入/待审核特有字段
    private String batchStatus; // PENDING/APPROVED/REJECTED (for batch import) or review status
    private String originalFilename;
    private Integer totalRecords;
    private OffsetDateTime createdAt;
    private OffsetDateTime reviewedAt;
    private String reviewedBy;
    private String rejectedReason;
    private String submittedBy;

    /**
     * 从ActivityVO创建
     */
    public static MyActivityItemVO fromActivityVO(ActivityVO activityVO) {
        return MyActivityItemVO.builder()
                .id(activityVO.getId())
                .itemType("ACTIVITY")
                .functionary(activityVO.getFunctionary())
                .name(activityVO.getName())
                .activityType(activityVO.getType())
                .description(activityVO.getDescription())
                .enrollmentStartTime(activityVO.getEnrollmentStartTime())
                .enrollmentEndTime(activityVO.getEnrollmentEndTime())
                .startTime(activityVO.getStartTime())
                .expectedEndTime(activityVO.getExpectedEndTime())
                .endTime(activityVO.getEndTime())
                .coverPath(activityVO.getCoverPath())
                .coverImage(activityVO.getCoverImage())
                .maxParticipants(activityVO.getMaxParticipants())
                .attachment(activityVO.getAttachment())
                .participants(activityVO.getParticipants())
                .status(activityVO.getStatus())
                .isFull(activityVO.getIsFull())
                .duration(activityVO.getDuration())
                .build();
    }

    /**
     * 从PendingActivity创建
     */
    public static MyActivityItemVO fromPendingActivity(PendingActivity pendingActivity, ZoneId zone) {
        return MyActivityItemVO.builder()
                .id(pendingActivity.getId())
                .itemType("PENDING_ACTIVITY")
                .functionary(pendingActivity.getFunctionary())
                .name(pendingActivity.getName())
                .activityType(pendingActivity.getType())
                .description(pendingActivity.getDescription())
                .duration(pendingActivity.getDuration())
                .endTime(pendingActivity.getEndTime() == null ? null :
                        pendingActivity.getEndTime().atZone(zone).toOffsetDateTime())
                .coverPath(pendingActivity.getCoverPath())
                .attachment(pendingActivity.getAttachment())
                .participants(pendingActivity.getParticipants())
                .status(pendingActivity.getStatus())
                .createdAt(pendingActivity.getCreatedAt() == null ? null :
                          pendingActivity.getCreatedAt().atZone(zone).toOffsetDateTime())
                .submittedBy(pendingActivity.getSubmittedBy())
                .batchStatus("PENDING") // 待审核活动都是PENDING状态
                .maxParticipants(pendingActivity.getParticipants() != null ?
                                pendingActivity.getParticipants().size() : 0)
                .build();
    }

    public static MyActivityItemVO fromPendingActivityDTO(PendingActivityDTO pendingActivity) {
        return MyActivityItemVO.builder()
                .id(pendingActivity.getId())
                .itemType("PENDING_ACTIVITY")
                .functionary(pendingActivity.getFunctionary())
                .name(pendingActivity.getName())
                .activityType(pendingActivity.getType())
                .description(pendingActivity.getDescription())
                .duration(pendingActivity.getDuration())
                .endTime(pendingActivity.getEndTime())
                .coverPath(pendingActivity.getCoverPath())
                .coverImage(pendingActivity.getCoverImage())
                .attachment(pendingActivity.getAttachment())
                .participants(pendingActivity.getParticipants())
                .status(pendingActivity.getStatus())
                .createdAt(pendingActivity.getCreatedAt())
                .reviewedAt(pendingActivity.getReviewedAt())
                .reviewedBy(pendingActivity.getReviewedBy())
                .rejectedReason(pendingActivity.getRejectedReason())
                .submittedBy(pendingActivity.getSubmittedBy())
                .batchStatus("PENDING")
                .maxParticipants(pendingActivity.getParticipants() != null ? pendingActivity.getParticipants().size() : 0)
                .build();
    }

    /**
     * 从PendingBatchImport创建
     */
    public static MyActivityItemVO fromPendingBatchImport(PendingBatchImport batchImport, ZoneId zone) {
        return MyActivityItemVO.builder()
                .id(batchImport.getId())
                .itemType("BATCH_IMPORT")
                .name("批量导入: " + batchImport.getOriginalFilename())
                .description("批量导入" + batchImport.getTotalRecords() + "条记录")
                .batchStatus(batchImport.getStatus())
                .originalFilename(batchImport.getOriginalFilename())
                .totalRecords(batchImport.getTotalRecords())
                .createdAt(batchImport.getCreatedAt() == null ? null :
                           batchImport.getCreatedAt().atZone(zone).toOffsetDateTime())
                .reviewedAt(batchImport.getReviewedAt() == null ? null :
                           batchImport.getReviewedAt().atZone(zone).toOffsetDateTime())
                .reviewedBy(batchImport.getReviewedBy())
                .rejectedReason(batchImport.getRejectedReason())
                .functionary(batchImport.getSubmittedBy())
                .submittedBy(batchImport.getSubmittedBy())
                .build();
    }
}

