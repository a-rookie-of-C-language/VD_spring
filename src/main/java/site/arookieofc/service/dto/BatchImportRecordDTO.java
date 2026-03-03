package site.arookieofc.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Excel批量导入记录DTO
 * 对应Excel表格的一行数据
 * 字段：姓名、性别、学院、年级、学号、联系方式、服务时长（小时）、活动名称
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportRecordDTO {
    private String username;       // 姓名
    private String gender;         // 性别
    private String college;        // 学院
    private String grade;          // 年级
    private String studentNo;      // 学号
    private String phone;          // 联系方式
    private Double duration;       // 服务时长（小时）
    private String activityName;   // 活动名称（原始名称，可能包含小时数）
}

