package site.arookieofc.dao.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PendingBatchImportRecord {
    private Long id;
    private String batchId;
    private String username;
    private String gender;
    private String college;
    private String grade;
    private String studentNo;
    private String phone;
    private Double duration;
    private String activityName;
    private String originalActivityName;
    private Boolean userExists;
    private String validationStatus; // VALID/INVALID
    private String validationError;
}

