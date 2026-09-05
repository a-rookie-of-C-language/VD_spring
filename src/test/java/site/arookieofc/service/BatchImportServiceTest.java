package site.arookieofc.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.dao.entity.PendingBatchImport;
import site.arookieofc.dao.entity.PendingBatchImportRecord;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.dao.mapper.PendingBatchImportMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.dto.BatchImportRecordDTO;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchImportServiceTest {

    @Test
    void submitForReviewSplitsAndNormalizesActivityNames() throws IOException {
        ExcelParserService excelParserService = excelParserService();
        UserMapper userMapper = userMapper();
        PendingBatchImportMapper pendingBatchImportMapper = pendingBatchImportMapper();
        CampusIdentityService identityService = identityService();
        BatchImportService service = newService(
                excelParserService,
                userMapper,
                pendingBatchImportMapper,
                identityService);
        MultipartFile file = batchFile();

        when(excelParserService.parseBatchImportRecords(file)).thenReturn(List.of(
                batchRecord("Cleanup\uFF082 \u5C0F\u65F6\uFF09\uFF0CLibrary (1.5 hours)\u3001Food Drive", 2.0)));
        when(userMapper.getUserByStudentNo("20260001")).thenReturn(user("20260001"));
        when(identityService.existsByStudentNo(anyString())).thenReturn(true);

        service.submitForReview(file, "submitter");

        List<PendingBatchImportRecord> records = capturedInsertedRecords(pendingBatchImportMapper);

        assertEquals(3, records.size());
        assertEquals("Cleanup", records.get(0).getActivityName());
        assertEquals("Library", records.get(1).getActivityName());
        assertEquals("Food Drive", records.get(2).getActivityName());
    }

    @Test
    void normalizeActivityNameRemovesSupportedHourSuffixes() {
        BatchImportService service = newService();

        assertEquals("Cleanup", service.normalizeActivityName("Cleanup\uFF082 \u5C0F\u65F6\uFF09"));
        assertEquals("Library", service.normalizeActivityName("Library (1.5 hours)"));
        assertEquals("Food Drive", service.normalizeActivityName(" Food Drive "));
    }

    @Test
    void submitForReviewKeepsInvalidRecordWhenActivityNameIsEmpty() throws IOException {
        ExcelParserService excelParserService = excelParserService();
        UserMapper userMapper = userMapper();
        PendingBatchImportMapper pendingBatchImportMapper = pendingBatchImportMapper();
        CampusIdentityService identityService = identityService();
        BatchImportService service = newService(
                excelParserService,
                userMapper,
                pendingBatchImportMapper,
                identityService);
        MultipartFile file = batchFile();

        when(excelParserService.parseBatchImportRecords(file)).thenReturn(List.of(batchRecord("  ")));
        when(userMapper.getUserByStudentNo("20260001")).thenReturn(user("20260001"));
        when(identityService.existsByStudentNo(anyString())).thenReturn(true);

        var result = service.submitForReview(file, "submitter");

        List<PendingBatchImportRecord> records = capturedInsertedRecords(pendingBatchImportMapper);

        assertEquals(1, records.size());
        assertEquals("INVALID", records.get(0).getValidationStatus());
        assertEquals("row 3: activityName is empty", records.get(0).getValidationError());
        assertEquals(0, result.getValidRecords());
        assertEquals(1, result.getInvalidRecords());
    }

    @Test
    void approveBatchImportRecordsRuntimeRowFailureAndContinues() {
        UserMapper userMapper = userMapper();
        ActivityMapper activityMapper = activityMapper();
        PendingBatchImportMapper pendingBatchImportMapper = pendingBatchImportMapper();
        BatchImportService service = newService(userMapper, activityMapper, pendingBatchImportMapper);
        String batchId = "batch-1";

        when(pendingBatchImportMapper.getById(batchId)).thenReturn(PendingBatchImport.builder()
                .id(batchId)
                .status("PENDING")
                .build());
        when(pendingBatchImportMapper.updateStatusIfCurrent(eq(batchId), eq("PENDING"), eq("APPROVED"),
                any(), eq("reviewer"), eq(null))).thenReturn(1);
        when(pendingBatchImportMapper.getRecordsByBatchId(batchId)).thenReturn(List.of(
                PendingBatchImportRecord.builder()
                        .batchId(batchId)
                        .studentNo("20260001")
                        .activityName("Cleanup")
                        .duration(1.0)
                        .validationStatus("VALID")
                        .build()));
        when(userMapper.getUserByStudentNo("20260001")).thenReturn(user("20260001"));
        when(activityMapper.getIdByName("Cleanup")).thenReturn("activity-1");
        when(activityMapper.existsParticipant("activity-1", "20260001"))
                .thenThrow(new IllegalStateException("participant lookup failed"));

        var result = service.approveBatchImport(batchId, "reviewer");

        assertEquals(1, result.getValidRecords());
        assertEquals(0, result.getParticipantsAdded());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("studentNo 20260001 process failed: participant lookup failed")));
    }

    private BatchImportService newService() {
        return newService(
                excelParserService(),
                userMapper(),
                activityMapper(),
                pendingBatchImportMapper(),
                identityService());
    }

    private MultipartFile batchFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("batch.xlsx");
        return file;
    }

    private BatchImportRecordDTO batchRecord(String activityName) {
        return batchRecord(activityName, null);
    }

    private BatchImportRecordDTO batchRecord(String activityName, Double duration) {
        return BatchImportRecordDTO.builder()
                .studentNo("20260001")
                .username("Alice")
                .duration(duration)
                .activityName(activityName)
                .build();
    }

    private BatchImportService newService(
            ExcelParserService excelParserService,
            UserMapper userMapper,
            PendingBatchImportMapper pendingBatchImportMapper,
            CampusIdentityService identityService) {
        return newService(
                excelParserService,
                userMapper,
                activityMapper(),
                pendingBatchImportMapper,
                identityService);
    }

    private BatchImportService newService(
            UserMapper userMapper,
            ActivityMapper activityMapper,
            PendingBatchImportMapper pendingBatchImportMapper) {
        return newService(
                excelParserService(),
                userMapper,
                activityMapper,
                pendingBatchImportMapper,
                identityService());
    }

    private BatchImportService newService(
            ExcelParserService excelParserService,
            UserMapper userMapper,
            ActivityMapper activityMapper,
            PendingBatchImportMapper pendingBatchImportMapper,
            CampusIdentityService identityService) {
        return new BatchImportService(
                excelParserService,
                userMapper,
                activityMapper,
                pendingBatchImportMapper,
                volunteerHourGrantService(),
                identityService,
                applicationEventPublisher());
    }

    private ExcelParserService excelParserService() {
        return mock(ExcelParserService.class);
    }

    private UserMapper userMapper() {
        return mock(UserMapper.class);
    }

    private User user(String studentNo) {
        return User.builder()
                .studentNo(studentNo)
                .build();
    }

    private ActivityMapper activityMapper() {
        return mock(ActivityMapper.class);
    }

    private PendingBatchImportMapper pendingBatchImportMapper() {
        return mock(PendingBatchImportMapper.class);
    }

    private CampusIdentityService identityService() {
        return mock(CampusIdentityService.class);
    }

    private VolunteerHourGrantService volunteerHourGrantService() {
        return mock(VolunteerHourGrantService.class);
    }

    private ApplicationEventPublisher applicationEventPublisher() {
        return mock(ApplicationEventPublisher.class);
    }

    private List<PendingBatchImportRecord> capturedInsertedRecords(PendingBatchImportMapper pendingBatchImportMapper) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PendingBatchImportRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pendingBatchImportMapper).insertRecords(recordsCaptor.capture());
        return recordsCaptor.getValue();
    }
}
