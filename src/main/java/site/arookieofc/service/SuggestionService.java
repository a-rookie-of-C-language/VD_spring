package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.dao.entity.Suggestion;
import site.arookieofc.dao.entity.Suggestion.SuggestionStatus;
import site.arookieofc.dao.mapper.SuggestionMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.dto.SuggestionDTO;
import site.arookieofc.util.PaginationUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionMapper suggestionMapper;
    private final UserMapper userMapper;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * Create a new suggestion
     */
    @Transactional
    public SuggestionDTO createSuggestion(String title, String content, String studentNo) {
        String normalizedTitle = requireText(title, "TITLE_REQUIRED");
        String normalizedContent = requireText(content, "CONTENT_REQUIRED");
        String normalizedStudentNo = requireText(studentNo, "STUDENT_NO_REQUIRED");
        String id = UUID.randomUUID().toString();

        Suggestion suggestion = Suggestion.builder()
                .id(id)
                .title(normalizedTitle)
                .content(normalizedContent)
                .studentNo(normalizedStudentNo)
                .status(SuggestionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        suggestionMapper.insert(suggestion);
        log.info("Created suggestion: {} by student: {}", id, normalizedStudentNo);

        return SuggestionDTO.fromEntity(suggestion, ZONE);
    }

    private String requireText(String value, String errorCode) {
        if (value == null || value.trim().isEmpty()) {
            throw BusinessException.badRequest(errorCode);
        }
        return value.trim();
    }

    /**
     * Get my suggestions with pagination
     */
    public List<SuggestionDTO> getMySuggestions(String studentNo, int page, int pageSize) {
        int safePageSize = PaginationUtils.normalizePageSize(pageSize);
        int offset = PaginationUtils.offset(page, safePageSize);
        List<Suggestion> suggestions = suggestionMapper.listByStudentNo(studentNo, safePageSize, offset);

        return suggestions.stream()
                .map(entity -> SuggestionDTO.fromEntity(entity, ZONE))
                .collect(Collectors.toList());
    }

    /**
     * Count my suggestions
     */
    public int countMySuggestions(String studentNo) {
        return suggestionMapper.countByStudentNo(studentNo);
    }

    /**
     * Get all suggestions (admin) with pagination and optional status filter
     */
    public List<SuggestionDTO> getAllSuggestions(SuggestionStatus status, int page, int pageSize) {
        int safePageSize = PaginationUtils.normalizePageSize(pageSize);
        int offset = PaginationUtils.offset(page, safePageSize);
        List<Suggestion> suggestions = suggestionMapper.listAll(status, safePageSize, offset);
        if (suggestions.isEmpty()) return Collections.emptyList();

        // Batch-load users (1 query instead of N)
        List<String> studentNos = suggestions.stream().map(Suggestion::getStudentNo).distinct().collect(Collectors.toList());
        Map<String, String> nameMap = userMapper.listByStudentNos(studentNos).stream()
                .collect(Collectors.toMap(site.arookieofc.dao.entity.User::getStudentNo, site.arookieofc.dao.entity.User::getUsername, (a, b) -> a));

        return suggestions.stream().map(entity -> {
            SuggestionDTO dto = SuggestionDTO.fromEntity(entity, ZONE);
            dto.setUsername(nameMap.get(entity.getStudentNo()));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Count all suggestions with optional status filter
     */
    public int countAllSuggestions(SuggestionStatus status) {
        return suggestionMapper.countAll(status);
    }

    /**
     * Reply to a suggestion (admin)
     */
    @Transactional
    public SuggestionDTO replySuggestion(String id, String replyContent) {
        Suggestion suggestion = suggestionMapper.getById(id);
        if (suggestion == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }

        String normalizedReplyContent = requireText(replyContent, "REPLY_CONTENT_REQUIRED");
        LocalDateTime replyTime = LocalDateTime.now();
        suggestionMapper.updateReply(id, normalizedReplyContent, replyTime, SuggestionStatus.REPLIED);

        log.info("Replied to suggestion: {}", id);

        // Fetch updated suggestion
        suggestion = suggestionMapper.getById(id);
        SuggestionDTO dto = SuggestionDTO.fromEntity(suggestion, ZONE);

        // Enrich with username
        var user = userMapper.getUserByStudentNo(suggestion.getStudentNo());
        if (user != null) {
            dto.setUsername(user.getUsername());
        }

        return dto;
    }

    /**
     * Get suggestion by ID
     */
    public SuggestionDTO getSuggestionById(String id) {
        Suggestion suggestion = suggestionMapper.getById(id);
        if (suggestion == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }

        SuggestionDTO dto = SuggestionDTO.fromEntity(suggestion, ZONE);

        // Enrich with username
        var user = userMapper.getUserByStudentNo(suggestion.getStudentNo());
        if (user != null) {
            dto.setUsername(user.getUsername());
        }

        return dto;
    }
}
