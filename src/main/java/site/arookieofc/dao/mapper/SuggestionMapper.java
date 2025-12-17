package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.arookieofc.dao.entity.Suggestion;
import site.arookieofc.dao.entity.Suggestion.SuggestionStatus;

import java.util.List;

@Mapper
public interface SuggestionMapper {

    /**
     * Insert a new suggestion
     */
    int insert(Suggestion suggestion);

    /**
     * Get suggestion by ID
     */
    Suggestion getById(@Param("id") String id);

    /**
     * List suggestions by student number with pagination
     */
    List<Suggestion> listByStudentNo(@Param("studentNo") String studentNo,
                                      @Param("pageSize") int pageSize,
                                      @Param("offset") int offset);

    /**
     * Count suggestions by student number
     */
    int countByStudentNo(@Param("studentNo") String studentNo);

    /**
     * List all suggestions with pagination and optional status filter
     */
    List<Suggestion> listAll(@Param("status") SuggestionStatus status,
                              @Param("pageSize") int pageSize,
                              @Param("offset") int offset);

    /**
     * Count all suggestions with optional status filter
     */
    int countAll(@Param("status") SuggestionStatus status);

    /**
     * Update suggestion reply
     */
    int updateReply(@Param("id") String id,
                    @Param("replyContent") String replyContent,
                    @Param("replyTime") java.time.LocalDateTime replyTime,
                    @Param("status") SuggestionStatus status);
}

