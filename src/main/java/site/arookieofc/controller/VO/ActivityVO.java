package site.arookieofc.controller.VO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class ActivityVO {
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
    private OffsetDateTime expectedEndTime;
    private OffsetDateTime endTime;
    @JsonProperty("CoverPath")
    private String coverPath;
    /**
     * Cover image URL path (e.g., "/covers/xxx.jpg")
     * Frontend should use this URL directly to load images with browser caching
     */
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

    public static ActivityVO fromDTO(ActivityDTO dto) {
        return ActivityVO.builder()
                .id(dto.getId())
                .functionary(dto.getFunctionary())
                .name(dto.getName())
                .type(dto.getType())
                .description(dto.getDescription())
                .enrollmentStartTime(dto.getEnrollmentStartTime())
                .enrollmentEndTime(dto.getEnrollmentEndTime())
                .expectedEndTime(dto.getExpectedEndTime())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .coverPath(dto.getCoverPath())
                .coverImage(dto.getCoverImage())
                .maxParticipants(dto.getMaxParticipants())
                .attachment(dto.getAttachment())
                .participants(dto.getParticipants())
                .status(dto.getStatus())
                .isFull(dto.getIsFull())
                .duration(dto.getDuration())
                .build();
    }
}
