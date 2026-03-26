package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.arookieofc.controller.VO.MonitoringDashboardVO;
import site.arookieofc.controller.VO.MonitoringDashboardVO.*;
import site.arookieofc.controller.VO.MonitoringFiltersVO;
import site.arookieofc.controller.VO.MonitoringLogVO;
import site.arookieofc.controller.VO.MonitoringOverviewVO;
import site.arookieofc.controller.VO.UserStatVO;
import site.arookieofc.controller.VO.UserStatPageVO;
import site.arookieofc.dao.mapper.MonitoringMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringService {

    private final MonitoringMapper monitoringMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.logging.es.host:localhost}")
    private String esHost;

    @Value("${app.logging.es.port:9200}")
    private int esPort;

    @Value("${app.logging.es.scheme:http}")
    private String esScheme;

    @Value("${app.logging.es.index-pattern:volunteer-duration-*}")
    private String esIndexPattern;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public MonitoringDashboardVO getDashboardData(String timeRange) {
        // 计算时间范围
        TimeRange range = calculateTimeRange(timeRange);

        // 构建概览数据
        OverviewVO overview = buildOverview(range);

        // 构建分类统计数据
        ClassificationStatsVO classificationStats = buildClassificationStats();

        // 构建活动类型分布
        List<ActivityTypeDistributionVO> activityTypes = buildActivityTypeDistribution();

        // 构建Top10用户排行
        List<TopUserVO> topUsers = buildTopUsers(10);

        // 构建增长榜（可以基于最近参与活动最多的用户或其他指标）
        List<TopUserVO> growthRanking = buildGrowthRanking(range, 10);

        return MonitoringDashboardVO.builder()
                .overview(overview)
                .classificationStats(classificationStats)
                .activityTypes(activityTypes)
                .topUsers(topUsers)
                .growthRanking(growthRanking)
                .build();
    }

    /**
     * 获取筛选选项
     */
    public MonitoringFiltersVO getFilters() {
        List<String> colleges = monitoringMapper.getDistinctColleges();
        List<String> grades = monitoringMapper.getDistinctGrades();
        List<String> clazzes = monitoringMapper.getDistinctClazzes();

        return MonitoringFiltersVO.builder()
                .colleges(colleges)
                .grades(grades)
                .clazzes(clazzes)
                .build();
    }

    /**
     * 获取监控概览数据（支持筛选）
     */
    public MonitoringOverviewVO getOverview(String college, String grade, String clazz) {
        // 统计筛选条件下的用户数量
        Long totalUsers = monitoringMapper.countUsersByFilter(college, grade, clazz);

        // 统计筛选条件下的总时长
        Double totalDuration = monitoringMapper.sumDurationByFilter(college, grade, clazz);

        // 统计筛选条件下的参加活动人次
        Long totalActivities = monitoringMapper.countParticipantsByFilter(college, grade, clazz);

        // 计算平均值
        double averageDuration = (totalUsers != null && totalUsers > 0 && totalDuration != null)
                ? totalDuration / totalUsers : 0.0;

        double averageActivities = (totalUsers != null && totalUsers > 0 && totalActivities != null)
                ? (double) totalActivities / totalUsers : 0.0;

        // 获取已完成的活动数（不受用户筛选影响，全局统计）
        Long completedActivities = monitoringMapper.countCompletedActivities();

        return MonitoringOverviewVO.builder()
                .totalUsers(totalUsers != null ? totalUsers : 0L)
                .totalDuration(totalDuration != null ? Math.round(totalDuration * 10.0) / 10.0 : 0.0)
                .averageDuration(Math.round(averageDuration * 10.0) / 10.0)
                .totalActivities(totalActivities != null ? totalActivities : 0L)
                .averageActivities(Math.round(averageActivities * 10.0) / 10.0)
                .completedActivities(completedActivities != null ? completedActivities : 0L)
                .build();
    }

    /**
     * 获取用户统计详情（分页）
     */
    public UserStatPageVO getUserStats(String college, String grade, String clazz,
                                       String sortField, String sortOrder,
                                       int page, int pageSize) {
        // 计算offset
        int offset = Math.max(0, (page - 1) * pageSize);

        // 获取总数
        Long total = monitoringMapper.countUsersByFilter(college, grade, clazz);

        // 获取分页数据
        List<Map<String, Object>> data = monitoringMapper.getUserStatsByFilter(
                college, grade, clazz, sortField, sortOrder, pageSize, offset);

        // 转换为VO并添加排名
        List<UserStatVO> records = new ArrayList<>();
        int rank = offset + 1;
        for (Map<String, Object> item : data) {
            String studentNo = (String) item.get("studentNo");
            String name = (String) item.get("name");
            String userCollege = (String) item.get("college");
            String userGrade = (String) item.get("grade");
            String userClazz = (String) item.get("clazz");
            Double totalDuration = item.get("totalDuration") != null
                    ? ((Number) item.get("totalDuration")).doubleValue() : 0.0;
            Long activityCount = item.get("activityCount") != null
                    ? ((Number) item.get("activityCount")).longValue() : 0L;

            records.add(UserStatVO.builder()
                    .studentNo(studentNo)
                    .name(name)
                    .college(userCollege)
                    .grade(userGrade)
                    .clazz(userClazz)
                    .totalDuration(Math.round(totalDuration * 10.0) / 10.0)
                    .activityCount(activityCount)
                    .rank(rank++)
                    .build());
        }

        return UserStatPageVO.builder()
                .total(total != null ? total : 0L)
                .current(page)
                .size(pageSize)
                .records(records)
                .build();
    }

    public List<MonitoringLogVO> getRecentLogs(int size, String keyword) {
        int boundedSize = Math.max(1, Math.min(size, 200));
        String endpoint = String.format(
                "%s://%s:%d/%s/_search?ignore_unavailable=true&allow_no_indices=true",
                esScheme,
                esHost,
                esPort,
                esIndexPattern
        );

        String body;
        if (keyword != null && !keyword.isBlank()) {
            body = String.format(
                    Locale.ROOT,
                    "{\"size\":%d,\"sort\":[{\"@timestamp\":{\"order\":\"desc\"}}],\"query\":{\"bool\":{\"should\":[{\"match_phrase\":{\"message\":\"%s\"}},{\"match_phrase\":{\"logger_name\":\"%s\"}},{\"match_phrase\":{\"level\":\"%s\"}}],\"minimum_should_match\":1}}}",
                    boundedSize,
                    escapeJson(keyword),
                    escapeJson(keyword),
                    escapeJson(keyword)
            );
        } else {
            body = String.format(
                    Locale.ROOT,
                    "{\"size\":%d,\"sort\":[{\"@timestamp\":{\"order\":\"desc\"}}],\"query\":{\"match_all\":{}}}",
                    boundedSize
            );
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
                String responseBody = response.body();
                String bodyPreview = responseBody == null ? "" : responseBody.substring(0, Math.min(responseBody.length(), 300));
                throw new IllegalStateException("Elasticsearch query failed: HTTP " + response.statusCode() + ", body=" + bodyPreview);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray()) {
                return Collections.emptyList();
            }

            List<MonitoringLogVO> logs = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                logs.add(MonitoringLogVO.builder()
                        .timestamp(source.path("@timestamp").asText(""))
                        .level(source.path("level").asText("UNKNOWN"))
                        .logger(source.path("logger_name").asText(""))
                        .thread(source.path("thread_name").asText(""))
                        .message(source.path("message").asText(""))
                        .service(source.path("service").asText(""))
                        .environment(source.path("environment").asText(""))
                        .build());
            }
            return logs;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Elasticsearch query interrupted. endpoint={}, size={}, keyword={}", endpoint, boundedSize, safeKeyword(keyword), e);
            return Collections.emptyList();
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = e.getClass().getName();
            }
            log.warn(
                    "Failed to query Elasticsearch logs. endpoint={}, size={}, keyword={}, error={}",
                    endpoint,
                    boundedSize,
                    safeKeyword(keyword),
                    errorMessage,
                    e
            );
            return Collections.emptyList();
        }
    }

    private String safeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String trimmed = keyword.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed;
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 构建概览数据
     */
    private OverviewVO buildOverview(TimeRange range) {
        Long totalUsers = monitoringMapper.countTotalUsers();
        Long totalActivities = monitoringMapper.countTotalActivities();
        Long completedActivities = monitoringMapper.countCompletedActivities();
        Double totalDuration = monitoringMapper.sumTotalDuration();
        Long totalParticipants = monitoringMapper.countTotalParticipants();

        // 计算平均时长（总时长/总用户数）
        double averageDuration = (totalUsers != null && totalUsers > 0 && totalDuration != null)
                ? totalDuration / totalUsers : 0.0;

        // 时间范围内的统计
        Long newActivities = monitoringMapper.countNewActivities(range.start, range.end);
        Long activeUsers = monitoringMapper.countActiveUsers(range.start, range.end);

        return OverviewVO.builder()
                .totalUsers(totalUsers != null ? totalUsers : 0L)
                .totalActivities(totalActivities != null ? totalActivities : 0L)
                .completedActivities(completedActivities != null ? completedActivities : 0L)
                .totalDuration(totalDuration != null ? Math.round(totalDuration * 10.0) / 10.0 : 0.0)
                .totalParticipants(totalParticipants != null ? totalParticipants : 0L)
                .averageDuration(Math.round(averageDuration * 10.0) / 10.0)
                .newActivities(newActivities != null ? newActivities : 0L)
                .activeUsers(activeUsers != null ? activeUsers : 0L)
                .build();
    }

    /**
     * 构建分类统计数据
     */
    private ClassificationStatsVO buildClassificationStats() {
        // 按年级统计
        List<ClassificationItemVO> byGrade = buildClassificationItems(
                monitoringMapper.getStatisticsByGrade());

        // 按学院统计
        List<ClassificationItemVO> byCollege = buildClassificationItems(
                monitoringMapper.getStatisticsByCollege());

        // 按班级统计（Top 10）
        List<ClassificationItemVO> byClazz = buildClassificationItems(
                monitoringMapper.getStatisticsByClazz(10));

        return ClassificationStatsVO.builder()
                .byGrade(byGrade)
                .byCollege(byCollege)
                .byClazz(byClazz)
                .build();
    }

    /**
     * 将数据库查询结果转换为ClassificationItemVO列表
     */
    private List<ClassificationItemVO> buildClassificationItems(List<Map<String, Object>> data) {
        return data.stream()
                .map(item -> {
                    String name = (String) item.get("name");
                    Long userCount = item.get("userCount") != null
                            ? ((Number) item.get("userCount")).longValue() : 0L;
                    Long activityCount = item.get("activityCount") != null
                            ? ((Number) item.get("activityCount")).longValue() : 0L;
                    double totalHours = item.get("totalHours") != null
                            ? ((Number) item.get("totalHours")).doubleValue() : 0.0;
                    double averageHours = item.get("averageHours") != null
                            ? ((Number) item.get("averageHours")).doubleValue() : 0.0;

                    return ClassificationItemVO.builder()
                            .name(name != null ? name : "未分类")
                            .userCount(userCount)
                            .activityCount(activityCount)
                            .totalHours(Math.round(totalHours * 10.0) / 10.0)
                            .averageHours(Math.round(averageHours * 10.0) / 10.0)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建活动类型分布
     */
    private List<ActivityTypeDistributionVO> buildActivityTypeDistribution() {
        List<Map<String, Object>> typeData = monitoringMapper.countByActivityType();

        return typeData.stream()
                .map(item -> {
                    String type = (String) item.get("type");
                    Long count = ((Number) item.get("count")).longValue();
                    // 直接使用枚举值，让前端处理中文映射
                    return ActivityTypeDistributionVO.builder()
                            .name(type)
                            .value(count)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建志愿时长Top用户
     */
    private List<TopUserVO> buildTopUsers(int limit) {
        List<Map<String, Object>> topData = monitoringMapper.getTopUsersByHours(limit);

        List<TopUserVO> result = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> item : topData) {
            String studentNo = (String) item.get("studentNo");
            String name = (String) item.get("name");
            Double hours = item.get("hours") != null ? ((Number) item.get("hours")).doubleValue() : 0.0;

            result.add(TopUserVO.builder()
                    .rank(rank++)
                    .studentNo(studentNo)
                    .name(name != null ? name : studentNo)
                    .hours(Math.round(hours * 10.0) / 10.0)
                    .build());
        }
        return result;
    }

    /**
     * 构建增长榜（时间范围内获得时长最多的用户）
     * 这里简单复用Top用户数据，实际项目中可以根据时间范围计算增量
     */
    private List<TopUserVO> buildGrowthRanking(TimeRange range, int limit) {
        // 简化实现：直接复用总时长排行，实际项目中应该计算时间范围内的增量
        return buildTopUsers(limit);
    }

    /**
     * 计算时间范围
     */
    private TimeRange calculateTimeRange(String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;
        LocalDateTime end = now;

        switch (timeRange) {
            case "daily":
                start = now.toLocalDate().atStartOfDay();
                break;
            case "weekly":
                start = now.minusDays(7).toLocalDate().atStartOfDay();
                break;
            case "monthly":
                start = now.minusDays(30).toLocalDate().atStartOfDay();
                break;
            case "yearly":
                start = now.minusDays(365).toLocalDate().atStartOfDay();
                break;
            default:
                start = now.minusDays(30).toLocalDate().atStartOfDay();
        }

        return new TimeRange(start, end);
    }


    /**
     * 时间范围内部类
     */
    private static class TimeRange {
        final LocalDateTime start;
        final LocalDateTime end;

        TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
