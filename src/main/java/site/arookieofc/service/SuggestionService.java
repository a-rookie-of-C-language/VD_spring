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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
        String id = UUID.randomUUID().toString();

        Suggestion suggestion = Suggestion.builder()
                .id(id)
                .title(title)
                .content(content)
                .studentNo(studentNo)
                .status(SuggestionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        suggestionMapper.insert(suggestion);
        log.info("Created suggestion: {} by student: {}", id, studentNo);

        return SuggestionDTO.fromEntity(suggestion, ZONE);
    }

    /**
     * Get my suggestions with pagination
     */
    public List<SuggestionDTO> getMySuggestions(String studentNo, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Suggestion> suggestions = suggestionMapper.listByStudentNo(studentNo, pageSize, offset);

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
        int offset = (page - 1) * pageSize;
        List<Suggestion> suggestions = suggestionMapper.listAll(status, pageSize, offset);

        return suggestions.stream()
                .map(entity -> {
                    SuggestionDTO dto = SuggestionDTO.fromEntity(entity, ZONE);
                    // Enrich with username
                    var user = userMapper.getUserByStudentNo(entity.getStudentNo());
                    if (user != null) {
                        dto.setUsername(user.getUsername());
                    }
                    return dto;
                })
                .collect(Collectors.toList());
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

        LocalDateTime replyTime = LocalDateTime.now();
        suggestionMapper.updateReply(id, replyContent, replyTime, SuggestionStatus.REPLIED);

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

