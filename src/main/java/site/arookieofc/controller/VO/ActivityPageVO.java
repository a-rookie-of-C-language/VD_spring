package site.arookieofc.controller.VO;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@Builder
@ToString
public class ActivityPageVO {
    private List<ActivityVO> items;
    private int total;
    private int page;
    private int pageSize;
}
