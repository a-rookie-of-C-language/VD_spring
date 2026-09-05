package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessOperationLogVO {
    private String timestamp;
    private String operatorStudentNo;
    private String operatorRole;
    private String operatorIp;
    private String operatorUserAgent;
    private String requestId;
    private String action;
    private String targetType;
    private String targetId;
    private String targetName;
    private String detail;
    private String status;
    private Long durationMs;
    private String beforeChange;
    private String afterChange;
}
