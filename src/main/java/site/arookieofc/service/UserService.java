package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.service.dto.UserDTO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public Optional<UserDTO> getUserByStudentNo(String studentNo) {
        return Optional.ofNullable(userMapper.getUserByStudentNo(studentNo))
                .map(User::toUserDTO);
    }

    public Optional<UserDTO> login(String studentNo, String password) {
        User user = userMapper.getUserByStudentNo(studentNo);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return Optional.empty();
        }
        return Optional.of(user.toUserDTO());
    }

    public List<UserDTO> listAllUsers() {
        return userMapper.listAll().stream()
                .map(User::toUserDTO)
                .collect(Collectors.toList());
    }
}
