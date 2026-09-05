package site.arookieofc.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import site.arookieofc.controller.VO.BusinessOperationLogVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BusinessOperationLogService;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
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
    private final ObjectMapper objectMapper;

    @Around("@annotation(site.arookieofc.common.audit.BusinessOperation)")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNs = System.nanoTime();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        BusinessOperation operation = method.getAnnotation(BusinessOperation.class);

        Map<String, Object> argMap = mapArgs(signature, joinPoint.getArgs());
        UserPrincipal principal = resolvePrincipal(joinPoint.getArgs());

        String operatorNo = principal != null ? principal.getStudentNo() : "anonymous";
        String operatorRole = principal != null ? principal.getRole() : "unknown";
        String operatorIp = resolveOperatorIp();
        String operatorUserAgent = resolveUserAgent();
        String requestId = resolveRequestId();

        String targetId = extractValue(operation.targetIdParam(), argMap);
        String targetName = extractValue(operation.targetNameParam(), argMap);
        String beforeChange = snapshot(argMap);

        String status = "SUCCESS";
        Object resultObj;
        try {
            resultObj = joinPoint.proceed();
            if (resultObj instanceof Result result && result.getCode() != 200) {
                status = "FAILED";
            }
        } catch (Throwable e) {
            status = "FAILED";
            writeLog(operation, operatorNo, operatorRole, operatorIp, operatorUserAgent, requestId, targetId, targetName, status,
                    operation.detail() + " | exception=" + e.getClass().getSimpleName(),
                    durationMs(startNs), beforeChange, "");
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

        String afterChange = resultObj instanceof Result result ? snapshot(result.getData()) : snapshot(resultObj);
        writeLog(operation, operatorNo, operatorRole, operatorIp, operatorUserAgent, requestId, targetId, targetName, status, operation.detail(),
                durationMs(startNs), beforeChange, afterChange);
        return resultObj;
    }

    private void writeLog(BusinessOperation operation,
                          String operatorNo,
                          String operatorRole,
                          String operatorIp,
                          String operatorUserAgent,
                          String requestId,
                          String targetId,
                          String targetName,
                          String status,
                          String detail,
                          long durationMs,
                          String beforeChange,
                          String afterChange) {
        BusinessOperationLogVO logVO = BusinessOperationLogVO.builder()
                .timestamp(OffsetDateTime.now().toString())
                .operatorStudentNo(operatorNo)
                .operatorRole(operatorRole)
                .operatorIp(defaultString(operatorIp))
                .operatorUserAgent(defaultString(operatorUserAgent))
                .requestId(defaultString(requestId))
                .action(operation.action())
                .targetType(operation.targetType())
                .targetId(defaultString(targetId))
                .targetName(defaultString(targetName))
                .detail(defaultString(detail))
                .status(status)
                .durationMs(durationMs)
                .beforeChange(defaultString(beforeChange))
                .afterChange(defaultString(afterChange))
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
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException ignored) {
            }
        }
        return "";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String resolveOperatorIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return "unknown";
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] parts = xForwardedFor.split(",");
            if (parts.length > 0 && !parts[0].trim().isBlank()) {
                return parts[0].trim();
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown";
        }
        return remoteAddr;
    }

    private String resolveUserAgent() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return "";
        }
        return defaultString(servletRequestAttributes.getRequest().getHeader("User-Agent"));
    }

    private String resolveRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return "";
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        String header = request.getHeader("X-Request-ID");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        Object attr = request.getAttribute("requestId");
        return attr == null ? "" : String.valueOf(attr);
    }

    private long durationMs(long startNs) {
        return Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
    }

    private String snapshot(Object value) {
        if (value == null) {
            return "";
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > 2000 ? json.substring(0, 2000) : json;
        } catch (JsonProcessingException ignored) {
            String text = String.valueOf(value);
            return text.length() > 2000 ? text.substring(0, 2000) : text;
        }
    }
}
