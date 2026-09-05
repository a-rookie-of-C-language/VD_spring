package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatusTaskMetricsVO {
    private long pending;
    private long sent;
    private long failed;
    private long dead;
    private long done;
    private long total;
}
