package site.arookieofc.common.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import site.arookieofc.controller.VO.BusinessOperationLogVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BusinessOperationLogService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessOperationAspectTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesPrincipalFromMethodArgsWhenPresent() throws Throwable {
        BusinessOperationLogService logService = mock(BusinessOperationLogService.class);
        BusinessOperationAspect aspect = new BusinessOperationAspect(logService);

        UserPrincipal argPrincipal = new UserPrincipal("20260001", "leader", "leader-user");
        Object[] args = new Object[]{argPrincipal};

        ProceedingJoinPoint joinPoint = mockJoinPoint("operationWithPrincipalArg", args, Result.success());
        aspect.logOperation(joinPoint);

        ArgumentCaptor<BusinessOperationLogVO> captor = ArgumentCaptor.forClass(BusinessOperationLogVO.class);
        verify(logService).write(captor.capture());

        BusinessOperationLogVO logVO = captor.getValue();
        assertEquals("20260001", logVO.getOperatorStudentNo());
        assertEquals("leader", logVO.getOperatorRole());
        assertEquals("SUCCESS", logVO.getStatus());
    }

    @Test
    void fallsBackToSecurityContextPrincipalWhenMethodArgsDoNotContainPrincipal() throws Throwable {
        BusinessOperationLogService logService = mock(BusinessOperationLogService.class);
        BusinessOperationAspect aspect = new BusinessOperationAspect(logService);

        UserPrincipal contextPrincipal = new UserPrincipal("20260002", "admin", "admin-user");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(contextPrincipal, null, contextPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Object[] args = new Object[]{"activity-001"};
        ProceedingJoinPoint joinPoint = mockJoinPoint("operationWithoutPrincipalArg", args, Result.success());
        aspect.logOperation(joinPoint);

        ArgumentCaptor<BusinessOperationLogVO> captor = ArgumentCaptor.forClass(BusinessOperationLogVO.class);
        verify(logService).write(captor.capture());

        BusinessOperationLogVO logVO = captor.getValue();
        assertEquals("20260002", logVO.getOperatorStudentNo());
        assertEquals("admin", logVO.getOperatorRole());
        assertEquals("SUCCESS", logVO.getStatus());
    }

    private ProceedingJoinPoint mockJoinPoint(String methodName, Object[] args, Object proceedResult) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        Method method = DummyController.class.getDeclaredMethod(methodName, resolveParameterTypes(args));

        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"value"});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(proceedResult);
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
