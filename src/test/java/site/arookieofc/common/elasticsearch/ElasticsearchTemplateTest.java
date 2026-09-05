package site.arookieofc.common.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchTemplateTest {

    @Test
    void indexRestoresInterruptedFlagWhenHttpSendIsInterrupted() throws Exception {
        HttpClient httpClient = httpClient();
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler()))
                .thenThrow(new InterruptedException("stop"));
        ElasticsearchTemplate template = newTemplate(httpClient);

        try {
            assertFalse(template.index("idx", "{}"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void searchReturnsNullForMalformedJsonResponse() throws Exception {
        HttpClient httpClient = httpClient();
        HttpResponse<String> response = response();
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{not-json");
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler()))
                .thenReturn(response);
        ElasticsearchTemplate template = newTemplate(httpClient);

        assertNull(template.search("idx", "{}", 1));
    }

    private ElasticsearchTemplate newTemplate(HttpClient httpClient) {
        return new ElasticsearchTemplate(new ObjectMapper(), "http", "localhost", 9200, httpClient);
    }

    private HttpClient httpClient() {
        return mock(HttpClient.class);
    }

    private HttpResponse<String> response() {
        return mock(HttpResponse.class);
    }

    private HttpResponse.BodyHandler<String> stringBodyHandler() {
        return any();
    }
}
