package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface PendingActivityParticipantRepository {

    @Insert("INSERT INTO pending_activity_participants (pending_activity_id, student_no, duration) " +
            "VALUES (#{pendingActivityId}, #{studentNo}, #{duration})")
    void insert(@Param("pendingActivityId") String pendingActivityId,
                @Param("studentNo") String studentNo,
                @Param("duration") Double duration);

    @Select("SELECT student_no, duration FROM pending_activity_participants WHERE pending_activity_id = #{pendingActivityId}")
    @Results({
        @Result(property = "key", column = "student_no"),
        @Result(property = "value", column = "duration")
    })
    List<Map.Entry<String, Double>> findByPendingActivityId(@Param("pendingActivityId") String pendingActivityId);

    @Delete("DELETE FROM pending_activity_participants WHERE pending_activity_id = #{pendingActivityId}")
    void deleteByPendingActivityId(@Param("pendingActivityId") String pendingActivityId);

    // ...existing code...
}

