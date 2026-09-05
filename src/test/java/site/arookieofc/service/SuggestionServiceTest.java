package site.arookieofc.service;

import org.junit.jupiter.api.Test;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.dao.entity.Suggestion;
import site.arookieofc.dao.mapper.SuggestionMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.dto.SuggestionDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionServiceTest {

    @Test
    void createSuggestionRejectsBlankTitleBeforeInsert() {
        SuggestionMapper suggestionMapper = suggestionMapper();
        SuggestionService service = newService(suggestionMapper);
        String studentNo = "s1";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createSuggestion(" ", "content", studentNo));

        assertEquals("TITLE_REQUIRED", exception.getErrorCode());
        verify(suggestionMapper, never()).insert(any(Suggestion.class));
    }

    @Test
    void createSuggestionRejectsBlankContentBeforeInsert() {
        SuggestionMapper suggestionMapper = suggestionMapper();
        SuggestionService service = newService(suggestionMapper);
        String studentNo = "s1";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createSuggestion("title", "\t", studentNo));

        assertEquals("CONTENT_REQUIRED", exception.getErrorCode());
        verify(suggestionMapper, never()).insert(any(Suggestion.class));
    }

    @Test
    void createSuggestionRejectsBlankStudentNoBeforeInsert() {
        SuggestionMapper suggestionMapper = suggestionMapper();
        SuggestionService service = newService(suggestionMapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createSuggestion("title", "content", ""));

        assertEquals("STUDENT_NO_REQUIRED", exception.getErrorCode());
        verify(suggestionMapper, never()).insert(any(Suggestion.class));
    }

    @Test
    void createSuggestionTrimsInputBeforeInsertAndDtoMapping() {
        SuggestionMapper suggestionMapper = suggestionMapper();
        SuggestionService service = newService(suggestionMapper);
        String studentNo = "s1";

        SuggestionDTO dto = service.createSuggestion("  title  ", "  content  ", "  " + studentNo + "  ");

        verify(suggestionMapper).insert(any(Suggestion.class));
        assertEquals("title", dto.getTitle());
        assertEquals("content", dto.getContent());
        assertEquals(studentNo, dto.getStudentNo());
    }

    @Test
    void replySuggestionRejectsBlankReplyBeforeUpdate() {
        SuggestionMapper suggestionMapper = suggestionMapper();
        SuggestionService service = newService(suggestionMapper);
        String suggestionId = "sg1";
        String studentNo = "s1";
        when(suggestionMapper.getById(suggestionId)).thenReturn(suggestion(suggestionId, studentNo));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replySuggestion(suggestionId, " "));

        assertEquals("REPLY_CONTENT_REQUIRED", exception.getErrorCode());
        verify(suggestionMapper, never()).updateReply(anyString(), anyString(), any(), any());
    }

    @Test
    void replySuggestionTrimsReplyBeforeUpdate() {
        SuggestionMapper suggestionMapper = suggestionMapper();
        String suggestionId = "sg1";
        String studentNo = "s1";
        Suggestion original = suggestion(suggestionId, studentNo);
        Suggestion replied = suggestion(suggestionId, studentNo, "reply", Suggestion.SuggestionStatus.REPLIED);
        when(suggestionMapper.getById(suggestionId)).thenReturn(original, replied);
        SuggestionService service = newService(suggestionMapper);

        SuggestionDTO dto = service.replySuggestion(suggestionId, "  reply  ");

        verify(suggestionMapper).updateReply(eq(suggestionId), eq("reply"), any(), eq(Suggestion.SuggestionStatus.REPLIED));
        assertEquals("reply", dto.getReplyContent());
    }

    private SuggestionMapper suggestionMapper() {
        return mock(SuggestionMapper.class);
    }

    private Suggestion suggestion(String id, String studentNo) {
        return suggestion(id, studentNo, null, null);
    }

    private Suggestion suggestion(String id,
                                  String studentNo,
                                  String replyContent,
                                  Suggestion.SuggestionStatus status) {
        return Suggestion.builder()
                .id(id)
                .studentNo(studentNo)
                .replyContent(replyContent)
                .status(status)
                .build();
    }

    private SuggestionService newService(SuggestionMapper suggestionMapper) {
        return new SuggestionService(suggestionMapper, mock(UserMapper.class));
    }
}
