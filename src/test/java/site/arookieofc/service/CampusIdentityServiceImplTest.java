package site.arookieofc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import site.arookieofc.dao.mapper.UserMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampusIdentityServiceImplTest {

    @Test
    void buildIdentityEndpointEncodesStudentNumberPathSegment() {
        CampusIdentityServiceImpl service = newService(httpClient(),
                "https://identity.example/", "/api/users/{studentNo}/exists");

        String endpoint = service.buildIdentityEndpoint("2026/01?x=1");

        assertEquals("https://identity.example/api/users/2026%2F01%3Fx%3D1/exists", endpoint);
    }

    @Test
    void httpIdentityCheckSupportsBooleanAndNestedResponseShapes() throws Exception {
        HttpClient httpClient = httpClient();
        HttpResponse<String> booleanResponse = okResponse("true");
        HttpResponse<String> existsResponse = okResponse("{\"exists\":true}");
        HttpResponse<String> nestedExistsResponse = okResponse("{\"data\":{\"exists\":true}}");
        HttpResponse<String> nestedMissingResponse = okResponse("{\"data\":{\"exists\":false}}");
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler()))
                .thenReturn(booleanResponse)
                .thenReturn(existsResponse)
                .thenReturn(nestedExistsResponse)
                .thenReturn(nestedMissingResponse);
        CampusIdentityServiceImpl service = newService(httpClient);
        String studentNo = "s1";

        assertTrue(service.existsByStudentNo(studentNo));
        assertTrue(service.existsByStudentNo(studentNo));
        assertTrue(service.existsByStudentNo(studentNo));
        assertFalse(service.existsByStudentNo(studentNo));
    }

    @Test
    void httpIdentityCheckReturnsFalseForInvalidJson() throws Exception {
        HttpClient httpClient = httpClient();
        HttpResponse<String> malformedResponse = okResponse("{not-json");
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler()))
                .thenReturn(malformedResponse);
        CampusIdentityServiceImpl service = newService(httpClient);
        String studentNo = "s1";

        assertFalse(service.existsByStudentNo(studentNo));
    }

    @Test
    void httpIdentityCheckRestoresInterruptedFlag() throws Exception {
        HttpClient httpClient = httpClient();
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler()))
                .thenThrow(new InterruptedException("stop"));
        CampusIdentityServiceImpl service = newService(httpClient);
        String studentNo = "s1";

        try {
            assertFalse(service.existsByStudentNo(studentNo));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private CampusIdentityServiceImpl newService(HttpClient httpClient) {
        return newService(httpClient, "https://identity.example", "/api/users/{studentNo}/exists");
    }

    private CampusIdentityServiceImpl newService(HttpClient httpClient, String baseUrl, String pathTemplate) {
        return new CampusIdentityServiceImpl(
                userMapper(), new ObjectMapper(), httpClient, "http", baseUrl, pathTemplate);
    }

    private HttpClient httpClient() {
        return mock(HttpClient.class);
    }

    private UserMapper userMapper() {
        return mock(UserMapper.class);
    }

    private HttpResponse<String> okResponse(String body) {
        HttpResponse<String> response = response();
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    private HttpResponse<String> response() {
        return mock(HttpResponse.class);
    }

    private HttpResponse.BodyHandler<String> stringBodyHandler() {
        return any();
    }
}
