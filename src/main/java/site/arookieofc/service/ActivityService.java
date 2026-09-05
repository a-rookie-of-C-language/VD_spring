package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import site.arookieofc.common.cache.CacheInvalidateEvent;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.messaging.ActivityStartupSynchronizer;
import site.arookieofc.service.messaging.ActivityStatusTaskService;
import site.arookieofc.common.cache.LocalCache;
import site.arookieofc.util.PaginationUtils;
import java.io.IOException;
import java.time.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
public class ActivityService {
    private final ActivityMapper activityMapper;
    private final FileUploadService fileUploadService;
    private final ActivityStatusTaskService activityStatusTaskService;
    private final ApplicationEventPublisher eventPublisher;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    // 5s TTL, 1s null TTL (penetration), ±20% jitter (avalanche)
    private final LocalCache<List<ActivityDTO>> queryCache = new LocalCache<>(5_000, 1_000, 0.2);

    @EventListener
    public void onCacheInvalidate(CacheInvalidateEvent event) {
        if (event.getScope() == CacheInvalidateEvent.Scope.ACTIVITY
                || event.getScope() == CacheInvalidateEvent.Scope.ALL) {
            queryCache.invalidateAll();
        }
    }

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
        Activity activity = activityMapper.getByIdBase(id);
        if (activity == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        return enrichWithCoverImage(activity.toDTO(ZONE));
    }

    // ── Public delegates: hide/include review-ended activities ──

    public int countActivities(ActivityType type, ActivityStatus status,
                               String functionary, String name,
                               OffsetDateTime startFrom, OffsetDateTime startTo,
                               Boolean isFull) {
        return doCount(type, status, functionary, name, startFrom, startTo, isFull, false);
    }

    public int countActivitiesAll(ActivityType type, ActivityStatus status,
                                  String functionary, String name,
                                  OffsetDateTime startFrom, OffsetDateTime startTo,
                                  Boolean isFull) {
        return doCount(type, status, functionary, name, startFrom, startTo, isFull, true);
    }

    public List<ActivityDTO> listActivitiesPaged(ActivityType type, ActivityStatus status,
                                                 String functionary, String name,
                                                 OffsetDateTime startFrom, OffsetDateTime startTo,
                                                 Boolean isFull, int page, int pageSize) {
        return doListPaged(type, status, functionary, name, startFrom, startTo, isFull, false, page, pageSize);
    }

    public List<ActivityDTO> listActivitiesPagedAll(ActivityType type, ActivityStatus status,
                                                    String functionary, String name,
                                                    OffsetDateTime startFrom, OffsetDateTime startTo,
                                                    Boolean isFull, int page, int pageSize) {
        return doListPaged(type, status, functionary, name, startFrom, startTo, isFull, true, page, pageSize);
    }

    public List<ActivityDTO> listActivitiesByCursor(ActivityType type, ActivityStatus status,
                                                    String functionary, String name,
                                                    OffsetDateTime startFrom, OffsetDateTime startTo,
                                                    Boolean isFull,
                                                    OffsetDateTime cursorStartTime, String cursorId,
                                                    int pageSize) {
        return doListByCursor(type, status, functionary, name, startFrom, startTo, isFull, false, cursorStartTime, cursorId, pageSize);
    }

    public List<ActivityDTO> listActivitiesByCursorAll(ActivityType type, ActivityStatus status,
                                                       String functionary, String name,
                                                       OffsetDateTime startFrom, OffsetDateTime startTo,
                                                       Boolean isFull,
                                                       OffsetDateTime cursorStartTime, String cursorId,
                                                       int pageSize) {
        return doListByCursor(type, status, functionary, name, startFrom, startTo, isFull, true, cursorStartTime, cursorId, pageSize);
    }

    // ── Core implementations ──

    private int doCount(ActivityType type, ActivityStatus status, String functionary, String name,
                        OffsetDateTime startFrom, OffsetDateTime startTo, Boolean isFull, boolean includeHidden) {
        boolean excludeHidden = !includeHidden && status == null;
        return activityMapper.countFiltered(type, status, functionary, name,
                toLocal(startFrom), toLocal(startTo), isFull, excludeHidden);
    }

