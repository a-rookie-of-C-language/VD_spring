package site.arookieofc.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.service.BO.ActivityType;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityImportDTO {
    private String name;
    private ActivityType type;
    private String description;
    private Double duration;
    private OffsetDateTime endTime;
    private String functionary;
    private List<String> participants;
    @JsonIgnore
    private MultipartFile file; // Excel file with participant list
    @JsonIgnore
    private MultipartFile coverFile;
    @JsonProperty("CoverPath")
    private String coverPath;
    @JsonProperty("CoverImage")
    private String coverImage;
    private List<String> attachment;
    @JsonIgnore
    private List<MultipartFile> attachmentFiles;  // 附件文件上传
}

