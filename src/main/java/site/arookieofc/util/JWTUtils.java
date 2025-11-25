package site.arookieofc.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public final class JWTUtils {
    private static long expiryMinutes;
    private static byte[] SECRET_BYTES;

    @Value("${app.security.jwt.expiry-minutes:120}")
    private long expiryMinutesConfig;
    @Value("${app.security.jwt.secret:}")
    private String jwtSecretConfig;

    @PostConstruct
    private void init() {
        expiryMinutes = expiryMinutesConfig;
        if (jwtSecretConfig != null && jwtSecretConfig.length() >= 32) {
            SECRET_BYTES = jwtSecretConfig.getBytes();
        } else {
            SECRET_BYTES = Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded();
        }
    }

    public static String generateToken(String subject) {
        return generateToken(subject, null);
    }

    public static String generateToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant exp = now.plus(expiryMinutes, ChronoUnit.MINUTES);
        Map<String, Object> payload = claims == null ? new HashMap<>() : new HashMap<>(claims);
        return Jwts.builder()
                .setClaims(payload)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(Keys.hmacShaKeyFor(SECRET_BYTES), SignatureAlgorithm.HS256)
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET_BYTES))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static String getSubject(String token) {
        return parseToken(token).getSubject();
    }

    public static boolean isExpired(String token) {
        Date exp = parseToken(token).getExpiration();
        return exp != null && exp.before(new Date());
    }
}
