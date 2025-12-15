package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户统计分页VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatPageVO {
    private Long total;              // 总记录数
    private Integer current;         // 当前页
    private Integer size;            // 每页大小
    private List<UserStatVO> records; // 用户统计列表
}

