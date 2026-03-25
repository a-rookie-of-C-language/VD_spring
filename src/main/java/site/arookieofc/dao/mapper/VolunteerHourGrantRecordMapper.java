package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.arookieofc.dao.entity.VolunteerHourGrantRecord;

@Mapper
public interface VolunteerHourGrantRecordMapper {
    int insert(VolunteerHourGrantRecord record);

    VolunteerHourGrantRecord getByUniqueKey(@Param("studentNo") String studentNo,
                                            @Param("sourceType") String sourceType,
                                            @Param("sourceId") String sourceId);
}
