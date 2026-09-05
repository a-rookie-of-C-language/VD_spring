package site.arookieofc.service.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import site.arookieofc.service.BO.ActivityStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatusUpdateMessage {
    private String eventId;
    private String activityId;
    private ActivityStatus targetStatus;
    private Integer attempt;
    private LocalDateTime executeAt;
    private String source;
}
