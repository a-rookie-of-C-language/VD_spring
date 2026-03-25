package site.arookieofc.dao.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VolunteerHourGrantRecord {
    private String id;
    private String studentNo;
    private String sourceType;
    private String sourceId;
    private String sourceName;
    private Double duration;
    private LocalDateTime grantedAt;
    private String operator;
    private String remark;
}
