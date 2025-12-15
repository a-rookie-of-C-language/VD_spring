package site.arookieofc.util;

import io.jsonwebtoken.Claims;
import lombok.Getter;

/**
 * 封装Token解析结果，包含成功/失败状态及详细错误信息
 */
@Getter
public class TokenParseResult {
    private final boolean success;
    private final String message;
    private final Claims claims;

    private TokenParseResult(boolean success, String message, Claims claims) {
        this.success = success;
        this.message = message;
        this.claims = claims;
    }

    public static TokenParseResult success(Claims claims) {
        return new TokenParseResult(true, "Token验证成功", claims);
    }

    public static TokenParseResult expired() {
        return new TokenParseResult(false, "Token已过期，请重新登录", null);
    }

    public static TokenParseResult invalid() {
        return new TokenParseResult(false, "Token无效，请重新登录", null);
    }

    public static TokenParseResult malformed() {
        return new TokenParseResult(false, "Token格式错误", null);
    }

    public static TokenParseResult signatureInvalid() {
        return new TokenParseResult(false, "Token签名无效", null);
    }

    public static TokenParseResult empty() {
        return new TokenParseResult(false, "Token不能为空", null);
    }

    public static TokenParseResult error(String message) {
        return new TokenParseResult(false, message, null);
    }
}
