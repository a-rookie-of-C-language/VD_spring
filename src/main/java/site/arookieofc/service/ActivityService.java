package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.messaging.ActivityStartupSynchronizer;
import site.arookieofc.service.messaging.ActivityStatusUpdateMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.arookieofc.configuration.RabbitConfig;
import java.time.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
public class ActivityService {
    private final ActivityMapper activityMapper;
    private final FileUploadService fileUploadService;
    private final RabbitTemplate rabbitTemplate;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ActivityDTO enrichWithCoverImage(ActivityDTO dto) {
        if (dto.getCoverPath() != null && !dto.getCoverPath().isEmpty()) {
            dto.setCoverImage(fileUploadService.getCoverImageUrl(dto.getCoverPath()));
        }
        return dto;
    }

    public List<ActivityDTO> listActivities() {
        return activityMapper.listAll().stream()
                .map(a -> a.toDTO(ZONE))
                .map(this::enrichWithCoverImage)
                .collect(Collectors.toList());
    }

    public int refreshStatusesAndUpdate() {
        List<Activity> activities = activityMapper.listAllBase();
        int updated = 0;
        for (Activity a : activities) {
            if (a.getStatus() == ActivityStatus.ActivityEnded || a.getStatus() == ActivityStatus.FailReview || a.getStatus() == ActivityStatus.UnderReview) {
                continue;
            }
            ActivityStatus old = a.getStatus();
            ActivityStartupSynchronizer.changeStatus(a, ZONE);
            if (old != a.getStatus()) {
                activityMapper.updateStatus(a.getId(), a.getStatus());
                updated++;
            }
        }
        return updated;
    }

    public List<ActivityDTO> getActivitiesByStudentNo(String studentNo) {
        return activityMapper.getActivitiesByStudentNo(studentNo).stream()
                .map(a -> a.toDTO(ZONE))
                .map(this::enrichWithCoverImage)
                .collect(Collectors.toList());
    }

