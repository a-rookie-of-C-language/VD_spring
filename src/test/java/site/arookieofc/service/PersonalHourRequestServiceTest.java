package site.arookieofc.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.dao.entity.PersonalHourRequest;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.PersonalHourRequestMapper;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.dto.PersonalHourRequestDTO;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalHourRequestServiceTest {

    @Test
    void submitRequestThrowsBusinessExceptionWhenApplicantIsMissing() {
        PersonalHourRequestMapper requestMapper = requestMapper();
        UserMapper userMapper = userMapper();
        PersonalHourRequestService service = newService(requestMapper, userMapper);
        when(userMapper.getUserByStudentNo("missing")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitRequest(request(), "missing"));

        assertEquals(BAD_REQUEST, exception.getHttpStatus());
        assertEquals("APPLICANT_NOT_FOUND", exception.getErrorCode());
        verify(requestMapper, never()).insert(any(PersonalHourRequest.class));
    }

    @Test
    void reviewRequestRejectsBlankReasonInServiceLayer() {
        PersonalHourRequestMapper requestMapper = requestMapper();
        PersonalHourRequestService service = newService(requestMapper);
        String requestId = "r1";
        when(requestMapper.getById(requestId)).thenReturn(PersonalHourRequest.builder()
                .id(requestId)
                .status(ActivityStatus.UnderReview)
                .build());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reviewRequest(requestId, false, " ", "admin"));

        assertEquals("REASON_REQUIRED", exception.getErrorCode());
        verify(requestMapper, never()).updateStatusIfCurrent(
                anyString(), any(), any(), any(), any(), anyString());
    }

    @Test
    void submitRequestStopsBeforeInsertWhenAttachmentUploadFails() throws IOException {
        PersonalHourRequestMapper requestMapper = requestMapper();
        UserMapper userMapper = userMapper();
        FileUploadService fileUploadService = fileUploadService();
        MultipartFile file = attachmentFile();
        PersonalHourRequestService service = newService(requestMapper, userMapper, fileUploadService);
        String studentNo = "s1";
        when(userMapper.getUserByStudentNo(studentNo)).thenReturn(User.builder().studentNo(studentNo).build());
        when(fileUploadService.uploadAttachment(file)).thenThrow(new IOException("disk"));

        assertThrows(IllegalArgumentException.class, () -> service.submitRequest(
                request(file), studentNo));

        verify(requestMapper, never()).insert(any(PersonalHourRequest.class));
        verify(requestMapper, never()).insertAttachments(anyString(), any());
    }

    private PersonalHourRequestMapper requestMapper() {
        return mock(PersonalHourRequestMapper.class);
    }

    private UserMapper userMapper() {
        return mock(UserMapper.class);
    }

    private FileUploadService fileUploadService() {
        return mock(FileUploadService.class);
    }

    private MultipartFile attachmentFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        return file;
    }

    private PersonalHourRequestDTO request(MultipartFile... files) {
        return PersonalHourRequestDTO.builder()
                .files(List.of(files))
                .build();
    }

    private PersonalHourRequestService newService(PersonalHourRequestMapper requestMapper) {
        return newService(requestMapper, userMapper());
    }

    private PersonalHourRequestService newService(
            PersonalHourRequestMapper requestMapper,
            UserMapper userMapper) {
        return newService(requestMapper, userMapper, fileUploadService());
    }

    private PersonalHourRequestService newService(
            PersonalHourRequestMapper requestMapper,
            UserMapper userMapper,
            FileUploadService fileUploadService) {
        return new PersonalHourRequestService(
                requestMapper,
                userMapper,
                fileUploadService,
                mock(VolunteerHourGrantService.class),
                mock(ApplicationEventPublisher.class));
    }
}
