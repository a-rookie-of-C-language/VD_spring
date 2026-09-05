package site.arookieofc.common.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Reusable Elasticsearch HTTP operations.
 * Eliminates duplicated HttpClient setup, endpoint building, and error handling
 * across MonitoringService and BusinessOperationLogService.
 */
@Slf4j
@Component
public class ElasticsearchTemplate {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String scheme;
    private final String host;
    private final int port;

    @Autowired
    public ElasticsearchTemplate(ObjectMapper objectMapper,
                                 @Value("${app.logging.es.scheme:http}") String scheme,
                                 @Value("${app.logging.es.host:localhost}") String host,
                                 @Value("${app.logging.es.port:9200}") int port) {
        this(objectMapper, scheme, host, port, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    ElasticsearchTemplate(ObjectMapper objectMapper,
                          String scheme,
                          String host,
                          int port,
                          HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.httpClient = httpClient;
    }

    /**
     * Execute a _search query and return the parsed root JsonNode.
     */
    public JsonNode search(String indexPattern, String jsonBody, int timeoutSeconds) {
        String endpoint = String.format(Locale.ROOT,
                "%s://%s:%d/%s/_search?ignore_unavailable=true&allow_no_indices=true",
                scheme, host, port, indexPattern);
        return postJson(endpoint, jsonBody, timeoutSeconds);
    }

    /**
     * Index a single document (POST /{index}/_doc).
     */
    public boolean index(String indexName, String jsonBody) {
        String endpoint = String.format(Locale.ROOT, "%s://%s:%d/%s/_doc", scheme, host, port, indexName);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("ES index failed. status={}, body={}", response.statusCode(), preview(response.body()));
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("ES index interrupted: {}", safeMsg(e));
            return false;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("ES index error: {}", safeMsg(e));
            return false;
        }
    }

    /**
     * PUT a JSON body to an arbitrary ES endpoint (for ILM policies, index templates, etc.).
     */
    public void putJson(String endpoint, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("ES PUT failed: HTTP " + response.statusCode() + " body=" + preview(response.body()));
        }
    }

    /**
     * Build a full ES endpoint URL from a path (e.g. /_ilm/policy/my-policy).
     */
    public String endpoint(String path) {
        return String.format(Locale.ROOT, "%s://%s:%d%s", scheme, host, port,
                path.startsWith("/") ? path : "/" + path);
    }

    private JsonNode postJson(String endpoint, String body, int timeoutSeconds) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("ES query failed: HTTP {}, body={}", response.statusCode(), preview(response.body()));
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("ES query interrupted: {}", safeMsg(e));
            return null;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("ES query error: {}", safeMsg(e));
            return null;
        }
    }

    private static String safeMsg(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    private static String preview(String s) {
        return (s == null || s.length() <= 300) ? s : s.substring(0, 300);
    }
}
