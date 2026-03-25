package site.arookieofc.controller;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import site.arookieofc.configuration.SecurityConfig;
import site.arookieofc.security.JwtAuthenticationFilter;
import site.arookieofc.service.ActivityService;
import site.arookieofc.service.BatchImportService;
import site.arookieofc.service.BusinessOperationLogService;
import site.arookieofc.service.FileUploadService;
import site.arookieofc.service.MonitoringService;
import site.arookieofc.service.MyActivityService;
import site.arookieofc.service.PendingActivityService;
import site.arookieofc.service.SuggestionService;
import site.arookieofc.service.UserService;
import site.arookieofc.service.monitor.DeveloperMonitorService;
import site.arookieofc.service.monitor.RequestMetricsCollector;
import site.arookieofc.service.monitor.SystemMetricsWebSocketHandler;
import site.arookieofc.util.JWTUtils;
import site.arookieofc.util.TokenParseResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, SuggestionController.class, PendingActivityController.class, MonitoringController.class})
@AutoConfigureMockMvc(addFilters = true)
@Import(SecurityConfig.class)
class FocusedErrorStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private UserService userService;
    @MockBean
    private SuggestionService suggestionService;
    @MockBean
    private PendingActivityService pendingActivityService;
    @MockBean
    private BatchImportService batchImportService;
    @MockBean
    private MonitoringService monitoringService;
    @MockBean
    private DeveloperMonitorService developerMonitorService;
    @MockBean
    private BusinessOperationLogService businessOperationLogService;
    @MockBean
    private ActivityService activityService;
    @MockBean
    private MyActivityService myActivityService;
    @MockBean
    private FileUploadService fileUploadService;
    @MockBean
    private JWTUtils jwtUtils;
    @MockBean
    private RequestMetricsCollector requestMetricsCollector;
    @MockBean
    private SystemMetricsWebSocketHandler systemMetricsWebSocketHandler;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(2, FilterChain.class)
                    .doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void verifyTokenWithoutBearerReturns401() throws Exception {
        mockMvc.perform(get("/user/verifyToken"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void verifyTokenWithInvalidTokenReturns401() throws Exception {
        when(jwtUtils.parseTokenSafe("bad-token")).thenReturn(TokenParseResult.invalid());

        mockMvc.perform(get("/user/verifyToken").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void loginFailureReturns401() throws Exception {
        when(userService.login("20240001", "wrong")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/user/login")
                        .contentType("application/json")
                        .content("{\"studentNo\":\"20240001\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void suggestionValidationReturns400() throws Exception {
        mockMvc.perform(post("/suggestions")
                        .with(user("user1").roles("USER"))
                        .contentType("application/json")
                        .content("{\"title\":\"\",\"content\":\"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void pendingBatchImportInvalidExtensionReturns400() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "bad.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/pending-activities/batch-import")
                        .file(file)
                        .with(user("user1").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void monitoringInvalidTimeRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/monitoring/dashboard")
                        .with(user("root").roles("SUPERADMIN"))
                        .param("timeRange", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
