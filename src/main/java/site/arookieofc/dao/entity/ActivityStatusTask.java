package site.arookieofc.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatusTask {
    private Long id;
    private String eventId;
    private String activityId;
    private String targetStatus;
    private String source;
    private LocalDateTime executeAt;
    private String status;
    private Integer attempt;
    private String lastError;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
