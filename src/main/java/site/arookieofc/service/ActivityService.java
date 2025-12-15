package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
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

    public List<ActivityDTO> listActivities() {
        return activityMapper.listAll().stream().map(
                        a -> a
                                .toDTO(ZONE))
                .peek(d -> {
                    if (d.getCoverPath() != null && !d.getCoverPath().isEmpty()) {
                        d.setCoverImage(fileUploadService
                                .readCoverImageAsDataUrl(d.getCoverPath()));
                    }
                }).collect(Collectors.toList());
    }

    public int refreshStatusesAndUpdate() {
        List<Activity> activities = activityMapper.listAll();
        int updated = 0;
        for (Activity a : activities) {
            if (a.getStatus() == ActivityStatus.ActivityEnded || a.getStatus() == ActivityStatus.FailReview || a.getStatus() == ActivityStatus.UnderReview) {
                continue;
            }
            ActivityStatus old = a.getStatus();
            site.arookieofc.service.messaging.ActivityStartupSynchronizer.changeStatus(a, ZONE);
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
                .peek(d -> {
                    if (d.getCoverPath() != null && !d.getCoverPath().isEmpty()) {
                        d.setCoverImage(fileUploadService
                                .readCoverImageAsDataUrl(d.getCoverPath()));
                    }
                }).collect(Collectors.toList());
    }

    public ActivityDTO getActivityById(String id) {
        Activity activity = activityMapper.getById(id);
        if (activity == null) {
            throw new IllegalArgumentException("NOT_FOUND");
        }
        ActivityDTO dto = activity.toDTO(ZONE);
        if (dto.getCoverPath() != null && !dto.getCoverPath().isEmpty()) {
            dto.setCoverImage(fileUploadService.readCoverImageAsDataUrl(dto.getCoverPath()));
        }
        return dto;
    }

    public int countActivities(ActivityType type, ActivityStatus status,
                               String functionary, String name,
                               OffsetDateTime startFrom, OffsetDateTime startTo,
                               Boolean isFull) {
        LocalDateTime sf = startFrom == null ? null : startFrom.atZoneSameInstant(ZONE).toLocalDateTime();
        LocalDateTime st = startTo == null ? null : startTo.atZoneSameInstant(ZONE).toLocalDateTime();
        boolean excludeHidden = (status == null);
        return activityMapper.countFiltered(type, status, functionary, name, sf, st, isFull, excludeHidden);
    }

    public List<ActivityDTO> listActivitiesPaged(ActivityType type, ActivityStatus status,
                                                 String functionary, String name,
                                                 OffsetDateTime startFrom, OffsetDateTime startTo,
                                                 Boolean isFull, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        LocalDateTime sf = startFrom == null
                ? null
                : startFrom.atZoneSameInstant(ZONE).toLocalDateTime();
        LocalDateTime st = startTo == null
                ? null
                : startTo.atZoneSameInstant(ZONE).toLocalDateTime();
        boolean excludeHidden = (status == null);
        return activityMapper.listPaged(
                        type,
                        status,
                        functionary,
                        name,
                        sf,
                        st,
                        isFull,
                        excludeHidden,
                        pageSize,
                        offset
                )
                .stream()
                .map(a -> a.toDTO(ZONE))
                .peek(d -> {
                    if (d.getCoverPath() != null && !d.getCoverPath().isEmpty()) {
                        d.setCoverImage(fileUploadService.readCoverImageAsDataUrl(d.getCoverPath()));
                    }
                }).collect(Collectors.toList());
    }

    public List<ActivityDTO> listActivitiesPagedAll(ActivityType type, ActivityStatus status,
                                                    String functionary, String name,
                                                    OffsetDateTime startFrom, OffsetDateTime startTo,
                                                    Boolean isFull, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        LocalDateTime sf = startFrom == null ? null : startFrom.atZoneSameInstant(ZONE).toLocalDateTime();
        LocalDateTime st = startTo == null ? null : startTo.atZoneSameInstant(ZONE).toLocalDateTime();
        boolean excludeHidden = false;
        return activityMapper.listPaged(
                        type,
                        status,
                        functionary,
                        name,
                        sf,
                        st,
                        isFull,
                        excludeHidden,
                        pageSize,
                        offset
                )
                .stream()
                .map(a -> a.toDTO(ZONE))
                .peek(d -> {
                    if (d.getCoverPath() != null && !d.getCoverPath().isEmpty()) {
                        d.setCoverImage(fileUploadService.readCoverImageAsDataUrl(d.getCoverPath()));
                    }
                }).collect(Collectors.toList());
    }

    public int countActivitiesAll(ActivityType type, ActivityStatus status,
                                  String functionary, String name,
                                  OffsetDateTime startFrom, OffsetDateTime startTo,
                                  Boolean isFull) {
        LocalDateTime sf = startFrom == null ? null : startFrom.atZoneSameInstant(ZONE).toLocalDateTime();
        LocalDateTime st = startTo == null ? null : startTo.atZoneSameInstant(ZONE).toLocalDateTime();
        boolean excludeHidden = false;
        return activityMapper.countFiltered(type, status, functionary, name, sf, st, isFull, excludeHidden);
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
        
        // Automatically add functionary as participant
        String functionary = dto.getFunctionary();
        if (functionary != null && !functionary.isEmpty()) {
            activityMapper.insertParticipant(id, functionary);
        }
        
        // Add other participants (skip functionary if already in list)
        if (dto.getParticipants() != null && !dto.getParticipants().isEmpty()) {
            for (String participant : dto.getParticipants()) {
                if (!participant.equals(functionary)) {
                    // Check if participant already exists to avoid duplicates
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
        ActivityDTO out = created.toDTO(ZONE);
        if (out.getCoverPath() != null && !out.getCoverPath().isEmpty()) {
            out.setCoverImage(fileUploadService.readCoverImageAsDataUrl(out.getCoverPath()));
        }
        return out;
    }

    @Transactional
    public ActivityDTO updateActivity(String id, @Valid ActivityDTO dto) {
        Activity current = activityMapper.getById(id);
        if (current == null) {
            throw new IllegalArgumentException("NOT_FOUND");
        }
        if (current.getStatus() != ActivityStatus.UnderReview && current.getStatus() != ActivityStatus.FailReview) {
            throw new IllegalArgumentException("REVIEW_PASSED");
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
    public String enroll(String activityId, String studentNo) {
        Activity act = activityMapper.getById(activityId);
        if (act == null) {
            return "NOT_FOUND";
        }
        int cnt = activityMapper.countParticipantsByActivityId(activityId);
        if (act.getMaxParticipant() != null && cnt >= act.getMaxParticipant()) {
            return "CAPACITY_FULL";
        }
        int exists = activityMapper.existsParticipant(activityId, studentNo);
        if (exists > 0) {
            return "ALREADY_ENROLLED";
        }
        activityMapper.insertParticipant(activityId, studentNo);
        int after = cnt + 1;
        boolean full = act.getMaxParticipant() != null && after >= act.getMaxParticipant();
        if (full && !Objects.equals(act.getIsFull(), true)) {
            Activity updated = activityMapper.getById(activityId);
            updated.setIsFull(true);
            activityMapper.update(updated);
        }
        return "OK";
    }

    @Transactional
    public String unenroll(String activityId, String studentNo) {
        Activity act = activityMapper.getById(activityId);
        if (act == null) {
            return "NOT_FOUND";
        }
        // Prevent functionary from leaving their own activity
        if (studentNo.equals(act.getFunctionary())) {
            return "FUNCTIONARY_CANNOT_UNENROLL";
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime enrollmentEnd = act.getEnrollmentEndTime();
        if (enrollmentEnd == null || !now.isBefore(enrollmentEnd)) {
            return "ENROLLMENT_ENDED";
        }
        int exists = activityMapper.existsParticipant(activityId, studentNo);
        if (exists == 0) {
            return "NOT_ENROLLED";
        }
        activityMapper.deleteParticipant(activityId, studentNo);
        // Update isFull if needed
        int cnt = activityMapper.countParticipantsByActivityId(activityId);
        boolean full = act.getMaxParticipant() != null && cnt >= act.getMaxParticipant();
        if (Boolean.TRUE.equals(act.getIsFull()) && !full) {
            Activity updated = activityMapper.getById(activityId);
            updated.setIsFull(false);
            activityMapper.update(updated);
        }
        return "OK";
    }

    @Transactional
    public ActivityDTO reviewActivity(String id, boolean approve, String reason) {
        Activity a = activityMapper.getById(id);
        if (a == null) {
            throw new IllegalArgumentException("NOT_FOUND");
        }
        if (!approve) {
            a.setStatus(ActivityStatus.FailReview);
            a.setRejectedReason(reason);
            activityMapper.update(a);
            return getActivityDTO(id);
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime est = a.getEnrollmentStartTime();
        LocalDateTime eet = a.getEnrollmentEndTime();
        if (est == null || eet == null) {
            throw new IllegalArgumentException("INVALID_TIME");
        }
        if (now.isAfter(eet)) {
            throw new IllegalArgumentException("ENROLLMENT_PASSED");
        }
        
        // Clear rejected reason on approval
        a.setRejectedReason(null);
        // Use refreshStatus to set the correct status based on current time
        refreshStatus(a);
        activityMapper.update(a);
        scheduleStatusMessages(a);
        return getActivityDTO(id);
    }

    // status refresh handled by RabbitMQ delayed messages

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
