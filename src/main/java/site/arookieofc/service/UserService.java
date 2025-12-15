package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.dto.UserDTO;
import site.arookieofc.util.JWTUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    public Optional<UserDTO> getUser(String token) {
        String studentNo = JWTUtils.getSubject(token);
        UserDTO dto = Optional.ofNullable(userMapper.getUserByStudentNo(studentNo))
                .map(site.arookieofc.dao.entity.User::toUserDTO)
                .orElse(null);
        return Optional.ofNullable(dto);
    }

    public Optional<UserDTO> login(String studentNo, String password) {
        UserDTO dto = Optional.ofNullable(userMapper.login(studentNo, password))
                .map(site.arookieofc.dao.entity.User::toUserDTO)
                .orElse(null);
        return Optional.ofNullable(dto);
    }

    public Optional<UserDTO> getUserByStudentNo(String studentNo) {
        UserDTO dto = Optional.ofNullable(userMapper.getUserByStudentNo(studentNo))
                .map(User::toUserDTO)
                .orElse(null);
        return Optional.ofNullable(dto);
    }

    public java.util.List<UserDTO> listAllUsers() {
        return userMapper.listAll().stream()
                .map(User::toUserDTO)
                .collect(java.util.stream.Collectors.toList());
    }
}
