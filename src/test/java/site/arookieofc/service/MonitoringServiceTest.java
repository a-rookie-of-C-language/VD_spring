package site.arookieofc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import site.arookieofc.common.elasticsearch.ElasticsearchTemplate;
import site.arookieofc.controller.VO.MonitoringLogVO;
import site.arookieofc.dao.mapper.MonitoringMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringServiceTest {

    @Test
    void getUserStatsNormalizesPagingAndSortParametersBeforeMapperCall() {
        MonitoringMapper mapper = mapper();
        MonitoringService service = newService(mapper);

        when(mapper.countUsersByFilter(nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(0L);
        when(mapper.getUserStatsByFilter(
                nullable(String.class), nullable(String.class), nullable(String.class),
                any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.getUserStats(null, null, null, "duration", "sideways", -1, 500);

        verify(mapper).getUserStatsByFilter(
                nullable(String.class), nullable(String.class), nullable(String.class),
                eq("totalDuration"), eq("desc"), eq(100), eq(0));
    }

    @Test
    void getRecentLogsClampsSizeAndBuildsEscapedKeywordQuery() throws Exception {
        MonitoringMapper mapper = mapper();
        ElasticsearchTemplate esTemplate = esTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        MonitoringService service = newService(mapper, esTemplate);
        when(esTemplate.search(anyString(), anyString(), anyInt())).thenReturn(objectMapper.readTree("""
                {"hits":{"hits":[{"_source":{"@timestamp":"2026-05-30T10:00:00Z","level":"INFO","logger_name":"demo","message":"ok"}}]}}
                """));

        List<MonitoringLogVO> result = service.getRecentLogs(500, "  foo\"bar\\baz\n  ");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(esTemplate).search(anyString(), bodyCaptor.capture(), anyInt());
        String body = bodyCaptor.getValue();
        JsonNode queryBody = objectMapper.readTree(body);

        assertEquals(1, result.size());
        assertEquals("INFO", result.get(0).getLevel());
        assertEquals("ok", result.get(0).getMessage());
        assertFalse(body.contains("\n"));
        assertEquals(200, queryBody.path("size").asInt());
        assertEquals("foo\"bar\\baz", queryBody
                .path("query").path("bool").path("should").get(0).path("match_phrase").path("message").asText());
    }

    private MonitoringMapper mapper() {
        return mock(MonitoringMapper.class);
    }

    private ElasticsearchTemplate esTemplate() {
        return mock(ElasticsearchTemplate.class);
    }

    private MonitoringService newService(MonitoringMapper mapper) {
        return new MonitoringService(mapper, esTemplate());
    }

    private MonitoringService newService(MonitoringMapper mapper, ElasticsearchTemplate esTemplate) {
        return new MonitoringService(mapper, esTemplate, "logs-*");
    }
}
