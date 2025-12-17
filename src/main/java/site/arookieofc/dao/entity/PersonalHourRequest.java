package site.arookieofc.dao.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for personal hour request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersonalHourRequest {
    private String id;
    private String applicantStudentNo;   // 申请人学号
    private String name;                  // 活动名称
    private String functionary;           // 证明人/负责人姓名
    private ActivityType type;            // 活动类型
    private String description;           // 活动简述
    private LocalDateTime startTime;      // 活动开始时间
    private LocalDateTime endTime;        // 活动结束时间
    private Double duration;              // 申请时长
    private ActivityStatus status;         // 审核状态
    private String rejectedReason;        // 拒绝理由
    private LocalDateTime createdAt;      // 申请时间
    private LocalDateTime reviewedAt;     // 审核时间
    private String reviewedBy;            // 审核人学号
    private List<String> attachments;     // 证明材料路径
}

