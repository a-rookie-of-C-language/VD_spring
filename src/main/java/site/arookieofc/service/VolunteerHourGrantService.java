package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.arookieofc.common.exception.BusinessException;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    public static final String SOURCE_ACTIVITY = "ACTIVITY";
    public static final String SOURCE_IMPORT = "BATCH_IMPORT";
    public static final String SOURCE_PERSONAL_REQUEST = "PERSONAL_REQUEST";

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
        String normalizedStudentNo = normalizeStudentNo(studentNo);
        if (normalizedStudentNo == null) {
            log.warn("Invalid studentNo: {}", studentNo);
            return false;
        }

        if (duration == null || duration <= 0) {
            log.warn("Invalid duration for student {}: {}", normalizedStudentNo, duration);
            return false;
        }

        // 检查用户是否存在
        User user = userMapper.getUserByStudentNo(normalizedStudentNo);
        if (user == null) {
            log.error("User not found: {}", normalizedStudentNo);
            return false;
        }

        Double before = user.getTotalHours() != null ? user.getTotalHours() : 0.0;

        VolunteerHourGrantRecord record = VolunteerHourGrantRecord.builder()
                .id(UUID.randomUUID().toString())
                .studentNo(normalizedStudentNo)
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
                    normalizedStudentNo, sourceType, sourceId);
            return false;
        }

        // 原子递增，避免并发下读-改-写覆盖
        int updated = userMapper.incrementTotalHours(normalizedStudentNo, duration);
        if (updated == 0) {
            log.error("Failed to increment total hours for student: {}", normalizedStudentNo);
            throw new IllegalStateException("Failed to increment total hours for student: " + normalizedStudentNo);
        }

        Double after = before + duration;

        // 记录日志
        log.info("Hours granted successfully: student={}, duration={}, before~={}, after~={}, source={}, id={}, name={}",
                normalizedStudentNo, duration, before, after, sourceType, sourceId, sourceName);

        return true;
    }

    /**
     * 活动结束后为所有参与者发放时长
     */
    @Transactional
    public int grantHoursForCompletedActivity(String activityId) {
        Activity activity = requireActivity(activityId);
        if (activity.getStatus() != ActivityStatus.ActivityEnded) {
            log.warn("Activity not ended: {}, status: {}", activityId, activity.getStatus());
            return 0;
        }
        String sourceType = Boolean.TRUE.equals(activity.getImported()) ? SOURCE_IMPORT : SOURCE_ACTIVITY;
        return grantToParticipants(activity.getParticipants(), activity.getDuration(),
                sourceType, activityId, activity.getName());
    }

    /**
     * 个人时长申请通过后发放时长
     */
    @Transactional
    public boolean grantHoursForApprovedRequest(String requestId) {
        PersonalHourRequest request = personalHourRequestMapper.getById(requestId);
        if (request == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        if (request.getStatus() != ActivityStatus.ActivityEnded) {
            log.warn("Request not approved: {}, status: {}", requestId, request.getStatus());
            return false;
        }
        return grantHoursToUser(request.getApplicantStudentNo(), request.getDuration(),
                SOURCE_PERSONAL_REQUEST, requestId, request.getName());
    }

    /**
     * 批量导入活动通过后为所有参与者发放时长
     */
    @Transactional
    public int grantHoursForImportedActivity(String activityId, List<String> participants,
                                             Double duration, String activityName) {
        return grantToParticipants(participants, duration, SOURCE_IMPORT, activityId,
                activityName != null ? activityName : "导入活动");
    }

    // ── Internal helpers ──

    private int grantToParticipants(List<String> participants, Double duration,
                                    String sourceType, String sourceId, String sourceName) {
        List<String> normalizedParticipants = normalizeParticipants(participants);
        if (normalizedParticipants.isEmpty()) return 0;
        if (duration == null || duration <= 0) {
            log.warn("Invalid duration for {}: {}", sourceId, duration);
            return 0;
        }
        int granted = 0;
        for (String studentNo : normalizedParticipants) {
            if (grantHoursToUser(studentNo, duration, sourceType, sourceId, sourceName)) {
                granted++;
            }
        }
        log.info("Hours granted: id={}, name={}, total={}, granted={}, duration={}",
                sourceId, sourceName, normalizedParticipants.size(), granted, duration);
        return granted;
    }

    private List<String> normalizeParticipants(List<String> participants) {
        if (participants == null || participants.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String participant : participants) {
            String studentNo = normalizeStudentNo(participant);
            if (studentNo != null) {
                normalized.add(studentNo);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeStudentNo(String studentNo) {
        if (studentNo == null) {
            return null;
        }
        String trimmed = studentNo.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Activity requireActivity(String activityId) {
        Activity activity = activityMapper.getById(activityId);
        if (activity == null) {
            log.error("Activity not found: {}", activityId);
            throw BusinessException.notFound("NOT_FOUND");
        }
        return activity;
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
