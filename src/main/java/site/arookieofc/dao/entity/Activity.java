package site.arookieofc.dao.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Activity {
    private String id;
    private String functionary;
    private String name;
    private ActivityType type;
    private String description;
    private LocalDateTime enrollmentStartTime;
    private LocalDateTime enrollmentEndTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String coverPath;
    private Integer maxParticipant;
    private List<String> attachment;
    private List<String> participants;
    private ActivityStatus status;
    private Boolean isFull;
    private Double duration;

    public site.arookieofc.service.dto.ActivityDTO toDTO(java.time.ZoneId zone) {
        java.time.OffsetDateTime est = enrollmentStartTime == null ? null : enrollmentStartTime.atZone(zone).toOffsetDateTime();
        java.time.OffsetDateTime eet = enrollmentEndTime == null ? null : enrollmentEndTime.atZone(zone).toOffsetDateTime();
        java.time.OffsetDateTime st = startTime == null ? null : startTime.atZone(zone).toOffsetDateTime();
        java.time.OffsetDateTime et = endTime == null ? null : endTime.atZone(zone).toOffsetDateTime();
        return site.arookieofc.service.dto.ActivityDTO.builder()
                .id(id)
                .functionary(functionary)
                .name(name)
                .type(type)
                .description(description)
                .enrollmentStartTime(est)
                .enrollmentEndTime(eet)
                .startTime(st)
                .endTime(et)
                .coverPath(coverPath)
                .maxParticipants(maxParticipant)
                .attachment(attachment)
                .participants(participants)
                .status(status)
                .isFull(isFull)
                .duration(duration)
                .build();
    }
}
