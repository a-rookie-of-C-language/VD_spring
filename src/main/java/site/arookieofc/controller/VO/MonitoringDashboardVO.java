package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 监控大屏数据VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringDashboardVO {

    private OverviewVO overview;

    private ClassificationStatsVO classificationStats;

    private List<ActivityTypeDistributionVO> activityTypes;

    private List<TopUserVO> topUsers;

    private List<TopUserVO> growthRanking;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewVO {
        private Long totalUsers;              // 总用户数
        private Long totalActivities;         // 总活动数
        private Long completedActivities;     // 已完成活动数
        private Double totalDuration;         // 总志愿时长
        private Long totalParticipants;       // 总参与人次
        private Double averageDuration;       // 平均志愿时长
        private Long newActivities;           // 新增活动数（根据timeRange）
        private Long activeUsers;             // 活跃用户数（根据timeRange）
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassificationStatsVO {
        private List<ClassificationItemVO> byGrade;      // 按年级统计
        private List<ClassificationItemVO> byCollege;    // 按学院统计
        private List<ClassificationItemVO> byClazz;      // 按班级统计（Top 10）
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassificationItemVO {
        private String name;                  // 分类名称（年级/学院/班级）
        private Long userCount;               // 用户数
        private Long activityCount;           // 参与活动数
        private Double totalHours;            // 总时长
        private Double averageHours;          // 平均时长
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityTypeDistributionVO {
        private String name;                  // 活动类型名称
        private Long value;                   // 数量
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUserVO {
        private Integer rank;                 // 排名
        private String studentNo;             // 学号
        private String name;                  // 姓名
        private Double hours;                 // 志愿时长
    }
}

