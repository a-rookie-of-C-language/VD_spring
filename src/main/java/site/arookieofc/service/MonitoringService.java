package site.arookieofc.service;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import site.arookieofc.common.cache.CacheInvalidateEvent;
import site.arookieofc.common.elasticsearch.ElasticsearchTemplate;
import site.arookieofc.controller.VO.MonitoringDashboardVO;
import site.arookieofc.controller.VO.MonitoringDashboardVO.*;
import site.arookieofc.controller.VO.MonitoringFiltersVO;
import site.arookieofc.controller.VO.MonitoringLogVO;
import site.arookieofc.controller.VO.MonitoringOverviewVO;
import site.arookieofc.controller.VO.UserStatVO;
import site.arookieofc.controller.VO.UserStatPageVO;
import site.arookieofc.dao.mapper.MonitoringMapper;

import site.arookieofc.common.cache.LocalCache;
import site.arookieofc.util.PaginationUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MonitoringService {
    private static final List<String> LOG_KEYWORD_FIELDS = List.of("message", "logger_name", "level");

    private final MonitoringMapper monitoringMapper;
    private final ElasticsearchTemplate esTemplate;

    @Value("${app.logging.es.index-pattern:volunteer-duration-*}")
    private String esIndexPattern;

    // 30s TTL, 5s null TTL (penetration), ±20% jitter (avalanche), computeIfAbsent (breakdown)
    private final LocalCache<MonitoringDashboardVO> dashboardCache = new LocalCache<>(30_000, 5_000, 0.2);
    private final LocalCache<MonitoringOverviewVO> overviewCache = new LocalCache<>(30_000, 5_000, 0.2);
    private final LocalCache<MonitoringFiltersVO> filtersCache = new LocalCache<>(300_000, 30_000, 0.2);

    public MonitoringService(MonitoringMapper monitoringMapper, ElasticsearchTemplate esTemplate) {
        this(monitoringMapper, esTemplate, "volunteer-duration-*");
    }

    MonitoringService(MonitoringMapper monitoringMapper, ElasticsearchTemplate esTemplate, String esIndexPattern) {
        this.monitoringMapper = monitoringMapper;
        this.esTemplate = esTemplate;
        this.esIndexPattern = esIndexPattern;
    }

    @EventListener
    public void onCacheInvalidate(CacheInvalidateEvent event) {
        if (event.getScope() == CacheInvalidateEvent.Scope.MONITORING
                || event.getScope() == CacheInvalidateEvent.Scope.ACTIVITY
                || event.getScope() == CacheInvalidateEvent.Scope.ALL) {
            dashboardCache.invalidateAll();
            overviewCache.invalidateAll();
        }
    }

    public MonitoringDashboardVO getDashboardData(String timeRange) {
        return dashboardCache.get("dashboard:" + timeRange, () -> {
            TimeRange range = calculateTimeRange(timeRange);
            return MonitoringDashboardVO.builder()
                    .overview(buildOverview(range))
                    .classificationStats(buildClassificationStats())
                    .activityTypes(buildActivityTypeDistribution())
                    .topUsers(buildTopUsers(10))
                    .growthRanking(buildTopUsers(10))
                    .build();
        });
    }

    public MonitoringFiltersVO getFilters() {
        return filtersCache.get("filters", () -> MonitoringFiltersVO.builder()
                .colleges(monitoringMapper.getDistinctColleges())
                .grades(monitoringMapper.getDistinctGrades())
                .clazzes(monitoringMapper.getDistinctClazzes())
                .build());
    }

    public MonitoringOverviewVO getOverview(String college, String grade, String clazz) {
        String key = "overview:" + college + "|" + grade + "|" + clazz;
        return overviewCache.get(key, () -> {
            Long totalUsers = monitoringMapper.countUsersByFilter(college, grade, clazz);
            Double totalDuration = monitoringMapper.sumDurationByFilter(college, grade, clazz);
            Long totalActivities = monitoringMapper.countParticipantsByFilter(college, grade, clazz);
            Long completedActivities = monitoringMapper.countCompletedActivities();
            double avgDur = (totalUsers != null && totalUsers > 0 && totalDuration != null) ? totalDuration / totalUsers : 0.0;
            double avgAct = (totalUsers != null && totalUsers > 0 && totalActivities != null) ? (double) totalActivities / totalUsers : 0.0;
            return MonitoringOverviewVO.builder()
                    .totalUsers(totalUsers != null ? totalUsers : 0L)
                    .totalDuration(totalDuration != null ? Math.round(totalDuration * 10.0) / 10.0 : 0.0)
                    .averageDuration(Math.round(avgDur * 10.0) / 10.0)
                    .totalActivities(totalActivities != null ? totalActivities : 0L)
                    .averageActivities(Math.round(avgAct * 10.0) / 10.0)
                    .completedActivities(completedActivities != null ? completedActivities : 0L)
                    .build();
        });
    }

    /**
     * 获取用户统计详情（分页）
     */
    public UserStatPageVO getUserStats(String college, String grade, String clazz,
                                       String sortField, String sortOrder,
                                       int page, int pageSize) {
        // 计算offset
        int safePage = PaginationUtils.normalizePage(page);
        int safePageSize = PaginationUtils.normalizePageSize(pageSize);
        int offset = PaginationUtils.offset(safePage, safePageSize);
        String safeSortField = normalizeSortField(sortField);
        String safeSortOrder = normalizeSortOrder(sortOrder);

        // 获取总数
        Long total = monitoringMapper.countUsersByFilter(college, grade, clazz);

        // 获取分页数据
        List<Map<String, Object>> data = monitoringMapper.getUserStatsByFilter(
                college, grade, clazz, safeSortField, safeSortOrder, safePageSize, offset);

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
                .current(safePage)
                .size(safePageSize)
                .records(records)
                .build();
    }

    private String normalizeSortField(String sortField) {
        if ("duration".equals(sortField) || "totalDuration".equals(sortField)) {
            return "totalDuration";
        }
        if ("activityCount".equals(sortField)) {
            return "activityCount";
        }
        return null;
    }

    private String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }

    public List<MonitoringLogVO> getRecentLogs(int size, String keyword) {
        int boundedSize = Math.max(1, Math.min(size, 200));
        String normalizedKeyword = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        String body = buildRecentLogsQueryBody(boundedSize, normalizedKeyword);

        JsonNode root = esTemplate.search(esIndexPattern, body, 5);
        if (root == null) return Collections.emptyList();

        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) return Collections.emptyList();

        List<MonitoringLogVO> logs = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode src = hit.path("_source");
            logs.add(MonitoringLogVO.builder()
                    .timestamp(src.path("@timestamp").asText(""))
                    .level(src.path("level").asText("UNKNOWN"))
                    .logger(src.path("logger_name").asText(""))
                    .thread(src.path("thread_name").asText(""))
                    .message(src.path("message").asText(""))
                    .service(src.path("service").asText(""))
                    .environment(src.path("environment").asText(""))
                    .build());
        }
        return logs;
    }

    private String buildRecentLogsQueryBody(int size, String keyword) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("size", size);

        ArrayNode sort = body.putArray("sort");
        sort.addObject().putObject("@timestamp").put("order", "desc");

        ObjectNode query = body.putObject("query");
        if (keyword == null) {
            query.putObject("match_all");
            return body.toString();
        }

        ObjectNode bool = query.putObject("bool");
        ArrayNode should = bool.putArray("should");
        for (String field : LOG_KEYWORD_FIELDS) {
            should.addObject().putObject("match_phrase").put(field, keyword);
        }
        bool.put("minimum_should_match", 1);
        return body.toString();
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

    private List<TopUserVO> buildTopUsers(int limit) {
        List<Map<String, Object>> topData = monitoringMapper.getTopUsersByHours(limit);
        List<TopUserVO> result = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> item : topData) {
            String studentNo = (String) item.get("studentNo");
            String name = (String) item.get("name");
            double hours = item.get("hours") != null ? ((Number) item.get("hours")).doubleValue() : 0.0;
            result.add(TopUserVO.builder()
                    .rank(rank++).studentNo(studentNo)
                    .name(name != null ? name : studentNo)
                    .hours(Math.round(hours * 10.0) / 10.0).build());
        }
        return result;
    }

    private static final Map<String, Long> TIME_RANGE_DAYS = Map.of(
            "daily", 1L, "weekly", 7L, "monthly", 30L, "yearly", 365L);

    private TimeRange calculateTimeRange(String timeRange) {
        long days = TIME_RANGE_DAYS.getOrDefault(timeRange, 30L);
        LocalDateTime now = LocalDateTime.now();
        return new TimeRange(now.minusDays(days).toLocalDate().atStartOfDay(), now);
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
