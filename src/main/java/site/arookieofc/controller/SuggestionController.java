package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.controller.VO.SuggestionVO;
import site.arookieofc.dao.entity.Suggestion.SuggestionStatus;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.SuggestionService;
import site.arookieofc.service.dto.SuggestionDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @PostMapping
    public Result createSuggestion(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody CreateSuggestionRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            return Result.of(400, "Title is required", null);
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Result.of(400, "Content is required", null);
        }

        SuggestionDTO dto = suggestionService.createSuggestion(
                request.getTitle(),
                request.getContent(),
                principal.getStudentNo()
        );

        SuggestionVO vo = SuggestionVO.fromDTO(dto);
        return Result.success(vo);
    }

    @GetMapping("/my")
    public Result getMySuggestions(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                    @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        int total = suggestionService.countMySuggestions(principal.getStudentNo());
        List<SuggestionDTO> dtos = suggestionService.getMySuggestions(
                principal.getStudentNo(), page, pageSize);

        List<SuggestionVO> items = dtos.stream()
                .map(SuggestionVO::fromDTO)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);

        return Result.success(data);
    }

    @GetMapping
    public Result getAllSuggestions(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                     @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
                                     @RequestParam(value = "status", required = false) String statusStr) {
        String role = principal.getRole();
        if (!"admin".equals(role) && !"superAdmin".equals(role)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }

        SuggestionStatus status = null;
        if (statusStr != null && !statusStr.trim().isEmpty()) {
            try {
                status = SuggestionStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Result.of(400, "Invalid status value. Use PENDING or REPLIED", null);
            }
        }

        int total = suggestionService.countAllSuggestions(status);
        List<SuggestionDTO> dtos = suggestionService.getAllSuggestions(status, page, pageSize);

        List<SuggestionVO> items = dtos.stream()
                .map(SuggestionVO::fromDTO)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);

        return Result.success(data);
    }

    @PostMapping("/{id}/reply")
    public Result replySuggestion(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable("id") String id,
                                  @RequestBody ReplySuggestionRequest request) {
        String role = principal.getRole();
        if (!"admin".equals(role) && !"superAdmin".equals(role)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }

        if (request.getReplyContent() == null || request.getReplyContent().trim().isEmpty()) {
            return Result.of(400, "Reply content is required", null);
        }

        SuggestionDTO dto = suggestionService.replySuggestion(id, request.getReplyContent());
        SuggestionVO vo = SuggestionVO.fromDTO(dto);
        return Result.success(vo);
    }

    @lombok.Data
    public static class CreateSuggestionRequest {
        private String title;
        private String content;
    }

    @lombok.Data
    public static class ReplySuggestionRequest {
        private String replyContent;
    }
}
