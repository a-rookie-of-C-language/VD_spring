package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.controller.VO.UserVO;
import site.arookieofc.security.AuthorizationGuards;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.UserService;
import site.arookieofc.service.dto.UserDTO;
import site.arookieofc.util.JWTUtils;
import site.arookieofc.util.TokenParseResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final JWTUtils jwtUtils;

    @GetMapping("/getUser")
    public Result getUser(@AuthenticationPrincipal UserPrincipal principal) {
        Optional<UserDTO> userOpt = userService.getUserByStudentNo(principal.getStudentNo());
        return userOpt.map(dto -> Result.success(UserVO.fromDTO(dto)))
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    @GetMapping("/verifyToken")
    public Result verifyToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Token不能为空");
        }
        String token = authHeader.substring(7);
        TokenParseResult result = jwtUtils.parseTokenSafe(token);
        if (result.isSuccess()) {
            return Result.success(null, result.getMessage());
        } else {
            throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, result.getMessage());
        }
    }

    @GetMapping("/getUserByStudentNo")
    public Result getUserByStudentNo(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestParam("studentNo") String studentNo) {
        AuthorizationGuards.requireSelfOrAdmin(principal, studentNo);

        Optional<UserDTO> userOpt = userService.getUserByStudentNo(studentNo);
        return userOpt.map(dto -> Result.success(UserVO.fromDTO(dto)))
                .orElseThrow(() -> BusinessException.notFound("User not found"));
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        Optional<UserDTO> userOpt = userService.login(request.getStudentNo(), request.getPassword());

        if (userOpt.isPresent()) {
            UserDTO user = userOpt.get();
            Map<String, Object> claims = new HashMap<>();
            claims.put("username", user.getUsername());
            claims.put("role", user.getRole().name());
            String token = jwtUtils.generateToken(user.getStudentNo(), claims);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("studentNo", user.getStudentNo());
            data.put("username", user.getUsername());
            data.put("role", user.getRole());

            return Result.success(data);
        } else {
            throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }

    @GetMapping("/listAll")
    public Result listAllUsers() {
        List<UserDTO> users = userService.listAllUsers();
        List<UserVO> userVOs = users.stream()
                .map(UserVO::fromDTO)
                .collect(Collectors.toList());
        return Result.success(userVOs);
    }

    @lombok.Data
    public static class LoginRequest {
        private String studentNo;
        private String password;
    }
}
