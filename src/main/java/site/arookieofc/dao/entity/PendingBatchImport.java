package site.arookieofc.dao.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;

import java.time.LocalDateTime;

/**
 * 批量导入主记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PendingBatchImport {
    private String id;
    private String submittedBy;          // 提交人学号
    private String originalFilename;     // 原始文件名
    private Integer totalRecords;        // 总记录数
    private LocalDateTime createdAt;     // 创建时间
    private String status;               // 状态: PENDING/APPROVED/REJECTED
    private LocalDateTime reviewedAt;    // 审核时间
    private String reviewedBy;           // 审核人学号
    private String rejectedReason;       // 拒绝理由
}

