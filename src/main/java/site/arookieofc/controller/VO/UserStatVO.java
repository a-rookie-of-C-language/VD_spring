package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户统计明细VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatVO {
    private String studentNo;       // 学号
    private String name;            // 姓名
    private String college;         // 学院
    private String grade;           // 年级
    private String clazz;           // 班级
    private Double totalDuration;   // 个人总时长
    private Long activityCount;     // 个人参加活动数
    private Integer rank;           // 排名
}

