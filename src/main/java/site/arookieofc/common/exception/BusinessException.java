package site.arookieofc.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String errorCode;

    public BusinessException(HttpStatus httpStatus, String errorCode) {
        super(errorCode);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public BusinessException(HttpStatus httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }


    public static BusinessException notFound(String code) {
        return new BusinessException(HttpStatus.NOT_FOUND, code);
    }

    public static BusinessException badRequest(String code) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code);
    }

    public static BusinessException forbidden(String code) {
        return new BusinessException(HttpStatus.FORBIDDEN, code);
    }

    public static BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    public static BusinessException conflict(String code) {
        return new BusinessException(HttpStatus.CONFLICT, code);
    }
}
