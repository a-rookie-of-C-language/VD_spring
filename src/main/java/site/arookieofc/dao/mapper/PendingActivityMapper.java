package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.arookieofc.dao.entity.PendingActivity;
import site.arookieofc.service.BO.ActivityType;

import java.util.List;

@Mapper
public interface PendingActivityMapper {
    PendingActivity getById(@Param("id") String id);

    List<PendingActivity> listAll();

    List<PendingActivity> listBySubmitter(@Param("submittedBy") String submittedBy);

    int insert(PendingActivity pendingActivity);

    int delete(@Param("id") String id);

    List<String> selectAttachmentsByPendingActivityId(@Param("pendingActivityId") String pendingActivityId);

    List<String> selectParticipantsByPendingActivityId(@Param("pendingActivityId") String pendingActivityId);

    int deleteAttachmentsByPendingActivityId(@Param("pendingActivityId") String pendingActivityId);

    int deleteParticipantsByPendingActivityId(@Param("pendingActivityId") String pendingActivityId);

    int insertAttachments(@Param("pendingActivityId") String pendingActivityId, @Param("paths") List<String> paths);

    int insertParticipants(@Param("pendingActivityId") String pendingActivityId, @Param("studentNos") List<String> studentNos);

    int countFiltered(@Param("type") ActivityType type,
                      @Param("functionary") String functionary,
                      @Param("name") String name,
                      @Param("submittedBy") String submittedBy);

    List<PendingActivity> listPaged(@Param("type") ActivityType type,
                                    @Param("functionary") String functionary,
                                    @Param("name") String name,
                                    @Param("submittedBy") String submittedBy,
                                    @Param("pageSize") int pageSize,
                                    @Param("offset") int offset);
}

