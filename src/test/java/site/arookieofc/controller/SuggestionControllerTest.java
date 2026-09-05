package site.arookieofc.controller;

import org.junit.jupiter.api.Test;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.service.SuggestionService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SuggestionControllerTest {

    @Test
    void createSuggestionRejectsMissingPrincipalBeforeServiceCall() {
        SuggestionService suggestionService = suggestionService();
        SuggestionController controller = newController(suggestionService);
        SuggestionController.CreateSuggestionRequest request = new SuggestionController.CreateSuggestionRequest();
        request.setTitle("Title");
        request.setContent("Content");

        assertThrows(BusinessException.class, () -> controller.createSuggestion(null, request));

        verify(suggestionService, never()).createSuggestion(anyString(), anyString(), anyString());
    }

    @Test
    void createSuggestionRejectsMissingBodyBeforeServiceCall() {
        SuggestionService suggestionService = suggestionService();
        SuggestionController controller = newController(suggestionService);

        assertThrows(BusinessException.class, () -> controller.createSuggestion(null, null));

        verify(suggestionService, never()).createSuggestion(anyString(), anyString(), anyString());
    }

    @Test
    void getMySuggestionsRejectsMissingPrincipalBeforeServiceCall() {
        SuggestionService suggestionService = suggestionService();
        SuggestionController controller = newController(suggestionService);

        assertThrows(BusinessException.class, () -> controller.getMySuggestions(null, 1, 10));

        verify(suggestionService, never()).countMySuggestions(anyString());
        verify(suggestionService, never()).getMySuggestions(anyString(), anyInt(), anyInt());
    }

    @Test
    void replySuggestionRejectsMissingBodyBeforeServiceCall() {
        SuggestionService suggestionService = suggestionService();
        SuggestionController controller = newController(suggestionService);

        assertThrows(BusinessException.class, () ->
                controller.replySuggestion(new site.arookieofc.security.UserPrincipal("admin", "admin", "Admin"), "s1", null));

        verify(suggestionService, never()).replySuggestion(anyString(), anyString());
    }

    private SuggestionController newController(SuggestionService suggestionService) {
        return new SuggestionController(suggestionService);
    }

    private SuggestionService suggestionService() {
        return mock(SuggestionService.class);
    }
}
