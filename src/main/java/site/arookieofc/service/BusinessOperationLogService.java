package site.arookieofc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.arookieofc.controller.VO.BusinessOperationLogVO;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessOperationLogService {
    private final ObjectMapper objectMapper;

    @Value("${app.logging.es.scheme:http}")
    private String esScheme;

    @Value("${app.logging.es.host:localhost}")
    private String esHost;

    @Value("${app.logging.es.port:9200}")
    private int esPort;

    @Value("${app.logging.es.business-index-prefix:volunteer-business-log}")
    private String businessIndexPrefix;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public void write(BusinessOperationLogVO logVO) {
        String index = businessIndexPrefix + "-" + LocalDate.now();
        String endpoint = String.format(Locale.ROOT, "%s://%s:%d/%s/_doc", esScheme, esHost, esPort, index);
        try {
            String payload = objectMapper.writeValueAsString(logVO);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Failed to write business operation log. httpStatus={}, body={}", response.statusCode(), preview(response.body()));
            }
        } catch (Exception e) {
            log.warn("Failed to write business operation log: {}", safeMessage(e));
        }
    }

    public List<BusinessOperationLogVO> queryRecent(int size, String keyword) {
        int boundedSize = Math.max(1, Math.min(size, 200));
        String endpoint = String.format(
                Locale.ROOT,
                "%s://%s:%d/%s-*/_search?ignore_unavailable=true&allow_no_indices=true",
                esScheme,
                esHost,
                esPort,
                businessIndexPrefix
        );

        String body;
        if (keyword != null && !keyword.isBlank()) {
            String escaped = escapeJson(keyword.trim());
            body = String.format(
                    Locale.ROOT,
                    "{\"size\":%d,\"sort\":[{\"timestamp\":{\"order\":\"desc\"}}],\"query\":{\"bool\":{\"should\":[{\"match_phrase\":{\"action\":\"%s\"}},{\"match_phrase\":{\"targetName\":\"%s\"}},{\"match_phrase\":{\"operatorStudentNo\":\"%s\"}},{\"match_phrase\":{\"detail\":\"%s\"}}],\"minimum_should_match\":1}}}",
                    boundedSize,
                    escaped,
                    escaped,
                    escaped,
                    escaped
            );
        } else {
            body = String.format(Locale.ROOT, "{\"size\":%d,\"sort\":[{\"timestamp\":{\"order\":\"desc\"}}],\"query\":{\"match_all\":{}}}", boundedSize);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Failed to query business operation logs. status={}, body={}", response.statusCode(), preview(response.body()));
                return Collections.emptyList();
            }

            JsonNode hits = objectMapper.readTree(response.body()).path("hits").path("hits");
            if (!hits.isArray()) {
                return Collections.emptyList();
            }

            List<BusinessOperationLogVO> result = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                result.add(BusinessOperationLogVO.builder()
                        .timestamp(source.path("timestamp").asText(""))
                        .operatorStudentNo(source.path("operatorStudentNo").asText(""))
                        .operatorRole(source.path("operatorRole").asText(""))
                        .action(source.path("action").asText(""))
                        .targetType(source.path("targetType").asText(""))
                        .targetId(source.path("targetId").asText(""))
                        .targetName(source.path("targetName").asText(""))
                        .detail(source.path("detail").asText(""))
                        .status(source.path("status").asText(""))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to query business operation logs: {}", safeMessage(e));
            return Collections.emptyList();
        }
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    private String preview(String input) {
        if (input == null) {
            return "";
        }
        return input.length() <= 300 ? input : input.substring(0, 300);
    }
}
