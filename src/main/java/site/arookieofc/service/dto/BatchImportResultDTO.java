package site.arookieofc.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResultDTO {
    private String batchId;
    private int totalRecords;
    private int validRecords;
    private int invalidRecords;
    private int newUsersCreated;
    private int newActivitiesCreated;
    private int participantsAdded;
    private int hoursGranted;
    private List<String> createdUserStudentNos;
    private List<String> createdActivityNames;
    private List<String> errors;
}

