package site.arookieofc.dao.entity;

import lombok.Builder;
import lombok.Data;
import site.arookieofc.service.BO.Role;
import site.arookieofc.service.dto.UserDTO;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Builder
public class User {
    private String studentNo;
    private String username;
    private String password;
    private Role role;
    private Double totalHours;
    private LocalDateTime createdAt;
    private String clazz;      // 班级
    private String grade;      // 年级
    private String college;    // 学院

    public UserDTO toUserDTO() {
        return UserDTO
                .builder()
                .studentNo(studentNo)
                .username(username)
                .password(password)
                .role(role)
                .totalHours(totalHours)
                .createdAt(createdAt == null ? null : createdAt.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime())
                .clazz(clazz)
                .grade(grade)
                .college(college)
                .build();
    }
}
