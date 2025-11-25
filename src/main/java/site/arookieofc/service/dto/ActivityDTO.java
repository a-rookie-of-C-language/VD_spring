package site.arookieofc.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityDTO {
    private String id;
    private String functionary;
    private String name;
    private ActivityType type;
    private String description;
    @JsonProperty("EnrollmentStartTime")
    private OffsetDateTime enrollmentStartTime;
    @JsonProperty("EnrollmentEndTime")
    private OffsetDateTime enrollmentEndTime;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    @JsonProperty("CoverPath")
    @Size(max = 255)
    private String coverPath;
    @JsonIgnore
    private MultipartFile coverFile;
    @JsonProperty("CoverImage")
    private String coverImage;
    @JsonProperty("maxParticipants")
    private Integer maxParticipants;
    @JsonProperty("Attachment")
    private List<String> attachment;
    private List<String> participants;
    private ActivityStatus status;
    private Boolean isFull;
    private Double duration;

    public Activity toEntity(java.time.ZoneId zone) {
        return site.arookieofc.dao.entity.Activity.builder()
                .id(this.id)
                .functionary(this.functionary)
                .name(this.name)
                .type(this.type)
                .description(this.description)
                .enrollmentStartTime(this.enrollmentStartTime == null ? null : this.enrollmentStartTime.atZoneSameInstant(zone).toLocalDateTime())
                .enrollmentEndTime(this.enrollmentEndTime == null ? null : this.enrollmentEndTime.atZoneSameInstant(zone).toLocalDateTime())
                .startTime(this.startTime == null ? null : this.startTime.atZoneSameInstant(zone).toLocalDateTime())
                .endTime(this.endTime == null ? null : this.endTime.atZoneSameInstant(zone).toLocalDateTime())
                .coverPath(this.coverPath)
                .maxParticipant(this.maxParticipants)
                .status(this.status)
                .isFull(this.isFull != null ? this.isFull : false)
                .duration(this.duration)
                .build();
    }

    public Activity toEntity(String id, java.time.ZoneId zone) {
        return site.arookieofc.dao.entity.Activity.builder()
                .id(id)
                .functionary(this.functionary)
                .name(this.name)
                .type(this.type)
                .description(this.description)
                .enrollmentStartTime(this.enrollmentStartTime == null ? null : this.enrollmentStartTime.atZoneSameInstant(zone).toLocalDateTime())
                .enrollmentEndTime(this.enrollmentEndTime == null ? null : this.enrollmentEndTime.atZoneSameInstant(zone).toLocalDateTime())
                .startTime(this.startTime == null ? null : this.startTime.atZoneSameInstant(zone).toLocalDateTime())
                .endTime(this.endTime == null ? null : this.endTime.atZoneSameInstant(zone).toLocalDateTime())
                .coverPath(this.coverPath)
                .maxParticipant(this.maxParticipants)
                .status(this.status)
                .isFull(this.isFull != null ? this.isFull : false)
                .duration(this.duration)
                .build();
    }

    /**
     * Convert Activity entity to DTO
     * @param activity Activity entity
     * @param zone ZoneId for time conversion
     * @return ActivityDTO
     */
    
}
