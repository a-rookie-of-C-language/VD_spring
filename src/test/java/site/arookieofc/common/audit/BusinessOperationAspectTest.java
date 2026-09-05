package site.arookieofc.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import site.arookieofc.controller.VO.BusinessOperationLogVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BusinessOperationLogService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessOperationAspectTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesPrincipalFromMethodArgsWhenPresent() throws Throwable {
        BusinessOperationLogService logService = logService();
        BusinessOperationAspect aspect = newAspect(logService);

        UserPrincipal argPrincipal = principal("20260001", "leader", "leader-user");
        Object[] args = new Object[]{argPrincipal};

        ProceedingJoinPoint joinPoint = mockJoinPoint("operationWithPrincipalArg", args, Result.success());
        aspect.logOperation(joinPoint);

        BusinessOperationLogVO logVO = writtenLog(logService);
        assertEquals("20260001", logVO.getOperatorStudentNo());
        assertEquals("leader", logVO.getOperatorRole());
        assertEquals("SUCCESS", logVO.getStatus());
    }

    @Test
    void fallsBackToSecurityContextPrincipalWhenMethodArgsDoNotContainPrincipal() throws Throwable {
        BusinessOperationLogService logService = logService();
        BusinessOperationAspect aspect = newAspect(logService);

        UserPrincipal contextPrincipal = principal("20260002", "admin", "admin-user");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(contextPrincipal, null, contextPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Object[] args = new Object[]{"activity-001"};
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.10.10.1, 10.10.10.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ProceedingJoinPoint joinPoint = mockJoinPoint("operationWithoutPrincipalArg", args, Result.success());
        aspect.logOperation(joinPoint);

        BusinessOperationLogVO logVO = writtenLog(logService);
        assertEquals("20260002", logVO.getOperatorStudentNo());
        assertEquals("admin", logVO.getOperatorRole());
        assertEquals("10.10.10.1", logVO.getOperatorIp());
        assertEquals("SUCCESS", logVO.getStatus());
    }

    @Test
    void snapshotFallsBackToStringWhenJsonSerializationFails() throws Throwable {
        BusinessOperationLogService logService = logService();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("bad json") { });
        BusinessOperationAspect aspect = newAspect(logService, objectMapper);

        ProceedingJoinPoint joinPoint = mockJoinPoint(
                "operationWithoutPrincipalArg", new Object[]{"fallback-value"}, "after-value");
        aspect.logOperation(joinPoint);

        BusinessOperationLogVO logVO = writtenLog(logService);
        assertTrue(logVO.getBeforeChange().contains("fallback-value"));
        assertEquals("after-value", logVO.getAfterChange());
    }

    @Test
    void leavesTargetFieldsEmptyWhenResultHasNoCandidateGetter() throws Throwable {
        BusinessOperationLogService logService = logService();
        BusinessOperationAspect aspect = newAspect(logService);

        ProceedingJoinPoint joinPoint = mockJoinPoint(
                "operationWithoutPrincipalArg", new Object[]{"activity-003"}, Result.success(new Object()));
        aspect.logOperation(joinPoint);

        BusinessOperationLogVO logVO = writtenLog(logService);
        assertEquals("", logVO.getTargetId());
        assertEquals("", logVO.getTargetName());
    }

    @Test
    void writesFailedLogAndRethrowsOriginalExceptionWhenOperationThrows() throws Throwable {
        BusinessOperationLogService logService = logService();
        BusinessOperationAspect aspect = newAspect(logService);
        RuntimeException failure = new RuntimeException("boom");

        ProceedingJoinPoint joinPoint = mockThrowingJoinPoint(
                "operationWithoutPrincipalArg", new Object[]{"activity-002"}, failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> aspect.logOperation(joinPoint));
        assertEquals(failure, thrown);

        BusinessOperationLogVO logVO = writtenLog(logService);
        assertEquals("FAILED", logVO.getStatus());
        assertTrue(logVO.getDetail().contains("review activity"));
        assertTrue(logVO.getDetail().contains("exception=RuntimeException"));
        assertEquals("", logVO.getAfterChange());
    }

    private BusinessOperationLogService logService() {
        return mock(BusinessOperationLogService.class);
    }

    private UserPrincipal principal(String studentNo, String role, String username) {
        return new UserPrincipal(studentNo, role, username);
    }

    private BusinessOperationAspect newAspect(BusinessOperationLogService logService) {
        return newAspect(logService, new ObjectMapper());
    }

    private BusinessOperationAspect newAspect(BusinessOperationLogService logService, ObjectMapper objectMapper) {
        return new BusinessOperationAspect(logService, objectMapper);
    }

    private BusinessOperationLogVO writtenLog(BusinessOperationLogService logService) {
        ArgumentCaptor<BusinessOperationLogVO> captor = ArgumentCaptor.forClass(BusinessOperationLogVO.class);
        verify(logService).write(captor.capture());
        return captor.getValue();
    }

    private ProceedingJoinPoint mockJoinPoint(String methodName, Object[] args, Object proceedResult) throws Throwable {
        ProceedingJoinPoint joinPoint = baseJoinPoint(methodName, args);
        when(joinPoint.proceed()).thenReturn(proceedResult);
        return joinPoint;
    }

    private ProceedingJoinPoint mockThrowingJoinPoint(String methodName, Object[] args, Throwable failure) throws Throwable {
        ProceedingJoinPoint joinPoint = baseJoinPoint(methodName, args);
        when(joinPoint.proceed()).thenThrow(failure);
        return joinPoint;
    }

    private ProceedingJoinPoint baseJoinPoint(String methodName, Object[] args) throws NoSuchMethodException {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        Method method = DummyController.class.getDeclaredMethod(methodName, resolveParameterTypes(args));

        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"value"});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    private Class<?>[] resolveParameterTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return types;
    }

    static class DummyController {
        @BusinessOperation(action = "PUBLISH", targetType = "activity", detail = "publish activity")
        public void operationWithPrincipalArg(UserPrincipal principal) {
        }

        @BusinessOperation(action = "REVIEW", targetType = "activity", detail = "review activity")
        public void operationWithoutPrincipalArg(String value) {
        }
    }
}
