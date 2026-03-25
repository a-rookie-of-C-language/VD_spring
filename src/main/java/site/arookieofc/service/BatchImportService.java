package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.arookieofc.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import site.arookieofc.dao.entity.Activity;
import site.arookieofc.dao.entity.PendingBatchImport;
import site.arookieofc.dao.entity.PendingBatchImportRecord;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.dao.mapper.PendingBatchImportMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.BO.Role;
import site.arookieofc.service.dto.BatchImportRecordDTO;
import site.arookieofc.service.dto.BatchImportResultDTO;
import site.arookieofc.service.dto.PendingBatchImportDTO;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 批量导入服务
 * 处理Excel表格中的用户和活动数据导入
 * 字段：姓名、性别、学院、年级、学号、联系方式、服务时长（小时）、活动名称
 *
 * 支持两种模式：
 * 1. 管理员直接导入：直接创建用户、活动、发放时长
 * 2. 非管理员提交审核：先创建待审核记录，管理员审核通过后才生效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchImportService {
    private final ExcelParserService excelParserService;
    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;
    private final PendingBatchImportMapper pendingBatchImportMapper;
    private final VolunteerHourGrantService volunteerHourGrantService;
    private final PasswordEncoder passwordEncoder;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    // 匹配活动名称后面的小时数，如 "xxx(2小时)" 或 "xxx（2小时）" 或 "xxx(2.5小时)"
    private static final Pattern HOUR_SUFFIX_PATTERN = Pattern.compile("(.+?)[（(]\\s*[\\d.]+\\s*小时\\s*[)）]\\s*$");

    /**
     * 批量导入Excel数据（根据角色判断是直接导入还是提交审核）
     *
     * @param file Excel文件
     * @param operatorStudentNo 操作者学号
     * @param isAdmin 是否为管理员
     * @return 导入结果
     */
    @Transactional
    public BatchImportResultDTO batchImport(MultipartFile file, String operatorStudentNo, boolean isAdmin) {
        if (isAdmin) {
            // 管理员直接导入
            return directImport(file, operatorStudentNo);
        } else {
            // 非管理员提交审核
            return submitForReview(file, operatorStudentNo);
        }
    }

    /**
     * 管理员直接导入（原有逻辑）
     */
    @Transactional
    public BatchImportResultDTO directImport(MultipartFile file, String operatorStudentNo) {
        List<String> errors = new ArrayList<>();
        Set<String> createdUserStudentNos = new HashSet<>();
        Set<String> createdActivityNames = new HashSet<>();
        int participantsAdded = 0;
        int hoursGranted = 0;

        // 1. 解析Excel文件
        List<BatchImportRecordDTO> records;
        try {
            records = excelParserService.parseBatchImportRecords(file);
        } catch (IOException e) {
            log.error("Failed to parse Excel file", e);
            return BatchImportResultDTO.builder()
                    .totalRecords(0)
                    .errors(Collections.singletonList("Excel文件解析失败: " + e.getMessage()))
                    .build();
        }

        if (records.isEmpty()) {
            return BatchImportResultDTO.builder()
                    .totalRecords(0)
                    .errors(Collections.singletonList("Excel文件中没有有效数据"))
                    .build();
        }

        log.info("Parsed {} records from Excel file", records.size());

        // 2. 构建活动名称映射（去重处理）
        // Key: 规范化后的活动名称, Value: 活动ID
        Map<String, String> activityNameToId = new HashMap<>();

        // 3. 处理每条记录
        for (int i = 0; i < records.size(); i++) {
            BatchImportRecordDTO record = records.get(i);
            int rowNum = i + 3; // Excel行号（第1,2行是表头，从第3行开始）

            try {
                // 3.1 处理用户
                String studentNo = record.getStudentNo();
                if (studentNo == null || studentNo.isEmpty()) {
                    errors.add("第" + rowNum + "行: 学号不能为空");
                    continue;
                }

                User existingUser = userMapper.getUserByStudentNo(studentNo);
                if (existingUser == null) {
                    // 创建新用户
                    User newUser = User.builder()
                            .studentNo(studentNo)
                            .username(record.getUsername())
                            .password(passwordEncoder.encode(studentNo))
                            .role(Role.user)
                            .totalHours(0.0)
                            .createdAt(LocalDateTime.now())
                            .college(record.getCollege())
                            .grade(record.getGrade())
                            .clazz(null) // Excel中没有班级字段
                            .build();

                    userMapper.insertUser(newUser);
                    createdUserStudentNos.add(studentNo);
                    log.info("Created new user: {}", studentNo);
                }

                // 3.2 处理活动（支持多个活动，逗号分割）
                String originalActivityName = record.getActivityName();
                if (originalActivityName == null || originalActivityName.isEmpty()) {
                    errors.add("第" + rowNum + "行: 活动名称不能为空");
                    continue;
                }

                // 分割多个活动名称（按逗号）
                String[] activityNames = originalActivityName.split("[,，]");

                // 处理每个活动
                for (String activityNameItem : activityNames) {
                    String trimmedActivityName = activityNameItem.trim();
                    if (trimmedActivityName.isEmpty()) {
                        continue;
                    }

                    // 规范化活动名称（去除后面的小时数）
                    String normalizedActivityName = normalizeActivityName(trimmedActivityName);

                    String activityId = activityNameToId.get(normalizedActivityName);

                    if (activityId == null) {
                        // 检查数据库中是否存在该活动
                        Activity existingActivity = activityMapper.getByName(normalizedActivityName);

                        if (existingActivity != null) {
                            activityId = existingActivity.getId();
                        } else {
                            // 创建新活动
                            activityId = UUID.randomUUID().toString();
                            LocalDateTime now = LocalDateTime.now();

                            Activity newActivity = Activity.builder()
                                    .id(activityId)
                                    .name(normalizedActivityName)
                                    .functionary(operatorStudentNo)
                                    .type(ActivityType.COMMUNITY_SERVICE) // 默认类型
                                    .description("通过Excel批量导入创建")
                                    .enrollmentStartTime(now)
                                    .enrollmentEndTime(now)
                                    .startTime(now)
                                    .expectedEndTime(now)
                                    .endTime(now)
                                    .status(ActivityStatus.ActivityEnded)
                                    .imported(true)
                                    .isFull(true)
                                    .maxParticipant(0) // 将在最后更新
                                    .duration(record.getDuration() != null ? record.getDuration() : 0.0)
                                    .build();

                            activityMapper.insert(newActivity);
                            createdActivityNames.add(normalizedActivityName);
                            log.info("Created new activity: {} (id: {})", normalizedActivityName, activityId);
                        }

                        activityNameToId.put(normalizedActivityName, activityId);
                    }

                    // 3.3 添加参与关系
                    int exists = activityMapper.existsParticipant(activityId, studentNo);
                    if (exists == 0) {
                        activityMapper.insertParticipant(activityId, studentNo);
                        participantsAdded++;
                        log.debug("Added participant {} to activity {}", studentNo, activityId);
                    }

                    // 3.4 发放时长（每个活动都发放）
                    Double duration = record.getDuration();
                    if (duration != null && duration > 0) {
                        boolean granted = volunteerHourGrantService.grantHoursToUser(
                                studentNo,
                                duration,
                                VolunteerHourGrantService.SOURCE_IMPORT,
                                activityId,
                                normalizedActivityName
                        );
                        if (granted) {
                            hoursGranted++;
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Error processing row {}: {}", rowNum, e.getMessage(), e);
                errors.add("第" + rowNum + "行处理失败: " + e.getMessage());
            }
        }

        // 4. 更新活动的maxParticipant字段
        for (String activityId : activityNameToId.values()) {
            int count = activityMapper.countParticipantsByActivityId(activityId);
            Activity activity = activityMapper.getById(activityId);
            if (activity != null) {
                activity.setMaxParticipant(count);
                activityMapper.update(activity);
            }
        }

        // 5. 构建返回结果
        return BatchImportResultDTO.builder()
                .totalRecords(records.size())
                .newUsersCreated(createdUserStudentNos.size())
                .newActivitiesCreated(createdActivityNames.size())
                .participantsAdded(participantsAdded)
                .hoursGranted(hoursGranted)
                .createdUserStudentNos(new ArrayList<>(createdUserStudentNos))
                .createdActivityNames(new ArrayList<>(createdActivityNames))
                .errors(errors)
                .build();
    }

    /**
     * 非管理员提交审核
     */
    @Transactional
    public BatchImportResultDTO submitForReview(MultipartFile file, String submitterStudentNo) {
        List<String> errors = new ArrayList<>();

        // 1. 解析Excel文件
        List<BatchImportRecordDTO> records;
        try {
            records = excelParserService.parseBatchImportRecords(file);
        } catch (IOException e) {
            log.error("Failed to parse Excel file", e);
            return BatchImportResultDTO.builder()
                    .totalRecords(0)
                    .errors(Collections.singletonList("Excel文件解析失败: " + e.getMessage()))
                    .build();
        }

        if (records.isEmpty()) {
            return BatchImportResultDTO.builder()
                    .totalRecords(0)
                    .errors(Collections.singletonList("Excel文件中没有有效数据"))
                    .build();
        }

        // 2. 创建待审核主记录
        String batchId = UUID.randomUUID().toString();
        PendingBatchImport pendingBatchImport = PendingBatchImport.builder()
                .id(batchId)
                .submittedBy(submitterStudentNo)
                .originalFilename(file.getOriginalFilename())
                .totalRecords(records.size())
                .createdAt(LocalDateTime.now())
                .status("PENDING")
                .build();

        pendingBatchImportMapper.insert(pendingBatchImport);

        // 3. 创建待审核详情记录
        List<PendingBatchImportRecord> pendingRecords = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            BatchImportRecordDTO record = records.get(i);
            int rowNum = i + 3;

            String studentNo = record.getStudentNo();
            if (studentNo == null || studentNo.isEmpty()) {
                errors.add("第" + rowNum + "行: 学号不能为空");
                continue;
            }

            String originalActivityName = record.getActivityName();
            if (originalActivityName == null || originalActivityName.isEmpty()) {
                errors.add("第" + rowNum + "行: 活动名称不能为空");
                continue;
            }

            // 分割多个活动名称（按逗号）
            String[] activityNames = originalActivityName.split("[,，]");
            boolean userExists = userMapper.getUserByStudentNo(studentNo) != null;

            // 为每个活动创建一条待审核记录
            for (String activityNameItem : activityNames) {
                String trimmedActivityName = activityNameItem.trim();
                if (trimmedActivityName.isEmpty()) {
                    continue;
                }

                String normalizedActivityName = normalizeActivityName(trimmedActivityName);

                PendingBatchImportRecord pendingRecord = PendingBatchImportRecord.builder()
                        .batchId(batchId)
                        .username(record.getUsername())
                        .gender(record.getGender())
                        .college(record.getCollege())
                        .grade(record.getGrade())
                        .studentNo(studentNo)
                        .phone(record.getPhone())
                        .duration(record.getDuration())
                        .activityName(normalizedActivityName)
                        .originalActivityName(trimmedActivityName)
                        .userExists(userExists)
                        .build();

                pendingRecords.add(pendingRecord);
            }
        }

        if (!pendingRecords.isEmpty()) {
            pendingBatchImportMapper.insertRecords(pendingRecords);
        }

        log.info("Submitted batch import for review: batchId={}, records={}, submitter={}",
                batchId, pendingRecords.size(), submitterStudentNo);

        return BatchImportResultDTO.builder()
                .batchId(batchId)
                .totalRecords(records.size())
                .newUsersCreated(0)
                .newActivitiesCreated(0)
                .participantsAdded(0)
                .hoursGranted(0)
                .createdUserStudentNos(Collections.emptyList())
                .createdActivityNames(Collections.emptyList())
                .errors(errors)
                .build();
    }

    /**
     * 获取待审核批量导入详情
     */
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

    /**
     * 获取待审核批量导入列表（分页）
     */
    public List<PendingBatchImportDTO> listPendingBatchImports(String status, String submittedBy, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return pendingBatchImportMapper.listPaged(status, submittedBy, pageSize, offset).stream()
                .map(entity -> PendingBatchImportDTO.fromEntity(entity, ZONE))
                .collect(Collectors.toList());
    }

    /**
     * 统计待审核批量导入数量
     */
    public int countPendingBatchImports(String status, String submittedBy) {
        return pendingBatchImportMapper.countFiltered(status, submittedBy);
    }

    /**
     * 审核通过批量导入
     */
    @Transactional
    public BatchImportResultDTO approveBatchImport(String batchId, String reviewerStudentNo) {
        PendingBatchImport pending = pendingBatchImportMapper.getById(batchId);
        if (pending == null) {
            throw BusinessException.notFound("NOT_FOUND");
        }

        if (!"PENDING".equals(pending.getStatus())) {
            throw BusinessException.badRequest("ALREADY_PROCESSED");
        }

        LocalDateTime reviewedAt = LocalDateTime.now();
        int claimed = pendingBatchImportMapper.updateStatusIfCurrent(
                batchId, "PENDING", "APPROVED", reviewedAt, reviewerStudentNo, null);
        if (claimed == 0) {
            throw BusinessException.badRequest("ALREADY_PROCESSED");
        }

        List<PendingBatchImportRecord> records = pendingBatchImportMapper.getRecordsByBatchId(batchId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("批次记录为空");
        }

        List<String> errors = new ArrayList<>();
        Set<String> createdUserStudentNos = new HashSet<>();
        Set<String> createdActivityNames = new HashSet<>();
        int participantsAdded = 0;
        int hoursGranted = 0;

        Map<String, String> activityNameToId = new HashMap<>();

        for (PendingBatchImportRecord record : records) {
            try {
                String studentNo = record.getStudentNo();

                // 1. 处理用户
                User existingUser = userMapper.getUserByStudentNo(studentNo);
                if (existingUser == null) {
                    User newUser = User.builder()
                            .studentNo(studentNo)
                            .username(record.getUsername())
                            .password(passwordEncoder.encode(studentNo))
                            .role(Role.user)
                            .totalHours(0.0)
                            .createdAt(LocalDateTime.now())
                            .college(record.getCollege())
                            .grade(record.getGrade())
                            .clazz(null)
                            .build();

                    userMapper.insertUser(newUser);
                    createdUserStudentNos.add(studentNo);
                    log.info("Created new user during approval: {}", studentNo);
                }

                // 2. 处理活动
                String normalizedActivityName = record.getActivityName();
                String activityId = activityNameToId.get(normalizedActivityName);

                if (activityId == null) {
                    Activity existingActivity = activityMapper.getByName(normalizedActivityName);

                    if (existingActivity != null) {
                        activityId = existingActivity.getId();
                    } else {
                        activityId = UUID.randomUUID().toString();
                        LocalDateTime now = LocalDateTime.now();

                        Activity newActivity = Activity.builder()
                                .id(activityId)
                                .name(normalizedActivityName)
                                .functionary(reviewerStudentNo)
                                .type(ActivityType.COMMUNITY_SERVICE)
                                .description("通过Excel批量导入创建（审核通过）")
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
                        log.info("Created new activity during approval: {} (id: {})", normalizedActivityName, activityId);
                    }

                    activityNameToId.put(normalizedActivityName, activityId);
                }

                // 3. 添加参与关系
                int exists = activityMapper.existsParticipant(activityId, studentNo);
                if (exists == 0) {
                    activityMapper.insertParticipant(activityId, studentNo);
                    participantsAdded++;
                }

                // 4. 发放时长
                Double duration = record.getDuration();
                if (duration != null && duration > 0) {
                    boolean granted = volunteerHourGrantService.grantHoursToUser(
                            studentNo, duration, VolunteerHourGrantService.SOURCE_IMPORT, activityId, normalizedActivityName);
                    if (granted) {
                        hoursGranted++;
                    }
                }

            } catch (Exception e) {
                log.error("Error processing record for student {}: {}", record.getStudentNo(), e.getMessage(), e);
                errors.add("学号" + record.getStudentNo() + "处理失败: " + e.getMessage());
            }
        }

        // 更新活动的maxParticipant字段
        for (String activityId : activityNameToId.values()) {
            int count = activityMapper.countParticipantsByActivityId(activityId);
            Activity activity = activityMapper.getById(activityId);
            if (activity != null) {
                activity.setMaxParticipant(count);
                activityMapper.update(activity);
            }
        }

        log.info("Approved batch import: batchId={}, reviewer={}", batchId, reviewerStudentNo);

        return BatchImportResultDTO.builder()
                .totalRecords(records.size())
                .newUsersCreated(createdUserStudentNos.size())
                .newActivitiesCreated(createdActivityNames.size())
                .participantsAdded(participantsAdded)
                .hoursGranted(hoursGranted)
                .createdUserStudentNos(new ArrayList<>(createdUserStudentNos))
                .createdActivityNames(new ArrayList<>(createdActivityNames))
                .errors(errors)
                .build();
    }

    /**
     * 拒绝批量导入
     */
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
                batchId, "PENDING", "REJECTED", LocalDateTime.now(), reviewerStudentNo, reason);
        if (rows == 0) {
            throw BusinessException.badRequest("ALREADY_PROCESSED");
        }
        log.info("Rejected batch import: batchId={}, reason={}, reviewer={}", batchId, reason, reviewerStudentNo);
    }

    /**
     * 删除待审核批量导入（包括详情记录）
     */
    @Transactional
    public void deletePendingBatchImport(String batchId) {
        pendingBatchImportMapper.deleteRecordsByBatchId(batchId);
        pendingBatchImportMapper.delete(batchId);
        log.info("Deleted pending batch import: batchId={}", batchId);
    }

    /**
     * 规范化活动名称
     * 去除活动名称后面的小时数标记，如 "xxx(2小时)" -> "xxx"
     *
     * @param originalName 原始活动名称
     * @return 规范化后的活动名称
     */
    public String normalizeActivityName(String originalName) {
        if (originalName == null || originalName.isEmpty()) {
            return originalName;
        }

        Matcher matcher = HOUR_SUFFIX_PATTERN.matcher(originalName);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }

        return originalName.trim();
    }

    /**
     * 获取用户提交的待审核批量导入
     *
     * @param submittedBy 提交人学号
     * @return 批量导入列表
     */
    public List<PendingBatchImport> getPendingBatchImportsBySubmitter(String submittedBy) {
        return pendingBatchImportMapper.listBySubmitter(submittedBy);
    }

    /**
     * 获取用户提交的指定状态批量导入（支持分页）
     *
     * @param submittedBy 提交人学号
     * @param status 状态(PENDING/APPROVED/REJECTED)，null表示所有状态
     * @param page 页码
     * @param pageSize 每页大小
     * @return 批量导入列表
     */
    public List<PendingBatchImport> getPendingBatchImportsBySubmitterPaged(
            String submittedBy, String status, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return pendingBatchImportMapper.listPaged(status, submittedBy, pageSize, offset);
    }

    /**
     * 统计用户提交的指定状态批量导入数量
     *
     * @param submittedBy 提交人学号
     * @param status 状态(PENDING/APPROVED/REJECTED)，null表示所有状态
     * @return 数量
     */
    public int countPendingBatchImportsBySubmitter(String submittedBy, String status) {
        return pendingBatchImportMapper.countFiltered(status, submittedBy);
    }
}

