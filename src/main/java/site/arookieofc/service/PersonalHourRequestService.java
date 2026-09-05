package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.arookieofc.common.cache.CacheInvalidateEvent;
import site.arookieofc.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.dao.entity.PersonalHourRequest;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.PersonalHourRequestMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.PersonalHourRequestDTO;
import site.arookieofc.util.PaginationUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing personal volunteer hour requests
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalHourRequestService {
    private final PersonalHourRequestMapper requestMapper;
    private final UserMapper userMapper;
    private final FileUploadService fileUploadService;
    private final VolunteerHourGrantService volunteerHourGrantService;
    private final ApplicationEventPublisher eventPublisher;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * Submit a new personal hour request
     */
    @Transactional
    public PersonalHourRequestDTO submitRequest(PersonalHourRequestDTO dto, String applicantStudentNo) {
        // Validate applicant exists
        User applicant = userMapper.getUserByStudentNo(applicantStudentNo);
        if (applicant == null) {
            throw BusinessException.badRequest("APPLICANT_NOT_FOUND");
        }

        // Generate ID
        String id = UUID.randomUUID().toString();

        List<String> attachmentPaths = collectAttachmentPaths(dto.getFiles());

        // Create entity
        PersonalHourRequest entity = PersonalHourRequest.builder()
                .id(id)
                .applicantStudentNo(applicantStudentNo)
                .name(dto.getName())
                .functionary(dto.getFunctionary())
                .type(dto.getType())
                .description(dto.getDescription())
                .startTime(dto.getStartTime() == null ? null : dto.getStartTime().atZoneSameInstant(ZONE).toLocalDateTime())
                .endTime(dto.getEndTime() == null ? null : dto.getEndTime().atZoneSameInstant(ZONE).toLocalDateTime())
                .duration(dto.getDuration())
                .status(ActivityStatus.UnderReview)
                .createdAt(LocalDateTime.now(ZONE))
                .build();

        requestMapper.insert(entity);

        // Insert attachments
        if (!attachmentPaths.isEmpty()) {
            requestMapper.insertAttachments(id, attachmentPaths);
        }

        log.info("Personal hour request submitted: {} by {}", id, applicantStudentNo);

        // Return created DTO
        PersonalHourRequest created = requestMapper.getById(id);
        PersonalHourRequestDTO result = PersonalHourRequestDTO.fromEntity(created, ZONE);
        result.setApplicantName(applicant.getUsername());
        return result;
    }

    /**
     * Get request by ID
     */
    public PersonalHourRequestDTO getRequestById(String id) {
        PersonalHourRequest entity = requestMapper.getById(id);
        if (entity == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }
        PersonalHourRequestDTO dto = PersonalHourRequestDTO.fromEntity(entity, ZONE);

        // Get applicant name
        User applicant = userMapper.getUserByStudentNo(entity.getApplicantStudentNo());
        if (applicant != null) {
            dto.setApplicantName(applicant.getUsername());
        }

        return dto;
    }

    /**
     * List requests by applicant (for user's my_requests)
     */
    public List<PersonalHourRequestDTO> listByApplicant(String applicantStudentNo) {
        return enrichWithNames(requestMapper.listByApplicant(applicantStudentNo));
    }

    /**
     * List pending requests (for admin review)
     */
    public List<PersonalHourRequestDTO> listPendingRequests(int page, int pageSize) {
        int safePageSize = PaginationUtils.normalizePageSize(pageSize);
        int offset = PaginationUtils.offset(page, safePageSize);
        return enrichWithNames(requestMapper.listPaged(null, null, ActivityStatus.UnderReview, null, safePageSize, offset));
    }

    /**
     * Count pending requests
     */
    public int countUnderViewRequests() {
        return requestMapper.countByStatus(ActivityStatus.UnderReview);
    }

    /**
     * List requests with filters and pagination
     */
    public List<PersonalHourRequestDTO> listRequestsPaged(
            String applicantStudentNo, ActivityType type, ActivityStatus status, String name,
            int page, int pageSize) {
        int safePageSize = PaginationUtils.normalizePageSize(pageSize);
        int offset = PaginationUtils.offset(page, safePageSize);
        return enrichWithNames(requestMapper.listPaged(applicantStudentNo, type, status, name, safePageSize, offset));
    }

    /**
     * Count requests with filters
     */
    public int countRequests(String applicantStudentNo, ActivityType type, ActivityStatus status, String name) {
        return requestMapper.countFiltered(applicantStudentNo, type, status, name);
    }

    /**
     * Review (approve/reject) a request
     */
    @Transactional
    public PersonalHourRequestDTO reviewRequest(String id, boolean approved, String reason, String reviewerStudentNo) {
        PersonalHourRequest entity = requestMapper.getById(id);
        if (entity == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }

        if (entity.getStatus() != ActivityStatus.UnderReview) {
            throw BusinessException.badRequest("ALREADY_REVIEWED");
        }
        if (!approved && (reason == null || reason.trim().isEmpty())) {
            throw BusinessException.badRequest("REASON_REQUIRED");
        }

        ActivityStatus newStatus = approved ? ActivityStatus.ActivityEnded : ActivityStatus.FailReview;
        String rejectedReason = approved ? null : reason;
        LocalDateTime reviewedAt = LocalDateTime.now(ZONE);

        int rows = requestMapper.updateStatusIfCurrent(
                id,
                ActivityStatus.UnderReview,
                newStatus,
                rejectedReason,
                reviewedAt,
                reviewerStudentNo);
        if (rows == 0) {
            throw BusinessException.badRequest("ALREADY_REVIEWED");
        }

        // If approved, add hours to user's total using unified grant service
        if (approved) {
            volunteerHourGrantService.grantHoursForApprovedRequest(id);
        }

        log.info("Personal hour request {} {} by {}", id, approved ? "approved" : "rejected", reviewerStudentNo);

        // Return updated DTO
        PersonalHourRequest updated = requestMapper.getById(id);
        PersonalHourRequestDTO dto = PersonalHourRequestDTO.fromEntity(updated, ZONE);
        User applicant = userMapper.getUserByStudentNo(updated.getApplicantStudentNo());
        if (applicant != null) {
            dto.setApplicantName(applicant.getUsername());
        }
        eventPublisher.publishEvent(new CacheInvalidateEvent(this, CacheInvalidateEvent.Scope.MONITORING, "review-hour-request"));
        return dto;
    }

    /**
     * Delete a request (only if pending, by the applicant)
     */
    @Transactional
    public void deleteRequest(String id, String applicantStudentNo) {
        PersonalHourRequest entity = requestMapper.getById(id);
        if (entity == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }

        // Only allow applicant to delete their own pending request
        if (!entity.getApplicantStudentNo().equals(applicantStudentNo)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }

        if (entity.getStatus() != ActivityStatus.UnderReview) {
            throw BusinessException.badRequest("CANNOT_DELETE_REVIEWED");
        }

        // Delete attachments files
        if (entity.getAttachments() != null) {
            for (String path : entity.getAttachments()) {
                deleteAttachment(path);
            }
        }

        // Delete from database
        requestMapper.deleteAttachmentsByRequestId(id);
        requestMapper.delete(id);

        log.info("Personal hour request deleted: {} by {}", id, applicantStudentNo);
    }

    /**
     * Delete an attachment file
     */
    private boolean deleteAttachment(String relativePath) {
        return fileUploadService.deleteAttachment(relativePath);
    }

    private List<String> collectAttachmentPaths(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                paths.add(fileUploadService.uploadAttachment(file));
            } catch (IOException e) {
                log.error("Failed to upload attachment", e);
                throw new IllegalArgumentException("Failed to upload attachment: " + e.getMessage(), e);
            }
        }
        return paths;
    }

    /**
     * Batch-load users (1 query) and enrich DTOs with applicant names.
     * Replaces N+1 per-row getUserByStudentNo calls.
     */
    private List<PersonalHourRequestDTO> enrichWithNames(List<PersonalHourRequest> entities) {
        if (entities.isEmpty()) return java.util.Collections.emptyList();
        List<String> studentNos = entities.stream().map(PersonalHourRequest::getApplicantStudentNo).distinct().collect(Collectors.toList());
        Map<String, String> nameMap = userMapper.listByStudentNos(studentNos).stream()
                .collect(Collectors.toMap(User::getStudentNo, User::getUsername, (a, b) -> a));
        return entities.stream().map(entity -> {
            PersonalHourRequestDTO dto = PersonalHourRequestDTO.fromEntity(entity, ZONE);
            dto.setApplicantName(nameMap.get(entity.getApplicantStudentNo()));
            return dto;
        }).collect(Collectors.toList());
    }
}