    public ActivityDTO getActivityById(String id) {
        Activity activity = activityMapper.getById(id);
        if (activity == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        return enrichWithCoverImage(activity.toDTO(ZONE));
    }

    public int countActivities(ActivityType type, ActivityStatus status,
                               String functionary, String name,
                               OffsetDateTime startFrom, OffsetDateTime startTo,
                               Boolean isFull) {
        return countActivities(type, status, functionary, name, startFrom, startTo, isFull, false);
    }

    public int countActivitiesAll(ActivityType type, ActivityStatus status,
                                  String functionary, String name,
                                  OffsetDateTime startFrom, OffsetDateTime startTo,
                                  Boolean isFull) {
        return countActivities(type, status, functionary, name, startFrom, startTo, isFull, true);
    }

    private int countActivities(ActivityType type, ActivityStatus status,
                                String functionary, String name,
                                OffsetDateTime startFrom, OffsetDateTime startTo,
                                Boolean isFull, boolean includeHidden) {
        LocalDateTime sf = startFrom == null ? null : startFrom.atZoneSameInstant(ZONE).toLocalDateTime();
        LocalDateTime st = startTo == null ? null : startTo.atZoneSameInstant(ZONE).toLocalDateTime();
        boolean excludeHidden = includeHidden ? false : (status == null);
        return activityMapper.countFiltered(type, status, functionary, name, sf, st, isFull, excludeHidden);
    }

    public List<ActivityDTO> listActivitiesPaged(ActivityType type, ActivityStatus status,
                                                 String functionary, String name,
                                                 OffsetDateTime startFrom, OffsetDateTime startTo,
                                                 Boolean isFull, int page, int pageSize) {
        return listActivitiesPaged(type, status, functionary, name, startFrom, startTo, isFull, false, page, pageSize);
    }

    public List<ActivityDTO> listActivitiesPagedAll(ActivityType type, ActivityStatus status,
                                                    String functionary, String name,
                                                    OffsetDateTime startFrom, OffsetDateTime startTo,
                                                    Boolean isFull, int page, int pageSize) {
        return listActivitiesPaged(type, status, functionary, name, startFrom, startTo, isFull, true, page, pageSize);
    }

    private List<ActivityDTO> listActivitiesPaged(ActivityType type, ActivityStatus status,
                                                  String functionary, String name,
                                                  OffsetDateTime startFrom, OffsetDateTime startTo,
                                                  Boolean isFull, boolean includeHidden,
                                                  int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        LocalDateTime sf = startFrom == null ? null : startFrom.atZoneSameInstant(ZONE).toLocalDateTime();
        LocalDateTime st = startTo == null ? null : startTo.atZoneSameInstant(ZONE).toLocalDateTime();
        boolean excludeHidden = includeHidden ? false : (status == null);
        return activityMapper.listPaged(type, status, functionary, name, sf, st, isFull, excludeHidden, pageSize, offset)
                .stream()
                .map(a -> a.toDTO(ZONE))
                .map(this::enrichWithCoverImage)
                .collect(Collectors.toList());
    }

    @Transactional
    public ActivityDTO createActivity(@Valid ActivityDTO dto) {
        try {
            MultipartFile coverFile = dto.getCoverFile();
            if (coverFile != null && !coverFile.isEmpty()) {
                String path = fileUploadService.uploadCoverImage(coverFile);
                dto.setCoverPath(path);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cover file upload failed: " + e.getMessage(), e);
        }
        dto.setStatus(ActivityStatus.UnderReview);
        String id = dto.getId() == null
                || dto.getId().isEmpty()
                ? UUID.randomUUID().toString()
                : dto.getId();
        Activity entity = dto.toEntity(id, ZONE);
        activityMapper.insert(entity);
        scheduleStatusMessages(entity);
        if (dto.getAttachment() != null && !dto.getAttachment().isEmpty()) {
            activityMapper.insertAttachments(id, dto.getAttachment());
        }
        
        String functionary = dto.getFunctionary();
        if (functionary != null && !functionary.isEmpty()) {
            activityMapper.insertParticipant(id, functionary);
        }
        
        if (dto.getParticipants() != null && !dto.getParticipants().isEmpty()) {
            for (String participant : dto.getParticipants()) {
                if (!participant.equals(functionary)) {
                    int exists = activityMapper.existsParticipant(id, participant);
                    if (exists == 0) {
                        activityMapper.insertParticipant(id, participant);
                    }
                }
            }
        }
        return getActivityDTO(id);
    }

    private ActivityDTO getActivityDTO(String id) {
        Activity created = activityMapper.getById(id);
        boolean full = created.getMaxParticipant() != null
                && created.getParticipants() != null
                && created.getParticipants().size() >= created.getMaxParticipant();
        if (!Objects.equals(created.getIsFull(), full)) {
            created.setIsFull(full);
            activityMapper.update(created);
            created = activityMapper.getById(id);
        }
        return enrichWithCoverImage(created.toDTO(ZONE));
    }

    @Transactional
    public ActivityDTO updateActivity(String id, @Valid ActivityDTO dto) {
        Activity current = activityMapper.getById(id);
        if (current == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        if (current.getStatus() != ActivityStatus.UnderReview && current.getStatus() != ActivityStatus.FailReview) {
            throw BusinessException.badRequest("REVIEW_PASSED");
        }
        try {
            MultipartFile coverFile = dto.getCoverFile();
            if (coverFile != null && !coverFile.isEmpty()) {
                String path = fileUploadService.uploadCoverImage(coverFile);
                dto.setCoverPath(path);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cover file upload failed: " + e.getMessage(), e);
        }
        Activity entity = dto.toEntity(id, ZONE);

        activityMapper.update(entity);
        scheduleStatusMessages(entity);
        if (dto.getAttachment() != null) {
            activityMapper.deleteAttachmentsByActivityId(id);
            if (!dto.getAttachment().isEmpty()) {
                activityMapper.insertAttachments(id, dto.getAttachment());
            }
        }
        if (dto.getParticipants() != null) {
            activityMapper.deleteParticipantsByActivityId(id);
            if (!dto.getParticipants().isEmpty()) {
                activityMapper.insertParticipants(id, dto.getParticipants());
            }
        }
        return getActivityDTO(id);
    }

    private void refreshStatus(Activity a) {
        ActivityStartupSynchronizer.changeStatus(a, ZONE);
    }

    @Transactional
    public void deleteActivity(String id) {
        activityMapper.deleteAttachmentsByActivityId(id);
        activityMapper.deleteParticipantsByActivityId(id);
        activityMapper.delete(id);
    }

    @Transactional
    public void enroll(String activityId, String studentNo) {
        Activity act = activityMapper.getById(activityId);
        if (act == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        int cnt = activityMapper.countParticipantsByActivityId(activityId);
        if (act.getMaxParticipant() != null && cnt >= act.getMaxParticipant()) {
            throw BusinessException.conflict("CAPACITY_FULL");
        }
        int exists = activityMapper.existsParticipant(activityId, studentNo);
        if (exists > 0) {
            throw BusinessException.conflict("ALREADY_ENROLLED");
        }
        activityMapper.insertParticipant(activityId, studentNo);
        int after = cnt + 1;
        boolean full = act.getMaxParticipant() != null && after >= act.getMaxParticipant();
        if (full && !Objects.equals(act.getIsFull(), true)) {
            Activity updated = activityMapper.getById(activityId);
            updated.setIsFull(true);
            activityMapper.update(updated);
        }
    }

    @Transactional
    public void unenroll(String activityId, String studentNo) {
        Activity act = activityMapper.getById(activityId);
        if (act == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        if (studentNo.equals(act.getFunctionary())) {
            throw BusinessException.forbidden("FUNCTIONARY_CANNOT_UNENROLL");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime enrollmentEnd = act.getEnrollmentEndTime();
        if (enrollmentEnd == null || !now.isBefore(enrollmentEnd)) {
            throw BusinessException.badRequest("ENROLLMENT_ENDED");
        }
        int exists = activityMapper.existsParticipant(activityId, studentNo);
        if (exists == 0) {
            throw BusinessException.conflict("NOT_ENROLLED");
        }
        activityMapper.deleteParticipant(activityId, studentNo);
        int cnt = activityMapper.countParticipantsByActivityId(activityId);
        boolean full = act.getMaxParticipant() != null && cnt >= act.getMaxParticipant();
        if (Boolean.TRUE.equals(act.getIsFull()) && !full) {
            Activity updated = activityMapper.getById(activityId);
            updated.setIsFull(false);
            activityMapper.update(updated);
        }
    }

    @Transactional
    public ActivityDTO reviewActivity(String id, boolean approve, String reason, String reviewerStudentNo) {
        Activity a = activityMapper.getById(id);
        if (a == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        if (a.getStatus() != ActivityStatus.UnderReview) {
            throw BusinessException.badRequest("ALREADY_REVIEWED");
        }

        LocalDateTime reviewedAt = LocalDateTime.now(ZONE);
        if (!approve) {
            int rows = activityMapper.updateStatusIfCurrent(
                    id,
                    ActivityStatus.UnderReview,
                    ActivityStatus.FailReview,
                    reason,
                    reviewedAt,
                    reviewerStudentNo);
            if (rows == 0) {
                throw BusinessException.badRequest("ALREADY_REVIEWED");
            }
            return getActivityDTO(id);
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime est = a.getEnrollmentStartTime();
        LocalDateTime eet = a.getEnrollmentEndTime();
        if (est == null || eet == null) {
            throw BusinessException.badRequest("INVALID_TIME");
        }
        if (now.isAfter(eet)) {
            throw BusinessException.badRequest("ENROLLMENT_PASSED");
        }

        a.setRejectedReason(null);
        a.setReviewedAt(reviewedAt);
        a.setReviewedBy(reviewerStudentNo);
        refreshStatus(a);
        int rows = activityMapper.updateStatusIfCurrent(
                id,
                ActivityStatus.UnderReview,
                a.getStatus(),
                null,
                reviewedAt,
                reviewerStudentNo);
        if (rows == 0) {
            throw BusinessException.badRequest("ALREADY_REVIEWED");
        }
        scheduleStatusMessages(a);
        return getActivityDTO(id);
    }

    private void scheduleStatusMessages(Activity entity) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        scheduleOne(entity.getId(),
                entity.getEnrollmentStartTime(),
                ActivityStatus.EnrollmentStarted,
                now);
        scheduleOne(entity.getId(),
                entity.getEnrollmentEndTime(),
                ActivityStatus.EnrollmentEnded,
                now);
        scheduleOne(entity.getId(),
                entity.getStartTime(),
                ActivityStatus.ActivityStarted,
                now);
        scheduleOne(entity.getId(),
                entity.getEndTime(),
                ActivityStatus.ActivityEnded,
                now);
    }

    private void scheduleOne(String id, LocalDateTime when, ActivityStatus status, ZonedDateTime now) {
        if (when == null) return;
        ZonedDateTime target = when.atZone(ZONE);
        long delayMs = Duration.between(now, target).toMillis();
        if (delayMs <= 0) return;
        ActivityStatusUpdateMessage msg = new ActivityStatusUpdateMessage(id, status);
        MessageProperties props = new MessageProperties();
        props.setHeader("x-delay", delayMs);
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        byte[] body;
        try {
            body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(msg);
        } catch (Exception e) {
            body = (id + "|" + status.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        Message amqpMsg = new Message(body, props);
        rabbitTemplate.send(RabbitConfig.DELAY_EXCHANGE, RabbitConfig.DELAY_ROUTING_KEY, amqpMsg);
    }
}
