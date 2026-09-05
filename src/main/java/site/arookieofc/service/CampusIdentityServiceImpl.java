package site.arookieofc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.arookieofc.dao.mapper.UserMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Service
@Slf4j
public class CampusIdentityServiceImpl implements CampusIdentityService {
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.identity.provider:local}")
    private String provider;

    @Value("${app.identity.http.base-url:}")
    private String identityBaseUrl;

    @Value("${app.identity.http.path-template:/api/identity/users/{studentNo}/exists}")
    private String identityPathTemplate;

    @Autowired
    public CampusIdentityServiceImpl(UserMapper userMapper, ObjectMapper objectMapper) {
        this(userMapper, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    CampusIdentityServiceImpl(UserMapper userMapper, ObjectMapper objectMapper, HttpClient httpClient) {
        this(userMapper, objectMapper, httpClient, "local", "", "/api/identity/users/{studentNo}/exists");
    }

    CampusIdentityServiceImpl(UserMapper userMapper,
                              ObjectMapper objectMapper,
                              HttpClient httpClient,
                              String provider,
                              String identityBaseUrl,
                              String identityPathTemplate) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.provider = provider;
        this.identityBaseUrl = identityBaseUrl;
        this.identityPathTemplate = identityPathTemplate;
    }

    @Override
    public boolean existsByStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            return false;
        }
        if ("http".equalsIgnoreCase(provider)) {
            return existsByHttp(studentNo.trim());
        }
        return userMapper.getUserByStudentNo(studentNo.trim()) != null;
    }

    private boolean existsByHttp(String studentNo) {
        if (identityBaseUrl == null || identityBaseUrl.isBlank()) {
            log.warn("Identity provider=http but app.identity.http.base-url is empty");
            return false;
        }
        String endpoint = buildIdentityEndpoint(studentNo);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            return parseExists(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Identity check interrupted for studentNo={}: {}", studentNo, safeMessage(e));
            return false;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Identity check failed for studentNo={}: {}", studentNo, safeMessage(e));
            return false;
        }
    }

    String buildIdentityEndpoint(String studentNo) {
        String encodedStudentNo = URLEncoder.encode(studentNo, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String path = identityPathTemplate.replace("{studentNo}", encodedStudentNo);
        return String.format(Locale.ROOT, "%s%s", trimTrailingSlash(identityBaseUrl), ensureLeadingSlash(path));
    }

    private boolean parseExists(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isBoolean()) {
                return root.asBoolean(false);
            }
            if (root.has("exists")) {
                return root.path("exists").asBoolean(false);
            }
            if (root.has("data")) {
                JsonNode data = root.path("data");
                if (data.isBoolean()) {
                    return data.asBoolean(false);
                }
                if (data.has("exists")) {
                    return data.path("exists").asBoolean(false);
                }
            }
        } catch (JsonProcessingException ignored) {
        }
        return false;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String ensureLeadingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
