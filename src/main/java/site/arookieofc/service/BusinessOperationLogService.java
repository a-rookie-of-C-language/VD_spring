package site.arookieofc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import site.arookieofc.common.elasticsearch.ElasticsearchTemplate;
import site.arookieofc.controller.VO.BusinessOperationLogVO;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class BusinessOperationLogService {
    private static final List<String> KEYWORD_FIELDS = List.of(
            "action", "targetName", "operatorStudentNo", "detail", "requestId", "operatorIp");

    private final ObjectMapper objectMapper;
    private final ElasticsearchTemplate esTemplate;

    @Value("${app.logging.es.business-index-prefix:volunteer-business-log}")
    private String businessIndexPrefix;

    @Value("${app.logging.es.business-index-template:volunteer-business-log-template}")
    private String businessIndexTemplate;

    @Value("${app.logging.es.business-ilm-policy:volunteer-business-log-ilm}")
    private String businessIlmPolicy;

    @Value("${app.logging.es.ilm-retention-days:30}")
    private int ilmRetentionDays;

    @Value("${app.logging.es.business-shards:1}")
    private int businessShards;

    @Value("${app.logging.es.business-replicas:1}")
    private int businessReplicas;

    @Value("${app.logging.es.fallback-buffer-size:2000}")
    private int fallbackBufferSize;

    @Value("${app.logging.es.fallback-flush-batch-size:200}")
    private int fallbackFlushBatchSize;

    private final ConcurrentLinkedQueue<BusinessOperationLogVO> fallbackBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean esBootstrapDone = new AtomicBoolean(false);

    public BusinessOperationLogService(ObjectMapper objectMapper, ElasticsearchTemplate esTemplate) {
        this(objectMapper, esTemplate,
                "volunteer-business-log",
                "volunteer-business-log-template",
                "volunteer-business-log-ilm",
                30,
                1,
                1,
                2000,
                200);
    }

    BusinessOperationLogService(ObjectMapper objectMapper,
                                ElasticsearchTemplate esTemplate,
                                String businessIndexPrefix,
                                String businessIndexTemplate,
                                String businessIlmPolicy,
                                int ilmRetentionDays,
                                int businessShards,
                                int businessReplicas,
                                int fallbackBufferSize,
                                int fallbackFlushBatchSize) {
        this.objectMapper = objectMapper;
        this.esTemplate = esTemplate;
        this.businessIndexPrefix = businessIndexPrefix;
        this.businessIndexTemplate = businessIndexTemplate;
        this.businessIlmPolicy = businessIlmPolicy;
        this.ilmRetentionDays = ilmRetentionDays;
        this.businessShards = businessShards;
        this.businessReplicas = businessReplicas;
        this.fallbackBufferSize = fallbackBufferSize;
        this.fallbackFlushBatchSize = fallbackFlushBatchSize;
    }

    @PostConstruct
    public void init() {
        ensureEsLifecycleSetupIfNeeded();
    }

    public void write(BusinessOperationLogVO logVO) {
        // Always buffer — never block the business thread on ES I/O.
        // The scheduled flush handles delivery with backpressure.
        buffer(logVO);
    }

    @Scheduled(fixedDelayString = "${app.logging.es.fallback-flush-ms:3000}")
    public void flushBufferedLogs() {
        if (fallbackBuffer.isEmpty()) return;
        ensureEsLifecycleSetupIfNeeded();
        int flushed = 0;
        while (flushed < Math.max(1, fallbackFlushBatchSize)) {
            BusinessOperationLogVO logVO = fallbackBuffer.poll();
            if (logVO == null) break;
            if (!writeDirect(logVO)) {
                fallbackBuffer.offer(logVO);
                break;
            }
            flushed++;
        }
        if (flushed > 0) {
            log.debug("Flushed {} business logs to ES, remaining={}", flushed, fallbackBuffer.size());
        }
    }

    public List<BusinessOperationLogVO> queryRecent(int size, String keyword) {
        int boundedSize = Math.max(1, Math.min(size, 200));
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String body = buildRecentQueryBody(boundedSize, normalizedKeyword);

        JsonNode root = esTemplate.search(businessIndexPrefix + "-*", body, 5);
        if (root == null) return Collections.emptyList();

        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) return Collections.emptyList();

        List<BusinessOperationLogVO> result = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode src = hit.path("_source");
            result.add(BusinessOperationLogVO.builder()
                    .timestamp(src.path("timestamp").asText(""))
                    .operatorStudentNo(src.path("operatorStudentNo").asText(""))
                    .operatorRole(src.path("operatorRole").asText(""))
                    .operatorIp(src.path("operatorIp").asText(""))
                    .operatorUserAgent(src.path("operatorUserAgent").asText(""))
                    .requestId(src.path("requestId").asText(""))
                    .action(src.path("action").asText(""))
                    .targetType(src.path("targetType").asText(""))
                    .targetId(src.path("targetId").asText(""))
                    .targetName(src.path("targetName").asText(""))
                    .detail(src.path("detail").asText(""))
                    .status(src.path("status").asText(""))
                    .durationMs(src.path("durationMs").asLong(0L))
                    .beforeChange(src.path("beforeChange").asText(""))
                    .afterChange(src.path("afterChange").asText(""))
                    .build());
        }
        return result;
    }

    private String buildRecentQueryBody(int size, String keyword) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", size);

        ArrayNode sort = body.putArray("sort");
        sort.addObject().putObject("timestamp").put("order", "desc");

        ObjectNode query = body.putObject("query");
        if (keyword == null) {
            query.putObject("match_all");
            return body.toString();
        }

        ObjectNode bool = query.putObject("bool");
        ArrayNode should = bool.putArray("should");
        for (String field : KEYWORD_FIELDS) {
            should.addObject().putObject("match_phrase").put(field, keyword);
        }
        bool.put("minimum_should_match", 1);
        return body.toString();
    }

    private void ensureEsLifecycleSetupIfNeeded() {
        if (esBootstrapDone.get()) {
            return;
        }
        synchronized (esBootstrapDone) {
            if (esBootstrapDone.get()) {
                return;
            }
            try {
                createIlmPolicy();
                createIndexTemplate();
                esBootstrapDone.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while bootstrapping business log ILM/template: {}", e.getMessage());
            } catch (IOException | IllegalStateException | IllegalArgumentException e) {
                log.warn("Failed to bootstrap business log ILM/template: {}", e.getMessage());
            }
        }
    }

    private void createIlmPolicy() throws IOException, InterruptedException {
        esTemplate.putJson(esTemplate.endpoint("/_ilm/policy/" + businessIlmPolicy), buildIlmPolicyBody());
    }

    private void createIndexTemplate() throws IOException, InterruptedException {
        esTemplate.putJson(esTemplate.endpoint("/_index_template/" + businessIndexTemplate), buildIndexTemplateBody());
    }

    private String buildIlmPolicyBody() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode phases = root.putObject("policy").putObject("phases");
        phases.putObject("hot").putObject("actions");
        ObjectNode deletePhase = phases.putObject("delete");
        deletePhase.put("min_age", Math.max(1, ilmRetentionDays) + "d");
        deletePhase.putObject("actions").putObject("delete");
        return root.toString();
    }

    private String buildIndexTemplateBody() {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("index_patterns").add(businessIndexPrefix + "-*");

        ObjectNode template = root.putObject("template");
        ObjectNode settings = template.putObject("settings");
        settings.put("number_of_shards", Math.max(1, businessShards));
        settings.put("number_of_replicas", Math.max(0, businessReplicas));
        settings.put("index.lifecycle.name", businessIlmPolicy);

        ObjectNode mappings = template.putObject("mappings");
        mappings.put("dynamic", true);
        ObjectNode properties = mappings.putObject("properties");
        putProperty(properties, "timestamp", "date");
        putProperty(properties, "operatorStudentNo", "keyword");
        putProperty(properties, "operatorRole", "keyword");
        putProperty(properties, "operatorIp", "ip").put("ignore_malformed", true);
        putProperty(properties, "operatorUserAgent", "text");
        putProperty(properties, "requestId", "keyword");
        putProperty(properties, "action", "keyword");
        putProperty(properties, "targetType", "keyword");
        putProperty(properties, "targetId", "keyword");
        putProperty(properties, "targetName", "text");
        putProperty(properties, "detail", "text");
        putProperty(properties, "status", "keyword");
        putProperty(properties, "durationMs", "long");
        putProperty(properties, "beforeChange", "text");
        putProperty(properties, "afterChange", "text");
        return root.toString();
    }

    private ObjectNode putProperty(ObjectNode properties, String name, String type) {
        ObjectNode property = properties.putObject(name);
        property.put("type", type);
        return property;
    }

    private boolean writeDirect(BusinessOperationLogVO logVO) {
        try {
            String payload = objectMapper.writeValueAsString(logVO);
            return esTemplate.index(businessIndexPrefix + "-" + LocalDate.now(), payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to write business operation log: {}", e.getMessage());
            return false;
        }
    }

    private void buffer(BusinessOperationLogVO logVO) {
        if (logVO == null) {
            return;
        }
        int maxBufferSize = Math.max(1, fallbackBufferSize);
        while (fallbackBuffer.size() >= maxBufferSize) {
            if (fallbackBuffer.poll() == null) {
                break;
            }
        }
        fallbackBuffer.offer(logVO);
    }
}
