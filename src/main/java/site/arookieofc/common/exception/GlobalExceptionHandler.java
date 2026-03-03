package site.arookieofc.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import site.arookieofc.controller.VO.Result;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        return Result.of(e.getHttpStatus().value(), e.getErrorCode(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());
        return Result.of(400, e.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public Result handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return Result.of(500, "Internal server error", null);
    }
}
