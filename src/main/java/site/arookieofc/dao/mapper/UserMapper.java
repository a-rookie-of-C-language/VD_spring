package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.arookieofc.dao.entity.User;

import java.util.List;


@Mapper
public interface UserMapper {
    User getUserByStudentNo(@Param("studentNo") String studentNo);
    User login(@Param("studentNo") String studentNo, @Param("password") String password);
    List<User> listAll();
    int updateTotalHours(@Param("studentNo") String studentNo, @Param("totalHours") Double totalHours);
}
