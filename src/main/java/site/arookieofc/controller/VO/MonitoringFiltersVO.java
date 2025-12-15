package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 监控筛选选项VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringFiltersVO {
    private List<String> colleges;    // 学院列表
    private List<String> grades;      // 年级列表
    private List<String> clazzes;     // 班级列表
}

