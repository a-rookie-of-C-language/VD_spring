package site.arookieofc.dao.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;

/**
 * 批量导入详情记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PendingBatchImportRecord {
    private Long id;
    private String batchId;              // 批次ID
    private String username;             // 姓名
    private String gender;               // 性别
    private String college;              // 学院
    private String grade;                // 年级
    private String studentNo;            // 学号
    private String phone;                // 联系方式
    private Double duration;             // 服务时长（小时）
    private String activityName;         // 活动名称（规范化后）
    private String originalActivityName; // 原始活动名称
    private Boolean userExists;          // 用户是否已存在
}

