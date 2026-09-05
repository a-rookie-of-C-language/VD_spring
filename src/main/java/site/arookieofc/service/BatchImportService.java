package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.cache.CacheInvalidateEvent;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.entity.PendingBatchImport;
import site.arookieofc.dao.entity.PendingBatchImportRecord;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.dao.mapper.PendingBatchImportMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.BatchImportRecordDTO;
import site.arookieofc.service.dto.BatchImportResultDTO;
import site.arookieofc.service.dto.PendingBatchImportDTO;
import site.arookieofc.util.PaginationUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchImportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String VALID = "VALID";
    private static final String INVALID = "INVALID";
    private static final Pattern ACTIVITY_SEPARATOR_PATTERN = Pattern.compile("[,;\\r\\n\\uFF0C\\u3001\\uFF1B]+");
    private static final Pattern NORMALIZED_HOUR_SUFFIX_PATTERN = Pattern.compile(
            "^(.+?)(?:[\\(\\[\\{\\uFF08\\u3010]\\s*\\d+(?:\\.\\d+)?\\s*(?:h|H|hours?|\\u5C0F\\u65F6)\\s*[\\)\\]\\}\\uFF09\\u3011])\\s*$");

    private final ExcelParserService excelParserService;
    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;
    private final PendingBatchImportMapper pendingBatchImportMapper;
    private final VolunteerHourGrantService volunteerHourGrantService;
    private final CampusIdentityService campusIdentityService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Two-phase import only: upload -> precheck -> pending review.
     */
    @Transactional
    public BatchImportResultDTO batchImport(MultipartFile file, String operatorStudentNo, boolean isAdmin) {
        return submitForReview(file, operatorStudentNo);
    }

    /**
     * Kept for backward compatibility. Behavior now equals precheck submission.
     */
    @Transactional
    public BatchImportResultDTO directImport(MultipartFile file, String operatorStudentNo) {
        return submitForReview(file, operatorStudentNo);
    }

    @Transactional
    public BatchImportResultDTO submitForReview(MultipartFile file, String submitterStudentNo) {
        List<BatchImportRecordDTO> records = parseRecords(file);
        if (records.isEmpty()) {
            return BatchImportResultDTO.builder()
                    .totalRecords(0)
                    .validRecords(0)
                    .invalidRecords(0)
                    .errors(Collections.singletonList("Excel has no importable data"))
                    .build();
        }

        String batchId = UUID.randomUUID().toString();
        PendingBatchImport pendingBatchImport = PendingBatchImport.builder()
                .id(batchId)
                .submittedBy(submitterStudentNo)
                .originalFilename(file.getOriginalFilename())
                .totalRecords(records.size())
                .createdAt(LocalDateTime.now(ZONE))
                .status("PENDING")
                .build();
        pendingBatchImportMapper.insert(pendingBatchImport);

        List<String> errors = new ArrayList<>();
        List<PendingBatchImportRecord> pendingRecords = new ArrayList<>();
        int validCount = 0;
        int invalidCount = 0;

        for (int i = 0; i < records.size(); i++) {
            BatchImportRecordDTO record = records.get(i);
            int rowNum = i + 3;

            String studentNo = trim(record.getStudentNo());
            String rowError = validateRequiredFields(studentNo, record.getActivityName(), rowNum);
            boolean identityExists = studentNo != null && campusIdentityService.existsByStudentNo(studentNo);
            boolean localUserExists = studentNo != null && userMapper.getUserByStudentNo(studentNo) != null;

            if (rowError == null && !identityExists) {
                rowError = "row " + rowNum + ": studentNo " + studentNo + " does not exist in unified identity";
            }

            List<String> activityNames = splitActivityNames(record.getActivityName());
            if (activityNames.isEmpty()) {
                rowError = rowError == null ? "row " + rowNum + ": activityName is empty" : rowError;
                activityNames = Collections.singletonList("");
            }

            for (String activityName : activityNames) {
                String normalized = normalizeActivityName(activityName);
                String validationStatus = rowError == null ? VALID : INVALID;
                String validationError = rowError == null ? "" : truncate(rowError, 500);

                PendingBatchImportRecord pendingRecord = PendingBatchImportRecord.builder()
                        .batchId(batchId)
                        .username(trim(record.getUsername()))
                        .gender(trim(record.getGender()))
                        .college(trim(record.getCollege()))
                        .grade(trim(record.getGrade()))
                        .studentNo(studentNo)
                        .phone(trim(record.getPhone()))
                        .duration(record.getDuration())
                        .activityName(normalized)
                        .originalActivityName(activityName)
                        .userExists(localUserExists)
                        .validationStatus(validationStatus)
                        .validationError(validationError)
                        .build();
                pendingRecords.add(pendingRecord);

                if (VALID.equals(validationStatus)) {
                    validCount++;
                } else {
                    invalidCount++;
                    errors.add(validationError);
                }
            }
        }

        if (!pendingRecords.isEmpty()) {
            pendingBatchImportMapper.insertRecords(pendingRecords);
        }

        return BatchImportResultDTO.builder()
                .batchId(batchId)
                .totalRecords(records.size())
                .validRecords(validCount)
                .invalidRecords(invalidCount)
                .newUsersCreated(0)
                .newActivitiesCreated(0)
                .participantsAdded(0)
                .hoursGranted(0)
                .createdUserStudentNos(Collections.emptyList())
                .createdActivityNames(Collections.emptyList())
                .errors(errors)
                .build();
    }

    public PendingBatchImportDTO getPendingBatchImport(String batchId) {
        PendingBatchImport pending = pendingBatchImportMapper.getById(batchId);
        if (pending == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }

        PendingBatchImportDTO dto = PendingBatchImportDTO.fromEntity(pending, ZONE);
        List<PendingBatchImportRecord> records = pendingBatchImportMapper.getRecordsByBatchId(batchId);
        dto.setRecords(records.stream()
                .map(PendingBatchImportDTO.PendingBatchImportRecordDTO::fromEntity)
                .collect(Collectors.toList()));
        return dto;
    }

    public List<PendingBatchImportDTO> listPendingBatchImports(String status, String submittedBy, int page, int pageSize) {
        int safePageSize = PaginationUtils.normalizePageSize(pageSize);
        int offset = PaginationUtils.offset(page, safePageSize);
        return pendingBatchImportMapper.listPaged(status, submittedBy, safePageSize, offset).stream()
                .map(entity -> PendingBatchImportDTO.fromEntity(entity, ZONE))
                .collect(Collectors.toList());
    }

    public int countPendingBatchImports(String status, String submittedBy) {
        return pendingBatchImportMapper.countFiltered(status, submittedBy);
    }

    @Transactional
    public BatchImportResultDTO approveBatchImport(String batchId, String reviewerStudentNo) {
        PendingBatchImport pending = pendingBatchImportMapper.getById(batchId);
        if (pending == null) throw BusinessException.notFound("NOT_FOUND");
        if (!"PENDING".equals(pending.getStatus())) throw BusinessException.badRequest("ALREADY_PROCESSED");

        int claimed = pendingBatchImportMapper.updateStatusIfCurrent(
                batchId, "PENDING", "APPROVED", LocalDateTime.now(ZONE), reviewerStudentNo, null);
        if (claimed == 0) throw BusinessException.badRequest("ALREADY_PROCESSED");

        List<PendingBatchImportRecord> allRecords = pendingBatchImportMapper.getRecordsByBatchId(batchId);
        List<PendingBatchImportRecord> validRecords = allRecords.stream()
                .filter(r -> VALID.equalsIgnoreCase(r.getValidationStatus())).collect(Collectors.toList());

        List<String> errors = collectInvalidErrors(allRecords);
        ApprovalCounters counters = processValidRecords(validRecords, reviewerStudentNo, errors);
        refreshMaxParticipant(counters.touchedActivityIds);

        BatchImportResultDTO result = BatchImportResultDTO.builder()
                .batchId(batchId).totalRecords(allRecords.size())
                .validRecords(validRecords.size()).invalidRecords(allRecords.size() - validRecords.size())
                .newUsersCreated(0).newActivitiesCreated(counters.createdActivityNames.size())
                .participantsAdded(counters.participantsAdded).hoursGranted(counters.hoursGranted)
                .createdUserStudentNos(Collections.emptyList())
                .createdActivityNames(new ArrayList<>(counters.createdActivityNames))
                .errors(errors).build();
        eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.ALL, "approve-batch"));
        return result;
    }

    private List<String> collectInvalidErrors(List<PendingBatchImportRecord> allRecords) {
        List<String> errors = new ArrayList<>();
        for (PendingBatchImportRecord r : allRecords) {
            if (!VALID.equalsIgnoreCase(r.getValidationStatus())
                    && r.getValidationError() != null && !r.getValidationError().isBlank()) {
                errors.add(r.getValidationError());
            }
        }
        return errors;
    }

    private ApprovalCounters processValidRecords(List<PendingBatchImportRecord> validRecords,
                                                  String reviewerStudentNo, List<String> errors) {
        ApprovalCounters c = new ApprovalCounters();
        Map<String, String> activityNameToId = new HashMap<>();

        for (PendingBatchImportRecord record : validRecords) {
            String studentNo = trim(record.getStudentNo());
            if (studentNo == null || userMapper.getUserByStudentNo(studentNo) == null) {
                errors.add("studentNo " + studentNo + " not found locally, skipped");
                continue;
            }
            try {
                String activityId = getOrCreateActivity(record, reviewerStudentNo, c.createdActivityNames, activityNameToId);
                c.touchedActivityIds.add(activityId);
                if (activityMapper.existsParticipant(activityId, studentNo) == 0) {
                    activityMapper.insertParticipant(activityId, studentNo);
                    c.participantsAdded++;
                }
                if (record.getDuration() != null && record.getDuration() > 0
                        && volunteerHourGrantService.grantHoursToUser(studentNo, record.getDuration(),
                            VolunteerHourGrantService.SOURCE_IMPORT, activityId, record.getActivityName())) {
                    c.hoursGranted++;
                }
            } catch (RuntimeException e) {
                errors.add("studentNo " + studentNo + " process failed: " + safeMessage(e));
            }
        }
        return c;
    }

    private static class ApprovalCounters {
        final Set<String> createdActivityNames = new HashSet<>();
        final Set<String> touchedActivityIds = new HashSet<>();
        int participantsAdded;
        int hoursGranted;
    }

    @Transactional
    public void rejectBatchImport(String batchId, String reason, String reviewerStudentNo) {
        PendingBatchImport pending = pendingBatchImportMapper.getById(batchId);
        if (pending == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        if (!"PENDING".equals(pending.getStatus())) {
            throw BusinessException.badRequest("ALREADY_PROCESSED");
        }

        int rows = pendingBatchImportMapper.updateStatusIfCurrent(
                batchId, "PENDING", "REJECTED", LocalDateTime.now(ZONE), reviewerStudentNo, reason);
        if (rows == 0) {
            throw BusinessException.badRequest("ALREADY_PROCESSED");
        }
    }

    @Transactional
    public void deletePendingBatchImport(String batchId) {
        pendingBatchImportMapper.deleteRecordsByBatchId(batchId);
        pendingBatchImportMapper.delete(batchId);
    }

    public String normalizeActivityName(String originalName) {
        if (originalName == null) {
            return "";
        }
        String trimmed = originalName.trim();
        Matcher matcher = NORMALIZED_HOUR_SUFFIX_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return trimmed;
    }

    public List<PendingBatchImport> getPendingBatchImportsBySubmitter(String submittedBy) {
        return pendingBatchImportMapper.listBySubmitter(submittedBy);
    }

    public List<PendingBatchImport> getPendingBatchImportsBySubmitterPaged(String submittedBy, String status, int page, int pageSize) {
        int safePageSize = PaginationUtils.normalizePageSize(pageSize);
        int offset = PaginationUtils.offset(page, safePageSize);
        return pendingBatchImportMapper.listPaged(status, submittedBy, safePageSize, offset);
    }

    public int countPendingBatchImportsBySubmitter(String submittedBy, String status) {
        return pendingBatchImportMapper.countFiltered(status, submittedBy);
    }

    private List<BatchImportRecordDTO> parseRecords(MultipartFile file) {
        try {
            return excelParserService.parseBatchImportRecords(file);
        } catch (IOException e) {
            throw BusinessException.badRequest("EXCEL_PARSE_FAILED: " + e.getMessage());
        }
    }

    private String validateRequiredFields(String studentNo, String activityName, int rowNum) {
        if (studentNo == null || studentNo.isBlank()) {
            return "row " + rowNum + ": studentNo is empty";
        }
        if (activityName == null || activityName.isBlank()) {
            return "row " + rowNum + ": activityName is empty";
        }
        return null;
    }

    private List<String> splitActivityNames(String activityName) {
        if (activityName == null || activityName.isBlank()) {
            return Collections.emptyList();
        }
        String[] split = ACTIVITY_SEPARATOR_PATTERN.split(activityName);
        List<String> result = new ArrayList<>();
        for (String item : split) {
            String trimmed = item == null ? "" : item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String getOrCreateActivity(PendingBatchImportRecord record,
                                       String reviewerStudentNo,
                                       Set<String> createdActivityNames,
                                       Map<String, String> activityNameToId) {
        String normalizedActivityName = normalizeActivityName(record.getActivityName());
        String activityId = activityNameToId.get(normalizedActivityName);
        if (activityId != null) {
            return activityId;
        }

        String existingActivityId = activityMapper.getIdByName(normalizedActivityName);
        if (existingActivityId != null && !existingActivityId.isBlank()) {
            activityId = existingActivityId;
            activityNameToId.put(normalizedActivityName, activityId);
            return activityId;
        }

        activityId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZONE);
        Activity newActivity = Activity.builder()
                .id(activityId)
                .name(normalizedActivityName)
                .functionary(reviewerStudentNo)
                .type(ActivityType.COMMUNITY_SERVICE)
                .description("Batch import approved record")
                .enrollmentStartTime(now)
                .enrollmentEndTime(now)
                .startTime(now)
                .expectedEndTime(now)
                .endTime(now)
                .status(ActivityStatus.ActivityEnded)
                .imported(true)
                .isFull(true)
                .maxParticipant(0)
                .duration(record.getDuration() != null ? record.getDuration() : 0.0)
                .reviewedAt(now)
                .reviewedBy(reviewerStudentNo)
                .build();
        activityMapper.insert(newActivity);
        createdActivityNames.add(normalizedActivityName);
        activityNameToId.put(normalizedActivityName, activityId);
        return activityId;
    }

    private void refreshMaxParticipant(Set<String> activityIds) {
        for (String activityId : activityIds) {
            Activity activity = activityMapper.getById(activityId);
            if (activity == null) {
                continue;
            }
            int count = activityMapper.countParticipantsByActivityId(activityId);
            activity.setMaxParticipant(count);
            activityMapper.update(activity);
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
