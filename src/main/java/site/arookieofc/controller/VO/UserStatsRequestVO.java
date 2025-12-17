package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsRequestVO {
    private Integer page = 1;
    private Integer pageSize = 10;
    private String college;
    private String grade;
    private String clazz;
    private String sortField;
    private String sortOrder = "desc";
}

