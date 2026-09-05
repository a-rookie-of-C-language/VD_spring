package site.arookieofc.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JWTUtilsTest {

    @Test
    void parseTokenSafeClassifiesEmptyMalformedAndBadSignatureTokens() {
        JWTUtils issuer = jwt("01234567890123456789012345678901");
        JWTUtils verifier = jwt("abcdefghijklmnopqrstuvwxyzabcdef");
        String subject = "s1";

        assertFalse(issuer.parseTokenSafe(" ").isSuccess());
        assertFalse(issuer.parseTokenSafe("not-a-jwt").isSuccess());
        assertFalse(verifier.parseTokenSafe(issuer.generateAccessToken(subject, null, 0)).isSuccess());
    }

    @Test
    void parseTokenSafeReturnsSuccessForValidAccessToken() {
        JWTUtils jwtUtils = jwt("01234567890123456789012345678901");
        String subject = "s1";

        TokenParseResult result = jwtUtils.parseTokenSafe(jwtUtils.generateAccessToken(subject, null, 3));

        assertTrue(result.isSuccess());
        assertTrue(jwtUtils.isTokenType(result.getClaims(), JWTUtils.TYPE_ACCESS));
    }

    private JWTUtils jwt(String secret) {
        return new JWTUtils(secret, 15L, 60L);
    }
}
