package site.arookieofc.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.ActivityMapper;
import site.arookieofc.dao.mapper.PendingActivityMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityImportDTO;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PendingActivityServiceTest {

    private static final OffsetDateTime IMPORT_END_TIME = OffsetDateTime.parse("2026-05-31T12:00:00+08:00");

    @Test
    void importActivityNormalizesParticipantsBeforeValidationAndInsert() {
        PendingActivityMapper pendingActivityMapper = pendingActivityMapper();
        UserMapper userMapper = userMapper();
        PendingActivityService service = newService(pendingActivityMapper, userMapper);
        String firstStudentNo = "s1";
        String secondStudentNo = "s2";
        when(userMapper.getUserByStudentNo(firstStudentNo)).thenReturn(user(firstStudentNo));
        when(userMapper.getUserByStudentNo(secondStudentNo)).thenReturn(user(secondStudentNo));

        service.importActivity(importRequest()
                .participants(Arrays.asList(" " + firstStudentNo + " ", "", null, firstStudentNo, " " + secondStudentNo + " "))
                .build(), "submitter", false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pendingActivityMapper).insertParticipants(anyString(), participantsCaptor.capture());

        assertEquals(List.of(firstStudentNo, secondStudentNo), participantsCaptor.getValue());
        verify(userMapper).getUserByStudentNo(firstStudentNo);
        verify(userMapper).getUserByStudentNo(secondStudentNo);
        verifyNoMoreInteractions(userMapper);
    }

    @Test
    void importActivityThrowsBusinessExceptionWhenParticipantIsMissing() {
        PendingActivityMapper pendingActivityMapper = pendingActivityMapper();
        ActivityMapper activityMapper = activityMapper();
        UserMapper userMapper = userMapper();
        PendingActivityService service = newService(pendingActivityMapper, activityMapper, userMapper);
        when(userMapper.getUserByStudentNo("missing")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.importActivity(importRequest()
                        .participants(List.of("missing"))
                        .build(), "submitter", false));

        assertEquals(BAD_REQUEST, exception.getHttpStatus());
        assertEquals("PARTICIPANT_NOT_FOUND", exception.getErrorCode());
        verify(pendingActivityMapper, never()).insert(any());
        verify(activityMapper, never()).insert(any());
    }

    @Test
    void importActivityStopsBeforeInsertWhenCoverUploadFails() throws IOException {
        PendingActivityMapper pendingActivityMapper = pendingActivityMapper();
        ActivityMapper activityMapper = activityMapper();
        FileUploadService fileUploadService = fileUploadService();
        MultipartFile coverFile = nonEmptyFile();
        PendingActivityService service = newService(pendingActivityMapper, activityMapper, fileUploadService);
        when(fileUploadService.uploadCoverImage(coverFile)).thenThrow(new IOException("disk"));

        assertThrows(IllegalArgumentException.class, () -> service.importActivity(importRequest()
                .coverFile(coverFile)
                .build(), "submitter", false));

        verify(pendingActivityMapper, never()).insert(any());
        verify(activityMapper, never()).insert(any());
    }

    @Test
    void importActivityPreservesExcelParseFailureCauseBeforeInsert() throws IOException {
        PendingActivityMapper pendingActivityMapper = pendingActivityMapper();
        ActivityMapper activityMapper = activityMapper();
        ExcelParserService excelParserService = excelParserService();
        MultipartFile excelFile = nonEmptyFile();
        PendingActivityService service = newService(pendingActivityMapper, activityMapper, excelParserService);
        IOException parseError = new IOException("bad excel");
        when(excelParserService.parseStudentNumbers(excelFile)).thenThrow(parseError);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.importActivity(importRequest()
                        .file(excelFile)
                        .build(), "submitter", false));

        assertSame(parseError, exception.getCause());
        verify(pendingActivityMapper, never()).insert(any());
        verify(activityMapper, never()).insert(any());
    }

    private PendingActivityService newService(PendingActivityMapper pendingActivityMapper, UserMapper userMapper) {
        return newService(pendingActivityMapper, activityMapper(), userMapper);
    }

    private MultipartFile nonEmptyFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        return file;
    }

    private PendingActivityMapper pendingActivityMapper() {
        return mock(PendingActivityMapper.class);
    }

    private ActivityMapper activityMapper() {
        return mock(ActivityMapper.class);
    }

    private UserMapper userMapper() {
        return mock(UserMapper.class);
    }

    private User user(String studentNo) {
        return User.builder()
                .studentNo(studentNo)
                .build();
    }

    private FileUploadService fileUploadService() {
        return mock(FileUploadService.class);
    }

    private ExcelParserService excelParserService() {
        return mock(ExcelParserService.class);
    }

    private ActivityImportDTO.ActivityImportDTOBuilder importRequest() {
        return ActivityImportDTO.builder()
                .name("Imported")
                .type(ActivityType.COMMUNITY_SERVICE)
                .duration(2.0)
                .endTime(IMPORT_END_TIME);
    }

    private PendingActivityService newService(PendingActivityMapper pendingActivityMapper,
                                              ActivityMapper activityMapper,
                                              UserMapper userMapper) {
        return newService(
                pendingActivityMapper,
                activityMapper,
                userMapper,
                fileUploadService(),
                excelParserService());
    }

    private PendingActivityService newService(PendingActivityMapper pendingActivityMapper,
                                              ActivityMapper activityMapper,
                                              FileUploadService fileUploadService) {
        return newService(
                pendingActivityMapper,
                activityMapper,
                userMapper(),
                fileUploadService,
                excelParserService());
    }

    private PendingActivityService newService(PendingActivityMapper pendingActivityMapper,
                                              ActivityMapper activityMapper,
                                              ExcelParserService excelParserService) {
        return newService(
                pendingActivityMapper,
                activityMapper,
                userMapper(),
                fileUploadService(),
                excelParserService);
    }

    private PendingActivityService newService(PendingActivityMapper pendingActivityMapper,
                                              ActivityMapper activityMapper,
                                              UserMapper userMapper,
                                              FileUploadService fileUploadService,
                                              ExcelParserService excelParserService) {
        return new PendingActivityService(
                pendingActivityMapper,
                activityMapper,
                userMapper,
                fileUploadService,
                excelParserService,
                volunteerHourGrantService(),
                applicationEventPublisher());
    }

    private VolunteerHourGrantService volunteerHourGrantService() {
        return mock(VolunteerHourGrantService.class);
    }

    private ApplicationEventPublisher applicationEventPublisher() {
        return mock(ApplicationEventPublisher.class);
    }
}
