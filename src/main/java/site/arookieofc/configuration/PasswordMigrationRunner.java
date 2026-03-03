package site.arookieofc.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import site.arookieofc.dao.entity.User;
import site.arookieofc.dao.mapper.UserMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordMigrationRunner implements ApplicationRunner {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        List<User> users = userMapper.listAll();
        int migrated = 0;
        for (User user : users) {
            if (user.getPassword() != null && !user.getPassword().startsWith("$2a$")) {
                String hashed = passwordEncoder.encode(user.getPassword());
                userMapper.updatePassword(user.getStudentNo(), hashed);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("Migrated {} user passwords to BCrypt", migrated);
        }
    }
}
