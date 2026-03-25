package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.entity.PersonalHourRequest;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.entity.VolunteerHourGrantRecord;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.dao.mapper.PersonalHourRequestMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.dao.mapper.VolunteerHourGrantRecordMapper;
import site.arookieofc.service.BO.ActivityStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 志愿时长发放服务
 * 统一管理所有场景下的志愿时长发放逻辑
 * 使用场景：
 * 1. 活动结束后，为所有参与者发放时长
 * 2. 个人时长申请通过后，为申请人发放时长
 * 3. 后台导入活动通过后，为所有参与者发放时长
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VolunteerHourGrantService {
    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;
    private final PersonalHourRequestMapper personalHourRequestMapper;
    private final VolunteerHourGrantRecordMapper volunteerHourGrantRecordMapper;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 为单个用户发放志愿时长
     *
     * @param studentNo 学号
     * @param duration 时长(小时)
     * @param sourceType 来源类型: ACTIVITY/PERSONAL_REQUEST/IMPORT
     * @param sourceId 来源ID(活动ID/申请ID等)
     * @param sourceName 来源名称(活动名称/申请名称等)
     * @return 是否发放成功
     */
    @Transactional
    public boolean grantHoursToUser(String studentNo, Double duration,
                                   String sourceType, String sourceId, String sourceName) {
        // 参数校验
        if (studentNo == null || studentNo.trim().isEmpty()) {
            log.warn("Invalid studentNo: {}", studentNo);
            return false;
        }

        if (duration == null || duration <= 0) {
            log.warn("Invalid duration for student {}: {}", studentNo, duration);
            return false;
        }

        // 检查用户是否存在
        User user = userMapper.getUserByStudentNo(studentNo);
        if (user == null) {
            log.error("User not found: {}", studentNo);
            return false;
        }

        Double before = user.getTotalHours() != null ? user.getTotalHours() : 0.0;

        VolunteerHourGrantRecord record = VolunteerHourGrantRecord.builder()
                .id(UUID.randomUUID().toString())
                .studentNo(studentNo)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .sourceName(sourceName)
                .duration(duration)
                .grantedAt(LocalDateTime.now(ZONE))
                .build();

        try {
            volunteerHourGrantRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("Duplicate hour grant skipped: student={}, sourceType={}, sourceId={}",
                    studentNo, sourceType, sourceId);
            return false;
        }

        // 原子递增，避免并发下读-改-写覆盖
        int updated = userMapper.incrementTotalHours(studentNo, duration);
        if (updated == 0) {
            log.error("Failed to increment total hours for student: {}", studentNo);
            throw new IllegalStateException("Failed to increment total hours for student: " + studentNo);
        }

        Double after = before + duration;

        // 记录日志
        log.info("Hours granted successfully: student={}, duration={}, before~={}, after~={}, source={}, id={}, name={}",
                studentNo, duration, before, after, sourceType, sourceId, sourceName);

        return true;
    }

    /**
     * 活动结束后为所有参与者发放时长
     *
     * @param activityId 活动ID
     * @return 成功发放的人数
     */
    @Transactional
    public int grantHoursForCompletedActivity(String activityId) {
        Activity activity = activityMapper.getById(activityId);
        if (activity == null) {
            log.error("Activity not found: {}", activityId);
            throw new IllegalArgumentException("NOT_FOUND");
        }

        // 只有已结束的活动才发放时长
        if (activity.getStatus() != ActivityStatus.ActivityEnded) {
            log.warn("Activity not ended yet: {}, current status: {}", activityId, activity.getStatus());
            return 0;
        }

        // 检查活动时长
        Double duration = activity.getDuration();
        if (duration == null || duration <= 0) {
            log.warn("Invalid activity duration: {}, duration: {}", activityId, duration);
            return 0;
        }

        // 获取参与者列表
        List<String> participants = activity.getParticipants();
        if (participants == null || participants.isEmpty()) {
            log.info("No participants for activity: {}", activityId);
            return 0;
        }

        // 为每个参与者发放时长
        int granted = 0;
        String sourceType = activity.getImported() != null && activity.getImported() ? "IMPORT" : "ACTIVITY";

        for (String studentNo : participants) {
            boolean success = grantHoursToUser(
                studentNo,
                duration,
                sourceType,
                activityId,
                activity.getName()
            );
            if (success) {
                granted++;
            }
        }

        log.info("Activity hours granted: activityId={}, activityName={}, totalParticipants={}, granted={}, duration={}",
                activityId, activity.getName(), participants.size(), granted, duration);

        return granted;
    }

    /**
     * 个人时长申请通过后发放时长
     *
     * @param requestId 申请ID
     * @return 是否发放成功
     */
    @Transactional
    public boolean grantHoursForApprovedRequest(String requestId) {
        PersonalHourRequest request = personalHourRequestMapper.getById(requestId);
        if (request == null) {
            log.error("Personal hour request not found: {}", requestId);
            throw new IllegalArgumentException("NOT_FOUND");
        }

        // 只有已通过的申请才发放
        if (request.getStatus() != ActivityStatus.ActivityEnded) {
            log.warn("Request not approved: {}, current status: {}", requestId, request.getStatus());
            return false;
        }

        boolean success = grantHoursToUser(
            request.getApplicantStudentNo(),
            request.getDuration(),
            "PERSONAL_REQUEST",
            requestId,
            request.getName()
        );

        if (success) {
            log.info("Personal request hours granted: requestId={}, requestName={}, applicant={}, duration={}",
                    requestId, request.getName(), request.getApplicantStudentNo(), request.getDuration());
        }

        return success;
    }

    /**
     * 批量导入活动通过后为所有参与者发放时长
     * 此方法主要用于后台直接导入的场景，通常会在导入时直接调用 grantHoursForCompletedActivity
     *
     * @param activityId 导入生成的活动ID
     * @param participants 参与者列表
     * @param duration 活动时长
     * @param activityName 活动名称
     * @return 成功发放的人数
     */
    @Transactional
    public int grantHoursForImportedActivity(String activityId,
                                            List<String> participants,
                                            Double duration,
                                            String activityName) {
        if (participants == null || participants.isEmpty()) {
            log.info("No participants for imported activity: {}", activityId);
            return 0;
        }

        if (duration == null || duration <= 0) {
            log.warn("Invalid imported activity duration: {}, duration: {}", activityId, duration);
            return 0;
        }

        int granted = 0;
        for (String studentNo : participants) {
            boolean success = grantHoursToUser(
                studentNo,
                duration,
                "IMPORT",
                activityId,
                activityName != null ? activityName : "导入活动"
            );
            if (success) {
                granted++;
            }
        }

        log.info("Imported activity hours granted: activityId={}, activityName={}, totalParticipants={}, granted={}, duration={}",
                activityId, activityName, participants.size(), granted, duration);

        return granted;
    }

    /**
     * 检查用户当前总时长
     *
     * @param studentNo 学号
     * @return 用户当前总时长，如果用户不存在返回null
     */
    public Double getUserTotalHours(String studentNo) {
        User user = userMapper.getUserByStudentNo(studentNo);
        if (user == null) {
            return null;
        }
        return user.getTotalHours() != null ? user.getTotalHours() : 0.0;
    }
}

