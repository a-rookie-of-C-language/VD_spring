package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.arookieofc.dao.entity.ActivityStatusTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ActivityStatusTaskMapper {
    int insert(ActivityStatusTask task);

    ActivityStatusTask getByEventId(@Param("eventId") String eventId);

    List<ActivityStatusTask> listDispatchable(@Param("limit") int limit,
                                              @Param("now") LocalDateTime now,
                                              @Param("sentBefore") LocalDateTime sentBefore);

    int markSent(@Param("eventId") String eventId,
                 @Param("updatedAt") LocalDateTime updatedAt);

    int markDone(@Param("eventId") String eventId,
                 @Param("updatedAt") LocalDateTime updatedAt);

    int markFailed(@Param("eventId") String eventId,
                   @Param("attempt") int attempt,
                   @Param("lastError") String lastError,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("updatedAt") LocalDateTime updatedAt);

    int markDead(@Param("eventId") String eventId,
                 @Param("attempt") int attempt,
                 @Param("lastError") String lastError,
                 @Param("updatedAt") LocalDateTime updatedAt);

    List<Map<String, Object>> countByStatus();

    int replayDeadTasks(@Param("limit") int limit,
                        @Param("updatedAt") LocalDateTime updatedAt);
}
