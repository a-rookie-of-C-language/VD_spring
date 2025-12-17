package site.arookieofc.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import site.arookieofc.dao.entity.Suggestion;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionDTO {
    private String id;
    private String title;
    private String content;
    private String studentNo;
    private String username;
    private Suggestion.SuggestionStatus status;
    private String replyContent;
    private OffsetDateTime replyTime;
    private OffsetDateTime createdAt;

    public static SuggestionDTO fromEntity(Suggestion entity, ZoneId zone) {
        if (entity == null) return null;
        return SuggestionDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .studentNo(entity.getStudentNo())
                .status(entity.getStatus())
                .replyContent(entity.getReplyContent())
                .replyTime(entity.getReplyTime() == null ? null : entity.getReplyTime().atZone(zone).toOffsetDateTime())
                .createdAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atZone(zone).toOffsetDateTime())
                .build();
    }
}

