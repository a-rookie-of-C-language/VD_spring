package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 监控统计专用Mapper
 */
@Mapper
public interface MonitoringMapper {

    /**
     * 获取总用户数
     */
    Long countTotalUsers();

    /**
     * 获取总活动数
     */
    Long countTotalActivities();

    /**
     * 获取已完成活动数
     */
    Long countCompletedActivities();

    /**
     * 获取总志愿时长（所有用户的总时长）
     */
    Double sumTotalDuration();

    /**
     * 获取总参与人次
     */
    Long countTotalParticipants();

    /**
     * 获取指定时间范围内新增的活动数
     */
    Long countNewActivities(@Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);

    /**
     * 获取指定时间范围内的活跃用户数（参与了活动的用户数）
     */
    Long countActiveUsers(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按活动类型分组统计
     */
    List<Map<String, Object>> countByActivityType();

    /**
     * 获取志愿时长Top N用户
     */
    List<Map<String, Object>> getTopUsersByHours(@Param("limit") int limit);

    /**
     * 按日期统计新增活动数
     */
    List<Map<String, Object>> countNewActivitiesByDate(@Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 按日期统计活跃用户数
     */
    List<Map<String, Object>> countActiveUsersByDate(@Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按日期统计完成活动数
     */
    List<Map<String, Object>> countCompletedActivitiesByDate(@Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);

    /**
     * 获取指定时间范围内的新用户数
     */
    Long countNewUsers(@Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime);

    /**
     * 获取指定时间范围内完成的活动数
     */
    Long countCompletedActivitiesInRange(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按年级统计用户志愿数据
     */
    List<Map<String, Object>> getStatisticsByGrade();

    /**
     * 按学院统计用户志愿数据
     */
    List<Map<String, Object>> getStatisticsByCollege();

    /**
     * 按班级统计用户志愿数据（Top N）
     */
    List<Map<String, Object>> getStatisticsByClazz(@Param("limit") int limit);

    /**
     * 获取所有不重复的学院列表
     */
    List<String> getDistinctColleges();

    /**
     * 获取所有不重复的年级列表
     */
    List<String> getDistinctGrades();

    /**
     * 获取所有不重复的班级列表
     */
    List<String> getDistinctClazzes();

    /**
     * 根据筛选条件统计用户数量
     */
    Long countUsersByFilter(@Param("college") String college,
                           @Param("grade") String grade,
                           @Param("clazz") String clazz);

    /**
     * 根据筛选条件统计总志愿时长
     */
    Double sumDurationByFilter(@Param("college") String college,
                               @Param("grade") String grade,
                               @Param("clazz") String clazz);

    /**
     * 根据筛选条件统计参加活动人次
     */
    Long countParticipantsByFilter(@Param("college") String college,
                                   @Param("grade") String grade,
                                   @Param("clazz") String clazz);

    /**
     * 根据筛选条件分页获取用户统计详情
     */
    List<Map<String, Object>> getUserStatsByFilter(@Param("college") String college,
                                                   @Param("grade") String grade,
                                                   @Param("clazz") String clazz,
                                                   @Param("sortField") String sortField,
                                                   @Param("sortOrder") String sortOrder,
                                                   @Param("pageSize") int pageSize,
                                                   @Param("offset") int offset);
}

