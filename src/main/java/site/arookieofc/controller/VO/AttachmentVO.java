package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VO for attachment upload response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentVO {
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private String description;
}

