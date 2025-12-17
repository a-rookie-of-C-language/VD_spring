package site.arookieofc.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for attachment information returned to frontend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {
    private Long id;
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private String description;
    private String uploadTime;
}

