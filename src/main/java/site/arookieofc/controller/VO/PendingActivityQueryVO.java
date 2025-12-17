package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import site.arookieofc.service.BO.ActivityType;

/**
 * VO for pending activity list query parameters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingActivityQueryVO {
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer pageSize = 10;
    private ActivityType type;
    private String functionary;
    private String name;
    private String submittedBy;
}

