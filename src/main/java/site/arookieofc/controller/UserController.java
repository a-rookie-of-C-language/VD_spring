package site.arookieofc.controller;


import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.service.BO.Role;
import site.arookieofc.service.UserService;
import site.arookieofc.service.dto.UserDTO;
import site.arookieofc.util.JWTUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/getUser")
    public Result getUser(@RequestParam("token") String token) {
        try {
            // Parse token to get user information directly without database query
            Claims claims = JWTUtils.parseToken(token);
            String studentNo = claims.getSubject();
            String username = claims.get("username", String.class);
            String roleStr = claims.get("role", String.class);
            
            if (studentNo == null || username == null || roleStr == null) {
                return Result.error("Missing required user information in token");
            }
            
            // Construct UserDTO from token claims
            UserDTO userDTO = UserDTO.builder()
                    .studentNo(studentNo)
                    .username(username)
                    .role(Role.valueOf(roleStr))
                    .build();
            
            return Result.success(userDTO);
        } catch (Exception e) {
            return Result.error("Failed to parse token: " + e.getMessage());
        }
    }

    @GetMapping("/getUserByStudentNo")
    public Result getUserByStudentNo(@RequestParam("studentNo") String studentNo) {
        Optional<UserDTO> userOpt = userService.getUserByStudentNo(studentNo);
        return userOpt.map(Result::success).orElseGet(() -> Result.error("User not found"));
    }

    @GetMapping("/login")
    public Result login(@RequestParam("studentNo") String studentNo,
                        @RequestParam("password") String password) {
        Optional<UserDTO> userOpt = userService.login(studentNo, password);
        
        if (userOpt.isPresent()) {
            UserDTO user = userOpt.get();
            Map<String, Object> claims = new HashMap<>();
            claims.put("username", user.getUsername());
            claims.put("role", user.getRole().name());
            // Generate token with user info in claims
            String token = JWTUtils.generateToken(studentNo, claims);
            
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("studentNo", user.getStudentNo());
            data.put("username", user.getUsername());
            data.put("role", user.getRole());
            
            return Result.success(data);
        } else {
            return Result.error("Invalid username or password");
        }
    }
}
