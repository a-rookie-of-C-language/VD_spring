package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;

/**
 * VO for activity list query parameters
 * Used as @RequestBody instead of multiple @RequestParam
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityQueryVO {
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer pageSize = 10;
    private ActivityType type;
    private ActivityStatus status;
    private String functionary;
    private String name;
    private String startFrom;  // ISO-8601 OffsetDateTime string
    private String startTo;    // ISO-8601 OffsetDateTime string
    private Boolean isFull;
    private String sortBy;     // Optional: sorting field
    private String sortOrder;  // Optional: "asc" or "desc"
}

