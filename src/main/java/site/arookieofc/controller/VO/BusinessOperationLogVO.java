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
    private String action;
    private String targetType;
    private String targetId;
    private String targetName;
    private String detail;
    private String status;
}
