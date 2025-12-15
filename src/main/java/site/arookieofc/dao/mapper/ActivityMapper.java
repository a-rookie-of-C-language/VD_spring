package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;

import java.util.List;

@Mapper
public interface ActivityMapper {
    Activity getById(@Param("id") String id);

    List<Activity> listAll();

    int insert(Activity activity);

    int update(Activity activity);

    int updateStatus(@Param("id") String id, @Param("status") ActivityStatus status);

    int updateRejectedReason(@Param("id") String id, @Param("reason") String reason);

    int delete(@Param("id") String id);

    List<String> selectAttachmentsByActivityId(@Param("activityId") String activityId);

    List<String> selectParticipantsByActivityId(@Param("activityId") String activityId);

    int deleteAttachmentsByActivityId(@Param("activityId") String activityId);

    int deleteParticipantsByActivityId(@Param("activityId") String activityId);

    int insertAttachments(@Param("activityId") String activityId, @Param("paths") List<String> paths);

    int insertParticipants(@Param("activityId") String activityId, @Param("studentNos") List<String> studentNos);

    int countParticipantsByActivityId(@Param("activityId") String activityId);

    int existsParticipant(@Param("activityId") String activityId, @Param("studentNo") String studentNo);

    int insertParticipant(@Param("activityId") String activityId, @Param("studentNo") String studentNo);

    int deleteParticipant(@Param("activityId") String activityId, @Param("studentNo") String studentNo);

    int countFiltered(@Param("type") ActivityType type,
                      @Param("status") ActivityStatus status,
                      @Param("functionary") String functionary,
                      @Param("name") String name,
                      @Param("startFrom") java.time.LocalDateTime startFrom,
                      @Param("startTo") java.time.LocalDateTime startTo,
                      @Param("isFull") Boolean isFull,
                      @Param("excludeHidden") Boolean excludeHidden);

    List<Activity> listPaged(@Param("type") ActivityType type,
                             @Param("status") ActivityStatus status,
                             @Param("functionary") String functionary,
                             @Param("name") String name,
                             @Param("startFrom") java.time.LocalDateTime startFrom,
                             @Param("startTo") java.time.LocalDateTime startTo,
                             @Param("isFull") Boolean isFull,
                             @Param("excludeHidden") Boolean excludeHidden,
                             @Param("pageSize") int pageSize,
                             @Param("offset") int offset);

    List<Activity> getActivitiesByStudentNo(@Param("studentNo") String studentNo);
    
    /**
     * Count activities by type for distribution chart
     * Returns list of {type, count}
     */
    List<java.util.Map<String, Object>> countByType();
}
