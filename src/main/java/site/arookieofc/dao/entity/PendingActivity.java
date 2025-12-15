package site.arookieofc.dao.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import site.arookieofc.service.BO.ActivityType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PendingActivity {
    private String id;
    private String functionary;
    private String name;
    private ActivityType type;
    private String description;
    private Double duration;
    private LocalDateTime endTime;
    private String coverPath;
    private LocalDateTime createdAt;
    private String submittedBy;
    private List<String> attachment;
    private List<String> participants;
}

