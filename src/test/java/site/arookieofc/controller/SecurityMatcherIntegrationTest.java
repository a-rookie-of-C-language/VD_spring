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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import site.arookieofc.configuration.SecurityConfig;
import site.arookieofc.security.JwtAuthenticationFilter;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.ActivityService;
import site.arookieofc.service.BatchImportService;
import site.arookieofc.service.BusinessOperationLogService;
import site.arookieofc.service.FileUploadService;
import site.arookieofc.service.MonitoringService;
import site.arookieofc.service.MyActivityService;
import site.arookieofc.service.PendingActivityService;
import site.arookieofc.service.UserService;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.monitor.DeveloperMonitorService;
import site.arookieofc.service.monitor.RequestMetricsCollector;
import site.arookieofc.service.monitor.SystemMetricsWebSocketHandler;
import site.arookieofc.util.JWTUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MonitoringController.class, UserController.class, ActivityController.class})
@AutoConfigureMockMvc(addFilters = true)
@Import(SecurityConfig.class)
class SecurityMatcherIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private MonitoringService monitoringService;

    @MockBean
    private DeveloperMonitorService developerMonitorService;

    @MockBean
    private BusinessOperationLogService businessOperationLogService;

    @MockBean
    private UserService userService;

    @MockBean
    private ActivityService activityService;

    @MockBean
    private PendingActivityService pendingActivityService;

    @MockBean
    private BatchImportService batchImportService;

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
    void monitoringAliasRequiresSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/monitoring/dashboard"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/monitoring/dashboard")
                        .with(user("user1").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/monitoring/dashboard")
                        .with(user("root").roles("SUPERADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void monitoringAliasBusinessLogsRequiresSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/monitoring/business-logs")
                        .with(user("user1").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/monitoring/business-logs")
                        .with(user("root").roles("SUPERADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void monitoringAliasDeveloperMetricsRequiresSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/monitoring/developer-metrics")
                        .with(user("user1").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/monitoring/developer-metrics")
                        .with(user("root").roles("SUPERADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void monitoringPrimaryPathAlsoRequiresSuperAdmin() throws Exception {
        mockMvc.perform(get("/monitoring/dashboard")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/monitoring/dashboard")
                        .with(user("root").roles("SUPERADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void userListAllRequiresAdminOrSuperAdmin() throws Exception {
        mockMvc.perform(get("/user/listAll")
                        .with(user("user1").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/user/listAll")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void refreshStatusesRequiresAdminOrSuperAdmin() throws Exception {
        mockMvc.perform(post("/activities/refreshStatuses")
                        .with(user("user1").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/activities/refreshStatuses")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void attachmentUtilityEndpointsRequireAdminOrSuperAdmin() throws Exception {
        mockMvc.perform(get("/activities/attachment/info").param("filePath", "attachments/demo.txt")
                        .with(user("user1").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/activities/attachment/info").param("filePath", "attachments/demo.txt")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void attachmentDeleteRequiresAdminOrSuperAdmin() throws Exception {
        mockMvc.perform(delete("/activities/attachment").param("filePath", "attachments/demo.txt")
                        .with(user("user1").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/activities/attachment").param("filePath", "attachments/demo.txt")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void activityCreateRejectsPlainUserButAllowsFunctionary() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("coverFile", "", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/activities")
                        .file(emptyFile)
                        .param("name", "Test Activity")
                        .param("type", "COMMUNITY_SERVICE")
                        .with(authentication(new UserPrincipal("user1", "user", "User One"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        ActivityDTO created = ActivityDTO.builder()
                .id("a1")
                .functionary("leader")
                .name("Test Activity")
                .build();
        when(activityService.createActivity(any(ActivityDTO.class))).thenReturn(created);

        mockMvc.perform(multipart("/activities")
                        .file(emptyFile)
                        .param("name", "Test Activity")
                        .param("type", "COMMUNITY_SERVICE")
                        .with(authentication(new UserPrincipal("leader", "functionary", "Leader"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.functionary").value("leader"));
    }

    @Test
    void activityDeleteRequiresOwnerOrAdmin() throws Exception {
        ActivityDTO ownedByLeader = ActivityDTO.builder()
                .id("a1")
                .functionary("leader")
                .build();
        when(activityService.getActivityById("a1")).thenReturn(ownedByLeader);

        mockMvc.perform(delete("/activities/a1")
                        .with(authentication(new UserPrincipal("other", "functionary", "Other"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(delete("/activities/a1")
                        .with(authentication(new UserPrincipal("leader", "functionary", "Leader"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/activities/a1")
                        .with(authentication(new UserPrincipal("admin", "admin", "Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
