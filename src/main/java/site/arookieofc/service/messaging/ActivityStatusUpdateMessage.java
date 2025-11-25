package site.arookieofc.service.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import site.arookieofc.service.BO.ActivityStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatusUpdateMessage {
    private String activityId;
    private ActivityStatus targetStatus;
}
