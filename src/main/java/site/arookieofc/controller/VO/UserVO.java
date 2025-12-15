package site.arookieofc.controller.VO;

import lombok.Builder;
import lombok.Data;
import site.arookieofc.service.BO.Role;
import site.arookieofc.service.dto.UserDTO;

import java.time.OffsetDateTime;

@Data
@Builder
public class UserVO {
    private String studentNo;
    private String username;
    private Role role;
    private Double totalHours;
    private OffsetDateTime createdAt;

    public static UserVO fromDTO(UserDTO dto) {
        return UserVO.builder()
                .studentNo(dto.getStudentNo())
                .username(dto.getUsername())
                .role(dto.getRole())
                .totalHours(dto.getTotalHours())
                .createdAt(dto.getCreatedAt())
                .build();
    }
}
