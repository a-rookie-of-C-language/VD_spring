package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Mapper for unified "my activities" queries using SQL UNION ALL.
 * Replaces the in-memory merge of 3 separate queries + memory sort.
 */
@Mapper
public interface MyActivityMapper {

    int countMyActivities(@Param("studentNo") String studentNo);

    List<Map<String, Object>> listMyActivitiesPaged(@Param("studentNo") String studentNo,
                                                     @Param("limit") int limit,
                                                     @Param("offset") int offset);
}
