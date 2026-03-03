package site.arookieofc.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量导入结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResultDTO {
    private String batchId;             // 批次ID（提交审核时返回）
    private int totalRecords;           // 总记录数
    private int newUsersCreated;        // 新创建的用户数
    private int newActivitiesCreated;   // 新创建的活动数
    private int participantsAdded;      // 添加的参与关系数
    private int hoursGranted;           // 发放时长的记录数
    private List<String> createdUserStudentNos;    // 新创建的用户学号列表
    private List<String> createdActivityNames;     // 新创建的活动名称列表
    private List<String> errors;                   // 错误信息列表
}