    private List<ActivityDTO> doListPaged(ActivityType type, ActivityStatus status, String functionary, String name,
                                          OffsetDateTime startFrom, OffsetDateTime startTo, Boolean isFull,
                                          boolean includeHidden, int page, int pageSize) {
        int safePage = PaginationUtils.normalizePage(page);
        int safePageSize = PaginationUtils.normalizePageSize(pageSize);
        String cacheKey = cacheKey(type, status, functionary, name, startFrom, startTo, isFull, includeHidden, safePage, safePageSize);
        return queryCache.get(cacheKey, () -> {
            int offset = PaginationUtils.offset(safePage, safePageSize);
            boolean excludeHidden = !includeHidden && status == null;
            return activityMapper.listPaged(type, status, functionary, name,
                            toLocal(startFrom), toLocal(startTo), isFull, excludeHidden, safePageSize, offset)
                    .stream().map(a -> a.toDTO(ZONE)).map(this::enrichWithCoverImage).collect(Collectors.toList());
        });
    }

    private static String cacheKey(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) sb.append(p == null ? "N" : p).append('|');
        return sb.toString();
    }

    private List<ActivityDTO> doListByCursor(ActivityType type, ActivityStatus status, String functionary, String name,
                                             OffsetDateTime startFrom, OffsetDateTime startTo, Boolean isFull,
                                             boolean includeHidden, OffsetDateTime cursorStartTime, String cursorId,
                                             int pageSize) {
        int safePageSize = PaginationUtils.normalizePageSize(
                pageSize, PaginationUtils.DEFAULT_MAX_PAGE_SIZE + 1);
        boolean excludeHidden = !includeHidden && status == null;
        return activityMapper.listByCursor(type, status, functionary, name,
                        toLocal(startFrom), toLocal(startTo), isFull, excludeHidden,
                        toLocal(cursorStartTime), cursorId, safePageSize)
                .stream().map(a -> a.toDTO(ZONE)).map(this::enrichWithCoverImage).collect(Collectors.toList());
    }

    private LocalDateTime toLocal(OffsetDateTime odt) {
        return odt == null ? null : odt.atZoneSameInstant(ZONE).toLocalDateTime();
    }

    @Transactional
    public ActivityDTO createActivity(@Valid ActivityDTO dto) {
        uploadCoverIfPresent(dto);
        dto.setStatus(ActivityStatus.UnderReview);
        String id = dto.getId() == null
                || dto.getId().isEmpty()
                ? UUID.randomUUID().toString()
                : dto.getId();
        Activity entity = dto.toEntity(id, ZONE);
        activityMapper.insert(entity);
        if (dto.getAttachment() != null && !dto.getAttachment().isEmpty()) {
            activityMapper.insertAttachments(id, dto.getAttachment());
        }
        
        List<String> participants = normalizeParticipants(dto.getParticipants(), dto.getFunctionary(), true);
        for (String participant : participants) {
            int exists = activityMapper.existsParticipant(id, participant);
            if (exists == 0) {
                activityMapper.insertParticipant(id, participant);
            }
        }
        eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.ACTIVITY, "create"));
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
        uploadCoverIfPresent(dto);
        dto.setStatus(ActivityStatus.UnderReview);
        dto.setRejectedReason(null);
        dto.setReviewedAt(null);
        dto.setReviewedBy(null);
        Activity entity = dto.toEntity(id, ZONE);

        activityMapper.update(entity);
        if (dto.getAttachment() != null) {
            activityMapper.deleteAttachmentsByActivityId(id);
            if (!dto.getAttachment().isEmpty()) {
                activityMapper.insertAttachments(id, dto.getAttachment());
            }
        }
        if (dto.getParticipants() != null) {
            activityMapper.deleteParticipantsByActivityId(id);
            List<String> participants = normalizeParticipants(dto.getParticipants(), entity.getFunctionary(), true);
            if (!participants.isEmpty()) {
                activityMapper.insertParticipants(id, participants);
            }
        }
        eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.ACTIVITY, "update"));
        return getActivityDTO(id);
    }

    private void uploadCoverIfPresent(ActivityDTO dto) {
        MultipartFile coverFile = dto.getCoverFile();
        if (coverFile == null || coverFile.isEmpty()) {
            return;
        }
        try {
            dto.setCoverPath(fileUploadService.uploadCoverImage(coverFile));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Cover file upload failed: " + e.getMessage(), e);
        }
    }

    private void refreshStatus(Activity a) {
        ActivityStartupSynchronizer.changeStatus(a, ZONE);
    }

    private List<String> normalizeParticipants(List<String> participants, String functionary, boolean includeFunctionary) {
        Set<String> normalized = new LinkedHashSet<>();
        if (includeFunctionary && functionary != null && !functionary.isBlank()) {
            normalized.add(functionary.trim());
        }
        if (participants != null) {
            for (String participant : participants) {
                if (participant != null && !participant.isBlank()) {
                    normalized.add(participant.trim());
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    @Transactional
    public void deleteActivity(String id) {
        activityMapper.deleteAttachmentsByActivityId(id);
        activityMapper.deleteParticipantsByActivityId(id);
        activityMapper.delete(id);
        eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.ACTIVITY, "delete"));
    }

    @Transactional
    public void enroll(String activityId, String studentNo) {
        // SELECT FOR UPDATE serializes concurrent enrollments on the same activity,
        // preventing the TOCTOU race where two threads both pass the capacity check.
        Activity act = activityMapper.selectForUpdate(activityId);
        if (act == null) throw BusinessException.notFound("NOT_FOUND");

        if (activityMapper.existsParticipant(activityId, studentNo) > 0) {
            throw BusinessException.conflict("ALREADY_ENROLLED");
        }
        int cnt = activityMapper.countParticipantsByActivityId(activityId);
        if (act.getMaxParticipant() != null && cnt >= act.getMaxParticipant()) {
            throw BusinessException.conflict("CAPACITY_FULL");
        }
        activityMapper.insertParticipant(activityId, studentNo);

        if (act.getMaxParticipant() != null && cnt + 1 >= act.getMaxParticipant()
                && !Boolean.TRUE.equals(act.getIsFull())) {
            act.setIsFull(true);
            activityMapper.update(act);
        }
        eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.ACTIVITY, "enroll"));
    }

    @Transactional
    public void unenroll(String activityId, String studentNo) {
        Activity act = activityMapper.selectForUpdate(activityId);
        if (act == null) throw BusinessException.notFound("NOT_FOUND");
        if (studentNo.equals(act.getFunctionary())) {
            throw BusinessException.forbidden("FUNCTIONARY_CANNOT_UNENROLL");
        }
        LocalDateTime enrollmentEnd = act.getEnrollmentEndTime();
        if (enrollmentEnd == null || !LocalDateTime.now(ZONE).isBefore(enrollmentEnd)) {
            throw BusinessException.badRequest("ENROLLMENT_ENDED");
        }
        if (activityMapper.existsParticipant(activityId, studentNo) == 0) {
            throw BusinessException.conflict("NOT_ENROLLED");
        }
        activityMapper.deleteParticipant(activityId, studentNo);

        int cnt = activityMapper.countParticipantsByActivityId(activityId);
        if (Boolean.TRUE.equals(act.getIsFull())
                && (act.getMaxParticipant() == null || cnt < act.getMaxParticipant())) {
            act.setIsFull(false);
            activityMapper.update(act);
        }
        eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.ACTIVITY, "unenroll"));
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
            eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.ACTIVITY, "review-reject"));
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
        eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.ACTIVITY, "review-approve"));
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
        if (Duration.between(now, when.atZone(ZONE)).toMillis() <= 0) return;
        activityStatusTaskService.scheduleStatusUpdate(id, status, when, "activity-service");
    }
}
