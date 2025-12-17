package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import site.arookieofc.dao.entity.Suggestion;
import site.arookieofc.service.dto.SuggestionDTO;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionVO {
    private String id;
    private String title;
    private String content;
    private String studentNo;
    private String username;
    private Suggestion.SuggestionStatus status;
    private String replyContent;
    private OffsetDateTime replyTime;
    private OffsetDateTime createTime;

    public static SuggestionVO fromDTO(SuggestionDTO dto) {
        if (dto == null) return null;
        return SuggestionVO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .content(dto.getContent())
                .studentNo(dto.getStudentNo())
                .username(dto.getUsername())
                .status(dto.getStatus())
                .replyContent(dto.getReplyContent())
                .replyTime(dto.getReplyTime())
                .createTime(dto.getCreatedAt())
                .build();
    }
}

