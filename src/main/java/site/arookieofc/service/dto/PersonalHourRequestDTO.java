package site.arookieofc.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.dao.entity.PersonalHourRequest;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * DTO for personal hour request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonalHourRequestDTO {
    private String id;
    private String applicantStudentNo;
    private String applicantName;         // 申请人姓名 (从user表关联)
    private String name;                  // 活动名称
    private String functionary;           // 证明人/负责人姓名
    private ActivityType type;            // 活动类型
    private String description;           // 活动简述
    private OffsetDateTime startTime;     // 活动开始时间
    private OffsetDateTime endTime;       // 活动结束时间
    private Double duration;              // 申请时长
    private ActivityStatus status;         // 审核状态
    private String rejectedReason;        // 拒绝理由
    private OffsetDateTime createdAt;     // 申请时间
    private OffsetDateTime reviewedAt;    // 审核时间
    private String reviewedBy;            // 审核人学号
    @JsonProperty("attachments")
    private List<String> attachments;     // 证明材料路径
    @JsonIgnore
    private List<MultipartFile> files;    // 上传的文件

    public static PersonalHourRequestDTO fromEntity(PersonalHourRequest entity, ZoneId zone) {
        if (entity == null) return null;
        return PersonalHourRequestDTO.builder()
                .id(entity.getId())
                .applicantStudentNo(entity.getApplicantStudentNo())
                .name(entity.getName())
                .functionary(entity.getFunctionary())
                .type(entity.getType())
                .description(entity.getDescription())
                .startTime(entity.getStartTime() == null ? null : entity.getStartTime().atZone(zone).toOffsetDateTime())
                .endTime(entity.getEndTime() == null ? null : entity.getEndTime().atZone(zone).toOffsetDateTime())
                .duration(entity.getDuration())
                .status(entity.getStatus())
                .rejectedReason(entity.getRejectedReason())
                .createdAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atZone(zone).toOffsetDateTime())
                .reviewedAt(entity.getReviewedAt() == null ? null : entity.getReviewedAt().atZone(zone).toOffsetDateTime())
                .reviewedBy(entity.getReviewedBy())
                .attachments(entity.getAttachments())
                .build();
    }

    /**
     * Convert DTO to entity
     */
    public PersonalHourRequest toEntity(ZoneId zone) {
        return PersonalHourRequest.builder()
                .id(this.id)
                .applicantStudentNo(this.applicantStudentNo)
                .name(this.name)
                .functionary(this.functionary)
                .type(this.type)
                .description(this.description)
                .startTime(this.startTime == null ? null : this.startTime.atZoneSameInstant(zone).toLocalDateTime())
                .endTime(this.endTime == null ? null : this.endTime.atZoneSameInstant(zone).toLocalDateTime())
                .duration(this.duration)
                .status(this.status)
                .rejectedReason(this.rejectedReason)
                .createdAt(this.createdAt == null ? null : this.createdAt.atZoneSameInstant(zone).toLocalDateTime())
                .reviewedAt(this.reviewedAt == null ? null : this.reviewedAt.atZoneSameInstant(zone).toLocalDateTime())
                .reviewedBy(this.reviewedBy)
                .attachments(this.attachments)
                .build();
    }
}

