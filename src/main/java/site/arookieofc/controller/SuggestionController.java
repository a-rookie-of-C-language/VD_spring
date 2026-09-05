package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.controller.VO.SuggestionVO;
import site.arookieofc.dao.entity.Suggestion.SuggestionStatus;
import site.arookieofc.security.AuthorizationGuards;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.SuggestionService;
import site.arookieofc.service.dto.SuggestionDTO;
import site.arookieofc.util.PaginationUtils;

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
        if (request == null) {
            throw BusinessException.badRequest("INVALID_REQUEST_BODY");
        }
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw BusinessException.badRequest("Title is required");
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw BusinessException.badRequest("Content is required");
        }

        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        SuggestionDTO dto = suggestionService.createSuggestion(
                request.getTitle(),
                request.getContent(),
                studentNo
        );

        SuggestionVO vo = SuggestionVO.fromDTO(dto);
        return Result.success(vo);
    }

    @GetMapping("/my")
    public Result getMySuggestions(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                    @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        page = PaginationUtils.normalizePage(page);
        pageSize = PaginationUtils.normalizePageSize(pageSize);
        String studentNo = AuthorizationGuards.requireStudentNo(principal);
        int total = suggestionService.countMySuggestions(studentNo);
        List<SuggestionDTO> dtos = suggestionService.getMySuggestions(
                studentNo, page, pageSize);

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
        AuthorizationGuards.requireAdmin(principal);
        page = PaginationUtils.normalizePage(page);
        pageSize = PaginationUtils.normalizePageSize(pageSize);

        SuggestionStatus status = null;
        if (statusStr != null && !statusStr.trim().isEmpty()) {
            try {
                status = SuggestionStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BusinessException.badRequest("Invalid status value. Use PENDING or REPLIED");
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
        AuthorizationGuards.requireAdmin(principal);

        if (request == null) {
            throw BusinessException.badRequest("INVALID_REQUEST_BODY");
        }
        if (request.getReplyContent() == null || request.getReplyContent().trim().isEmpty()) {
            throw BusinessException.badRequest("Reply content is required");
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
