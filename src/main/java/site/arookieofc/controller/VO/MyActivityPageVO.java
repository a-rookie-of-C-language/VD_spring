package site.arookieofc.controller.VO;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * 我的活动分页响应VO
 * 包含普通活动和批量导入项目
 */
@Data
@Builder
@ToString
public class MyActivityPageVO {
    private List<MyActivityItemVO> items;
    private int total;
    private int page;
    private int pageSize;
}

