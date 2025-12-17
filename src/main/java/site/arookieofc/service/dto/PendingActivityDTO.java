package site.arookieofc.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import site.arookieofc.dao.entity.PendingActivity;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * DTO for pending activity that's waiting for admin approval
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingActivityDTO {
    private String id;
    private String functionary;
    private String name;
    private ActivityType type;
    private String description;
    private Double duration;
    private OffsetDateTime endTime;
    @JsonProperty("CoverPath")
    private String coverPath;
    @JsonProperty("CoverImage")
    private String coverImage;
    private OffsetDateTime createdAt;
    private String submittedBy;
    @JsonProperty("Attachment")
    private List<String> attachment;
    private List<String> participants;
    private ActivityStatus status;  // 审核状态
    private OffsetDateTime reviewedAt;       // 审核时间
    private String reviewedBy;               // 审核人学号
    private String rejectedReason;           // 拒绝理由

    public static PendingActivityDTO fromEntity(PendingActivity entity, ZoneId zone) {
        if (entity == null) return null;
        return PendingActivityDTO.builder()
                .id(entity.getId())
                .functionary(entity.getFunctionary())
                .name(entity.getName())
                .type(entity.getType())
                .description(entity.getDescription())
                .duration(entity.getDuration())
                .endTime(entity.getEndTime() == null ? null : entity.getEndTime().atZone(zone).toOffsetDateTime())
                .coverPath(entity.getCoverPath())
                .createdAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atZone(zone).toOffsetDateTime())
                .submittedBy(entity.getSubmittedBy())
                .attachment(entity.getAttachment())
                .participants(entity.getParticipants())
                .status(entity.getStatus())
                .reviewedAt(entity.getReviewedAt() == null ? null : entity.getReviewedAt().atZone(zone).toOffsetDateTime())
                .reviewedBy(entity.getReviewedBy())
                .rejectedReason(entity.getRejectedReason())
                .build();
    }
}

