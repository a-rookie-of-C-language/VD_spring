package site.arookieofc.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JWTUtils {
    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_TOKEN_VERSION = "token_version";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private byte[] secretBytes;

    @Value("${app.security.jwt.access-expiry-minutes:15}")
    private long accessExpiryMinutes;

    @Value("${app.security.jwt.refresh-expiry-minutes:10080}")
    private long refreshExpiryMinutes;

    @Value("${app.security.jwt.secret:}")
    private String jwtSecretConfig;

    public JWTUtils() {
    }

    JWTUtils(String jwtSecretConfig, long accessExpiryMinutes, long refreshExpiryMinutes) {
        this.jwtSecretConfig = jwtSecretConfig;
        this.accessExpiryMinutes = accessExpiryMinutes;
        this.refreshExpiryMinutes = refreshExpiryMinutes;
        init();
    }

    @PostConstruct
    private void init() {
        if (jwtSecretConfig == null || jwtSecretConfig.length() < 32) {
            throw new IllegalStateException("JWT secret not configured or too short (>=32 chars).");
        }
        secretBytes = jwtSecretConfig.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(String subject) {
        return generateAccessToken(subject, null, 0);
    }

    public String generateToken(String subject, Map<String, Object> claims) {
        return generateAccessToken(subject, claims, 0);
    }

    public String generateAccessToken(String subject, Map<String, Object> claims, int tokenVersion) {
        return generateTypedToken(subject, claims, tokenVersion, TYPE_ACCESS, accessExpiryMinutes);
    }

    public String generateRefreshToken(String subject, Map<String, Object> claims, int tokenVersion) {
        return generateTypedToken(subject, claims, tokenVersion, TYPE_REFRESH, refreshExpiryMinutes);
    }

    private String generateTypedToken(String subject,
                                      Map<String, Object> claims,
                                      int tokenVersion,
                                      String type,
                                      long expiryMinutes) {
        Instant now = Instant.now();
        Instant exp = now.plus(expiryMinutes, ChronoUnit.MINUTES);
        Map<String, Object> payload = claims == null ? new HashMap<>() : new HashMap<>(claims);
        payload.put(CLAIM_TYPE, type);
        payload.put(CLAIM_TOKEN_VERSION, tokenVersion);
        return Jwts.builder()
                .setClaims(payload)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(Keys.hmacShaKeyFor(secretBytes), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secretBytes))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public TokenParseResult parseTokenSafe(String token) {
        if (token == null || token.trim().isEmpty()) {
            return TokenParseResult.empty();
        }
        try {
            Claims claims = parseToken(token);
            return TokenParseResult.success(claims);
        } catch (ExpiredJwtException e) {
            return TokenParseResult.expired();
        } catch (MalformedJwtException e) {
            return TokenParseResult.malformed();
        } catch (SignatureException e) {
            return TokenParseResult.signatureInvalid();
        } catch (JwtException e) {
            return TokenParseResult.invalid();
        }
    }

    public boolean isTokenType(Claims claims, String expectedType) {
        if (claims == null || expectedType == null) {
            return false;
        }
        String tokenType = claims.get(CLAIM_TYPE, String.class);
        return expectedType.equalsIgnoreCase(tokenType);
    }

    public int getTokenVersion(Claims claims) {
        if (claims == null) {
            return -1;
        }
        Object value = claims.get(CLAIM_TOKEN_VERSION);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }
}
