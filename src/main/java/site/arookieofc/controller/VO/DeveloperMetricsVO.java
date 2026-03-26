package site.arookieofc.controller.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeveloperMetricsVO {
    private String timestamp;
    private String backendStatus;
    private double systemCpuUsage;
    private double processCpuUsage;
    private double jvmMemoryUsage;
    private double systemMemoryUsage;
    private long heapUsedMb;
    private long heapMaxMb;
    private double qps;
    private long totalRequests;
    private int websocketClients;
    private MiddlewareStatusVO mysql;
    private MiddlewareStatusVO rabbitmq;
    private MiddlewareStatusVO elasticsearch;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MiddlewareStatusVO {
        private String status;
        private String detail;
    }
}
