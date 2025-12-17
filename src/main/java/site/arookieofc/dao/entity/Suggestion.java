package site.arookieofc.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Suggestion {
    private String id;
    private String title;
    private String content;
    private String studentNo;
    private SuggestionStatus status;
    private String replyContent;
    private LocalDateTime replyTime;
    private LocalDateTime createdAt;

    public enum SuggestionStatus {
        PENDING,
        REPLIED
    }
}

