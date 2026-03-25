package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.arookieofc.dao.entity.PersonalHourRequest;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PersonalHourRequestMapper {

    PersonalHourRequest getById(@Param("id") String id);

    List<PersonalHourRequest> listAll();

    List<PersonalHourRequest> listByApplicant(@Param("applicantStudentNo") String applicantStudentNo);

    int insert(PersonalHourRequest request);

    int update(PersonalHourRequest request);

    int updateStatus(@Param("id") String id,
                     @Param("status") ActivityStatus status,
                     @Param("rejectedReason") String rejectedReason,
                     @Param("reviewedAt") LocalDateTime reviewedAt,
                     @Param("reviewedBy") String reviewedBy);

    int updateStatusIfCurrent(@Param("id") String id,
                              @Param("currentStatus") ActivityStatus currentStatus,
                              @Param("status") ActivityStatus status,
                              @Param("rejectedReason") String rejectedReason,
                              @Param("reviewedAt") LocalDateTime reviewedAt,
                              @Param("reviewedBy") String reviewedBy);

    int delete(@Param("id") String id);

    List<String> selectAttachmentsByRequestId(@Param("requestId") String requestId);

    int deleteAttachmentsByRequestId(@Param("requestId") String requestId);

    int insertAttachments(@Param("requestId") String requestId, @Param("paths") List<String> paths);

    int countFiltered(@Param("applicantStudentNo") String applicantStudentNo,
                      @Param("type") ActivityType type,
                      @Param("status") ActivityStatus status,
                      @Param("name") String name);

    List<PersonalHourRequest> listPaged(@Param("applicantStudentNo") String applicantStudentNo,
                                        @Param("type") ActivityType type,
                                        @Param("status") ActivityStatus status,
                                        @Param("name") String name,
                                        @Param("pageSize") int pageSize,
                                        @Param("offset") int offset);

    int countByStatus(@Param("status") ActivityStatus status);
}

