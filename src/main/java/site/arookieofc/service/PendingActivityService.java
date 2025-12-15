package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.entity.PendingActivity;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.dao.mapper.PendingActivityMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityImportDTO;
import site.arookieofc.service.dto.PendingActivityDTO;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing pending activities awaiting admin approval
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActivityService {
    private final PendingActivityMapper pendingActivityMapper;
    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;
    private final FileUploadService fileUploadService;
    private final ExcelParserService excelParserService;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * Import activity - creates pending activity for admin review
     * @param dto Import DTO
     * @param submittedBy Student number of submitter
     * @param isAdmin Whether submitter is admin
     * @return Created activity or pending activity ID
     */
    @Transactional
    public String importActivity(ActivityImportDTO dto, String submittedBy, boolean isAdmin) {
        // Parse participants from both array and Excel file
        List<String> allParticipants = new ArrayList<>();

        // Add participants from array
        if (dto.getParticipants() != null && !dto.getParticipants().isEmpty()) {
            allParticipants.addAll(dto.getParticipants());
        }

        // Parse Excel file if provided
        if (dto.getFile() != null && !dto.getFile().isEmpty()) {
            try {
                List<String> excelParticipants = excelParserService.parseStudentNumbers(dto.getFile());
                allParticipants.addAll(excelParticipants);
            } catch (IOException e) {
                log.error("Failed to parse Excel file", e);
                throw new IllegalArgumentException("Failed to parse Excel file: " + e.getMessage());
            }
        }

        // Remove duplicates
        allParticipants = allParticipants.stream()
                .distinct()
                .collect(Collectors.toList());

        // Validate all participants exist
        for (String studentNo : allParticipants) {
            if (userMapper.getUserByStudentNo(studentNo) == null) {
                throw new IllegalArgumentException("User not found: " + studentNo);
            }
        }

        // Upload cover file if provided
        String coverPath = null;
        if (dto.getCoverFile() != null && !dto.getCoverFile().isEmpty()) {
            try {
                coverPath = fileUploadService.uploadCoverImage(dto.getCoverFile());
            } catch (Exception e) {
                log.error("Failed to upload cover image", e);
                throw new IllegalArgumentException("Failed to upload cover image: " + e.getMessage());
            }
        }

        String activityId = UUID.randomUUID().toString();

        // If admin, directly create activity
        if (isAdmin) {
            Activity activity = Activity.builder()
                    .id(activityId)
                    .functionary(dto.getFunctionary())
                    .name(dto.getName())
                    .type(dto.getType())
                    .description(dto.getDescription())
                    .duration(dto.getDuration())
                    .endTime(dto.getEndTime().atZoneSameInstant(ZONE).toLocalDateTime())
                    .coverPath(coverPath)
                    .maxParticipant(allParticipants.size())
                    .status(ActivityStatus.ActivityEnded)
                    .isFull(true)
                    .imported(true)
                    // Set all time fields to endTime for imported activities
                    .enrollmentStartTime(dto.getEndTime().atZoneSameInstant(ZONE).toLocalDateTime())
                    .enrollmentEndTime(dto.getEndTime().atZoneSameInstant(ZONE).toLocalDateTime())
                    .startTime(dto.getEndTime().atZoneSameInstant(ZONE).toLocalDateTime())
                    .expectedEndTime(dto.getEndTime().atZoneSameInstant(ZONE).toLocalDateTime())
                    .build();

            activityMapper.insert(activity);

            // Insert participants
            if (!allParticipants.isEmpty()) {
                activityMapper.insertParticipants(activityId, allParticipants);
            }

            // Insert attachments
            if (dto.getAttachment() != null && !dto.getAttachment().isEmpty()) {
                activityMapper.insertAttachments(activityId, dto.getAttachment());
            }

            log.info("Admin directly imported activity: {}", activityId);
            return activityId;
        } else {
            // For functionary, create pending activity
            PendingActivity pendingActivity = PendingActivity.builder()
                    .id(activityId)
                    .functionary(dto.getFunctionary())
                    .name(dto.getName())
                    .type(dto.getType())
                    .description(dto.getDescription())
                    .duration(dto.getDuration())
                    .endTime(dto.getEndTime().atZoneSameInstant(ZONE).toLocalDateTime())
                    .coverPath(coverPath)
                    .submittedBy(submittedBy)
                    .build();

            pendingActivityMapper.insert(pendingActivity);

            // Insert participants
            if (!allParticipants.isEmpty()) {
                pendingActivityMapper.insertParticipants(activityId, allParticipants);
            }

            // Insert attachments
            if (dto.getAttachment() != null && !dto.getAttachment().isEmpty()) {
                pendingActivityMapper.insertAttachments(activityId, dto.getAttachment());
            }

            log.info("Functionary submitted pending activity: {}", activityId);
            return activityId;
        }
    }

    /**
     * Get pending activity by ID
     */
    public PendingActivityDTO getPendingActivityById(String id) {
        PendingActivity entity = pendingActivityMapper.getById(id);
        if (entity == null) {
            throw new IllegalArgumentException("NOT_FOUND");
        }
        PendingActivityDTO dto = PendingActivityDTO.fromEntity(entity, ZONE);
        if (dto.getCoverPath() != null && !dto.getCoverPath().isEmpty()) {
            dto.setCoverImage(fileUploadService.readCoverImageAsDataUrl(dto.getCoverPath()));
        }
        return dto;
    }

    /**
     * List all pending activities (for admin)
     */
    public List<PendingActivityDTO> listAllPendingActivities() {
        return pendingActivityMapper.listAll().stream()
                .map(entity -> {
                    PendingActivityDTO dto = PendingActivityDTO.fromEntity(entity, ZONE);
                    if (dto.getCoverPath() != null && !dto.getCoverPath().isEmpty()) {
                        dto.setCoverImage(fileUploadService.readCoverImageAsDataUrl(dto.getCoverPath()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * List pending activities with pagination
     */
    public List<PendingActivityDTO> listPendingActivitiesPaged(
            ActivityType type, String functionary, String name, String submittedBy,
            int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return pendingActivityMapper.listPaged(type, functionary, name, submittedBy, pageSize, offset).stream()
                .map(entity -> {
                    PendingActivityDTO dto = PendingActivityDTO.fromEntity(entity, ZONE);
                    if (dto.getCoverPath() != null && !dto.getCoverPath().isEmpty()) {
                        dto.setCoverImage(fileUploadService.readCoverImageAsDataUrl(dto.getCoverPath()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Count pending activities
     */
    public int countPendingActivities(ActivityType type, String functionary, String name, String submittedBy) {
        return pendingActivityMapper.countFiltered(type, functionary, name, submittedBy);
    }

    /**
     * Approve pending activity - convert to actual activity
     */
    @Transactional
    public String approvePendingActivity(String id) {
        PendingActivity pending = pendingActivityMapper.getById(id);
        if (pending == null) {
            throw new IllegalArgumentException("NOT_FOUND");
        }

        // Create actual activity
        Activity activity = Activity.builder()
                .id(pending.getId())
                .functionary(pending.getFunctionary())
                .name(pending.getName())
                .type(pending.getType())
                .description(pending.getDescription())
                .duration(pending.getDuration())
                .endTime(pending.getEndTime())
                .coverPath(pending.getCoverPath())
                .maxParticipant(pending.getParticipants() != null ? pending.getParticipants().size() : 0)
                .status(ActivityStatus.ActivityEnded)
                .isFull(true)
                .imported(true)
                // Set all time fields to endTime for imported activities
                .enrollmentStartTime(pending.getEndTime())
                .enrollmentEndTime(pending.getEndTime())
                .startTime(pending.getEndTime())
                .expectedEndTime(pending.getEndTime())
                .build();

        activityMapper.insert(activity);

        // Copy participants
        if (pending.getParticipants() != null && !pending.getParticipants().isEmpty()) {
            activityMapper.insertParticipants(id, pending.getParticipants());
        }

        // Copy attachments
        if (pending.getAttachment() != null && !pending.getAttachment().isEmpty()) {
            activityMapper.insertAttachments(id, pending.getAttachment());
        }

        // Delete pending activity
        deletePendingActivity(id);

        log.info("Approved pending activity: {}", id);
        return id;
    }

    /**
     * Reject pending activity - delete and clean up
     */
    @Transactional
    public void rejectPendingActivity(String id, String reason) {
        PendingActivity pending = pendingActivityMapper.getById(id);
        if (pending == null) {
            throw new IllegalArgumentException("NOT_FOUND");
        }

        // Delete cover image if exists
        if (pending.getCoverPath() != null && !pending.getCoverPath().isEmpty()) {
            fileUploadService.deleteCoverImage(pending.getCoverPath());
        }

        // Delete pending activity (cascades to participants and attachments)
        deletePendingActivity(id);

        log.info("Rejected pending activity: {} with reason: {}", id, reason);
    }

    /**
     * Delete pending activity and related data
     */
    @Transactional
    public void deletePendingActivity(String id) {
        pendingActivityMapper.deleteParticipantsByPendingActivityId(id);
        pendingActivityMapper.deleteAttachmentsByPendingActivityId(id);
        pendingActivityMapper.delete(id);
    }
}

