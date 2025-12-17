package site.arookieofc.controller.VO;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.BO.ActivityStatus;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ActivityListRequestVO {
    private Boolean isFull;
    private String startTo;
    private String startFrom;
    private String name;
    private String functionary;
    private ActivityStatus status;
    private ActivityType type;
    private Integer pageSize = 10;
    private Integer page = 1;
}




