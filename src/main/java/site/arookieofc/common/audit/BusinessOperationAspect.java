package site.arookieofc.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import site.arookieofc.controller.VO.BusinessOperationLogVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BusinessOperationLogService;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessOperationAspect {
    private final BusinessOperationLogService businessOperationLogService;

    @Around("@annotation(site.arookieofc.common.audit.BusinessOperation)")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        BusinessOperation operation = method.getAnnotation(BusinessOperation.class);

        Map<String, Object> argMap = mapArgs(signature, joinPoint.getArgs());
        UserPrincipal principal = resolvePrincipal(joinPoint.getArgs());

        String operatorNo = principal != null ? principal.getStudentNo() : "anonymous";
        String operatorRole = principal != null ? principal.getRole() : "unknown";

        String targetId = extractValue(operation.targetIdParam(), argMap);
        String targetName = extractValue(operation.targetNameParam(), argMap);

        String status = "SUCCESS";
        Object resultObj;
        try {
            resultObj = joinPoint.proceed();
            if (resultObj instanceof Result result && result.getCode() != 200) {
                status = "FAILED";
            }
        } catch (Throwable e) {
            status = "FAILED";
            writeLog(operation, operatorNo, operatorRole, targetId, targetName, status, operation.detail() + " | exception=" + e.getClass().getSimpleName());
            throw e;
        }

        if (resultObj instanceof Result result) {
            if (targetId == null || targetId.isBlank()) {
                targetId = inferFromResult(result.getData(), "id", "activityId", "requestId", "batchId");
            }
            if (targetName == null || targetName.isBlank()) {
                targetName = inferFromResult(result.getData(), "name", "activityName", "title");
            }
        }

        writeLog(operation, operatorNo, operatorRole, targetId, targetName, status, operation.detail());
        return resultObj;
    }

    private void writeLog(BusinessOperation operation,
                          String operatorNo,
                          String operatorRole,
                          String targetId,
                          String targetName,
                          String status,
                          String detail) {
        BusinessOperationLogVO logVO = BusinessOperationLogVO.builder()
                .timestamp(OffsetDateTime.now().toString())
                .operatorStudentNo(operatorNo)
                .operatorRole(operatorRole)
                .action(operation.action())
                .targetType(operation.targetType())
                .targetId(defaultString(targetId))
                .targetName(defaultString(targetName))
                .detail(defaultString(detail))
                .status(status)
                .build();
        businessOperationLogService.write(logVO);
    }

    private Map<String, Object> mapArgs(MethodSignature signature, Object[] args) {
        Map<String, Object> argMap = new HashMap<>();
        String[] names = signature.getParameterNames();
        if (names == null) {
            return argMap;
        }
        for (int i = 0; i < names.length; i++) {
            argMap.put(names[i], args[i]);
        }
        return argMap;
    }

    private UserPrincipal resolvePrincipal(Object[] args) {
        UserPrincipal fromArgs = extractPrincipalFromArgs(args);
        if (fromArgs != null) {
            return fromArgs;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UserPrincipal principal) {
            return principal;
        }
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private UserPrincipal extractPrincipalFromArgs(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UserPrincipal principal) {
                return principal;
            }
        }
        return null;
    }

    private String extractValue(String key, Map<String, Object> argMap) {
        if (key == null || key.isBlank()) {
            return "";
        }
        Object value = argMap.get(key);
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private String inferFromResult(Object data, String... candidateKeys) {
        if (data == null) {
            return "";
        }
        if (data instanceof Map<?, ?> map) {
            for (String key : candidateKeys) {
                Object value = map.get(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            return "";
        }

        for (String key : candidateKeys) {
            String methodName = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
            try {
                Method method = data.getClass().getMethod(methodName);
                Object value = method.invoke(data);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
