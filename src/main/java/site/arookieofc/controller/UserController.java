package site.arookieofc.controller;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.controller.VO.UserVO;
import site.arookieofc.dao.entity.User;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.BO.Role;
import site.arookieofc.service.UserService;
import site.arookieofc.service.dto.UserDTO;
import site.arookieofc.util.JWTUtils;
import site.arookieofc.util.TokenParseResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/getUser")
    public Result getUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return Result.error("未登录或Token无效");
        }
        Optional<UserDTO> userOpt = userService.getUserByStudentNo(principal.getUsername());
        return userOpt.map(dto -> Result.success(UserVO.fromDTO(dto))).orElseGet(() -> Result.error("用户不存在"));
    }


    @GetMapping("/verifyToken")
    public Result verifyToken(@RequestHeader(value = "Authorization", required = false)
                                  String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.of(401, "Token不能为空", null);
        }
        String token = authHeader.substring(7);
        TokenParseResult result = JWTUtils.parseTokenSafe(token);
        if (result.isSuccess()) {
            return Result.success(null, result.getMessage());
        } else {
            return Result.of(401, result.getMessage(), null);
        }
    }

    @GetMapping("/getUserByStudentNo")
    public Result getUserByStudentNo(@RequestParam("studentNo") String studentNo) {
        Optional<UserDTO> userOpt = userService.getUserByStudentNo(studentNo);
        return userOpt.map(dto -> Result.success(UserVO.fromDTO(dto))).orElseGet(() -> Result.error("User not found"));
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

    @GetMapping("/listAll")
    public Result listAllUsers() {
        java.util.List<UserDTO> users = userService.listAllUsers();
        java.util.List<UserVO> userVOs = users.stream()
                .map(UserVO::fromDTO)
                .collect(java.util.stream.Collectors.toList());
        return Result.success(userVOs);
    }
}