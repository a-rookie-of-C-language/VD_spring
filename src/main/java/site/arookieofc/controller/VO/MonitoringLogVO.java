package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringLogVO {
    private String timestamp;
    private String level;
    private String logger;
    private String thread;
    private String message;
    private String service;
    private String environment;
}
