package site.arookieofc.service.dto;

import lombok.Builder;
import lombok.Data;
import site.arookieofc.service.BO.Role;
import java.time.OffsetDateTime;

@Data
@Builder
public class UserDTO {
    private String studentNo;
    private String username;
    private String password;
    private Role role;
    private Double totalHours;
    private OffsetDateTime createdAt;
}
