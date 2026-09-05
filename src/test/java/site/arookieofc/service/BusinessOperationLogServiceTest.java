package site.arookieofc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import site.arookieofc.common.elasticsearch.ElasticsearchTemplate;
import site.arookieofc.controller.VO.BusinessOperationLogVO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessOperationLogServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryRecentClampsSizeAndBuildsEscapedKeywordQuery() throws Exception {
        ElasticsearchTemplate esTemplate = esTemplate();
        BusinessOperationLogService service = newDefaultService(esTemplate);
        when(esTemplate.search(anyString(), anyString(), anyInt())).thenReturn(objectMapper.readTree("""
                {"hits":{"hits":[{"_source":{"action":"update","targetName":"Activity","durationMs":12}}]}}
                """));

        List<BusinessOperationLogVO> result = service.queryRecent(500, "  foo\"bar\\baz\n  ");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(esTemplate).search(anyString(), bodyCaptor.capture(), anyInt());
        String body = bodyCaptor.getValue();
        JsonNode queryBody = objectMapper.readTree(body);

        assertEquals(1, result.size());
        assertEquals("update", result.get(0).getAction());
        assertEquals("Activity", result.get(0).getTargetName());
        assertFalse(body.contains("\n"));
        assertEquals(200, queryBody.path("size").asInt());
        assertEquals("foo\"bar\\baz", queryBody
                .path("query").path("bool").path("should").get(0).path("match_phrase").path("action").asText());
    }

    @Test
    void writeUsesMinimumFallbackBufferCapacityWhenConfiguredSizeIsInvalid() {
        ElasticsearchTemplate esTemplate = esTemplate();
        BusinessOperationLogService service = newService(esTemplate, 0, 1);
        when(esTemplate.index(anyString(), anyString())).thenReturn(true);

        service.write(log("create"));
        service.flushBufferedLogs();

        verify(esTemplate).index(contains("business-log-"), anyString());
    }

    @Test
    void initBuildsStructuredIlmAndIndexTemplateBodies() throws Exception {
        ElasticsearchTemplate esTemplate = esTemplate();
        BusinessOperationLogService service = newService(esTemplate, 0, 0, -1);
        when(esTemplate.endpoint(anyString())).thenAnswer(invocation -> "http://es" + invocation.getArgument(0));

        service.init();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(esTemplate, times(2)).putJson(anyString(), bodyCaptor.capture());
        List<String> bodies = bodyCaptor.getAllValues();
        JsonNode ilmBody = objectMapper.readTree(bodies.get(0));
        JsonNode templateBody = objectMapper.readTree(bodies.get(1));

        assertEquals("1d", ilmBody.path("policy").path("phases").path("delete").path("min_age").asText());
        assertEquals("business-log-*", templateBody.path("index_patterns").get(0).asText());
        assertEquals(1, templateBody
                .path("template").path("settings").path("number_of_shards").asInt());
        assertEquals(0, templateBody
                .path("template").path("settings").path("number_of_replicas").asInt());
        assertEquals("keyword", templateBody
                .path("template").path("mappings").path("properties").path("operatorStudentNo").path("type").asText());
    }

    @Test
    void initRestoresInterruptedFlagWhenEsBootstrapIsInterrupted() throws Exception {
        ElasticsearchTemplate esTemplate = esTemplate();
        BusinessOperationLogService service = newService(esTemplate, 30, 1, 1);
        when(esTemplate.endpoint(anyString())).thenAnswer(invocation -> "http://es" + invocation.getArgument(0));
        doThrow(new InterruptedException("stop"))
                .doNothing()
                .when(esTemplate).putJson(anyString(), anyString());

        try {
            service.init();

            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        service.init();
        verify(esTemplate, times(3)).putJson(anyString(), anyString());
    }

    @Test
    void flushKeepsBufferedLogWhenSerializationFailsThenRetriesLater() throws Exception {
        ElasticsearchTemplate esTemplate = esTemplate();
        ObjectMapper objectMapper = spy(new ObjectMapper());
        BusinessOperationLogService service = newService(objectMapper, esTemplate, 10, 1);
        doThrow(new JsonProcessingException("bad") {})
                .doReturn("{\"action\":\"create\"}")
                .when(objectMapper).writeValueAsString(any(BusinessOperationLogVO.class));
        when(esTemplate.index(anyString(), anyString())).thenReturn(true);

        service.write(log("create"));
        service.flushBufferedLogs();
        verify(esTemplate, never()).index(anyString(), anyString());

        service.flushBufferedLogs();
        verify(esTemplate).index(contains("business-log-"), anyString());
    }

    private ElasticsearchTemplate esTemplate() {
        return mock(ElasticsearchTemplate.class);
    }

    private BusinessOperationLogVO log(String action) {
        return BusinessOperationLogVO.builder()
                .action(action)
                .build();
    }

    private BusinessOperationLogService newDefaultService(ElasticsearchTemplate esTemplate) {
        return new BusinessOperationLogService(objectMapper, esTemplate);
    }

    private BusinessOperationLogService newService(ElasticsearchTemplate esTemplate,
                                                   int ilmRetentionDays,
                                                   int businessShards,
                                                   int businessReplicas) {
        return newService(objectMapper, esTemplate, ilmRetentionDays, businessShards, businessReplicas, 2000, 200);
    }

    private BusinessOperationLogService newService(ElasticsearchTemplate esTemplate,
                                                   int fallbackBufferSize,
                                                   int fallbackFlushBatchSize) {
        return newService(objectMapper, esTemplate, 30, 1, 1, fallbackBufferSize, fallbackFlushBatchSize);
    }

    private BusinessOperationLogService newService(ObjectMapper objectMapper,
                                                   ElasticsearchTemplate esTemplate,
                                                   int fallbackBufferSize,
                                                   int fallbackFlushBatchSize) {
        return newService(objectMapper, esTemplate, 30, 1, 1, fallbackBufferSize, fallbackFlushBatchSize);
    }

    private BusinessOperationLogService newService(ObjectMapper objectMapper,
                                                   ElasticsearchTemplate esTemplate,
                                                   int ilmRetentionDays,
                                                   int businessShards,
                                                   int businessReplicas,
                                                   int fallbackBufferSize,
                                                   int fallbackFlushBatchSize) {
        return new BusinessOperationLogService(
                objectMapper, esTemplate, "business-log", "business-template", "business-ilm",
                ilmRetentionDays, businessShards, businessReplicas, fallbackBufferSize, fallbackFlushBatchSize);
    }
}
