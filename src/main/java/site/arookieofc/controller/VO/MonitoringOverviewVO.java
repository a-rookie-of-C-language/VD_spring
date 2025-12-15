package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 监控概览数据VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringOverviewVO {
    private Long totalUsers;              // 筛选条件下的总人数
    private Double totalDuration;         // 总志愿时长 (小时)
    private Double averageDuration;       // 平均志愿时长
    private Long totalActivities;         // 总参加活动人次
    private Double averageActivities;     // 平均参加活动数
    private Long completedActivities;     // 已完成的活动数
}

