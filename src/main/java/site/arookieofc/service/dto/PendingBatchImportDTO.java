package site.arookieofc.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import site.arookieofc.dao.entity.PendingBatchImport;
import site.arookieofc.dao.entity.PendingBatchImportRecord;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 待审核批量导入DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingBatchImportDTO {
    private String id;
    private String submittedBy;          // 提交人学号
    private String originalFilename;     // 原始文件名
    private Integer totalRecords;        // 总记录数
    private OffsetDateTime createdAt;    // 创建时间
    private String status;               // 状态: PENDING/APPROVED/REJECTED
    private OffsetDateTime reviewedAt;   // 审核时间
    private String reviewedBy;           // 审核人学号
    private String rejectedReason;       // 拒绝理由
    private List<PendingBatchImportRecordDTO> records; // 详情记录

    public static PendingBatchImportDTO fromEntity(PendingBatchImport entity, ZoneId zone) {
        if (entity == null) return null;
        return PendingBatchImportDTO.builder()
                .id(entity.getId())
                .submittedBy(entity.getSubmittedBy())
                .originalFilename(entity.getOriginalFilename())
                .totalRecords(entity.getTotalRecords())
                .createdAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atZone(zone).toOffsetDateTime())
                .status(entity.getStatus())
                .reviewedAt(entity.getReviewedAt() == null ? null : entity.getReviewedAt().atZone(zone).toOffsetDateTime())
                .reviewedBy(entity.getReviewedBy())
                .rejectedReason(entity.getRejectedReason())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingBatchImportRecordDTO {
        private Long id;
        private String batchId;
        private String username;
        private String gender;
        private String college;
        private String grade;
        private String studentNo;
        private String phone;
        private Double duration;
        private String activityName;
        private String originalActivityName;
        private Boolean userExists;

        public static PendingBatchImportRecordDTO fromEntity(PendingBatchImportRecord entity) {
            if (entity == null) return null;
            return PendingBatchImportRecordDTO.builder()
                    .id(entity.getId())
                    .batchId(entity.getBatchId())
                    .username(entity.getUsername())
                    .gender(entity.getGender())
                    .college(entity.getCollege())
                    .grade(entity.getGrade())
                    .studentNo(entity.getStudentNo())
                    .phone(entity.getPhone())
                    .duration(entity.getDuration())
                    .activityName(entity.getActivityName())
                    .originalActivityName(entity.getOriginalActivityName())
                    .userExists(entity.getUserExists())
                    .build();
        }
    }
}

