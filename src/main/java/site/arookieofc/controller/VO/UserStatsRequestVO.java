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
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer pageSize = 10;
    private String college;
    private String grade;
    private String clazz;
    private String sortField;
    @Builder.Default
    private String sortOrder = "desc";
}
