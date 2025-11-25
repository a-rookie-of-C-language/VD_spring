package site.arookieofc.controller.VO;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Result {
    private Integer code;
    private String message;
    private Object data;
    public static Result success() {
        return new Result(200, "success", null);
    }

    public static Result success(Object data) {
        return new Result(200, "success", data);
    }

    public static Result success(Object data, String message) {
        return new Result(200, message, data);
    }

    public static Result error() {
        return new Result(500, "error", null);
    }

    public static Result error(String message) {
        return new Result(500, message, null);
    }

    public static Result of(Integer code, String message, Object data) {
        return new Result(code, message, data);
    }

}
