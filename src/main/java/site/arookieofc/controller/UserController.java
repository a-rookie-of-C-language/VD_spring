package site.arookieofc.controller;

import io.jsonwebtoken.Claims;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
        Optional<UserDTO> userOpt = userService.getUserByStudentNo(AuthorizationGuards.requireStudentNo(principal));
        return userOpt.map(dto -> Result.success(UserVO.fromDTO(dto)))
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND"));
    }

    @GetMapping("/verifyToken")
    public Result verifyToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_MISSING");
        }
        String token = authHeader.substring(7).trim();
        TokenParseResult parseResult = jwtUtils.parseTokenSafe(token);
        if (!parseResult.isSuccess()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, parseResult.getMessage());
        }
        Claims claims = parseResult.getClaims();
        if (!jwtUtils.isTokenType(claims, JWTUtils.TYPE_ACCESS)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_TYPE_INVALID");
        }
        String studentNo = claims.getSubject();
        int tokenVersion = jwtUtils.getTokenVersion(claims);
        int currentTokenVersion = userService.getUserEntityByStudentNo(studentNo)
                .map(user -> user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                .orElse(-1);
        if (tokenVersion != currentTokenVersion) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED");
        }
        return Result.success(null, "TOKEN_VALID");
    }

    @GetMapping("/getUserByStudentNo")
    public Result getUserByStudentNo(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestParam("studentNo") String studentNo) {
        AuthorizationGuards.requireSelfOrAdmin(principal, studentNo);
        Optional<UserDTO> userOpt = userService.getUserByStudentNo(studentNo);
        return userOpt.map(dto -> Result.success(UserVO.fromDTO(dto)))
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND"));
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        if (request == null
                || request.getStudentNo() == null || request.getStudentNo().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw BusinessException.badRequest("INVALID_CREDENTIALS");
        }
        Optional<UserDTO> userOpt = userService.login(request.getStudentNo(), request.getPassword());
        if (userOpt.isEmpty()) throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");

        return Result.success(buildTokenResponse(userOpt.get()));
    }

    @PostMapping("/refresh")
    public Result refresh(@RequestBody(required = false) RefreshRequest request,
                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String refreshToken = resolveRefreshToken(request, authHeader);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISSING");
        }

        TokenParseResult parseResult = jwtUtils.parseTokenSafe(refreshToken);
        if (!parseResult.isSuccess()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, parseResult.getMessage());
        }
        Claims claims = parseResult.getClaims();
        if (!jwtUtils.isTokenType(claims, JWTUtils.TYPE_REFRESH)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_TYPE_INVALID");
        }

        String studentNo = claims.getSubject();
        int tokenVersion = jwtUtils.getTokenVersion(claims);
        UserDTO user = userService.getUserByStudentNo(studentNo)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        int currentTokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersion != currentTokenVersion) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED");
        }

        return Result.success(buildTokenResponse(user, currentTokenVersion));
    }

    @PostMapping("/logout")
    public Result logout(@AuthenticationPrincipal UserPrincipal principal) {
        userService.incrementTokenVersion(AuthorizationGuards.requireStudentNo(principal));
        return Result.success();
    }

    @GetMapping("/listAll")
    public Result listAllUsers() {
        List<UserDTO> users = userService.listAllUsers();
        List<UserVO> userVOs = users.stream().map(UserVO::fromDTO).collect(Collectors.toList());
        return Result.success(userVOs);
    }

    private String resolveRefreshToken(RefreshRequest request, String authHeader) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            return request.getRefreshToken().trim();
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    private Map<String, Object> buildTokenResponse(UserDTO user) {
        validateTokenUser(user);
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        Map<String, Object> data = buildTokenResponse(user, tokenVersion);
        data.put("studentNo", user.getStudentNo());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        return data;
    }

    private Map<String, Object> buildTokenResponse(UserDTO user, int tokenVersion) {
        validateTokenUser(user);
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole().name());

        String accessToken = jwtUtils.generateAccessToken(user.getStudentNo(), claims, tokenVersion);
        String refreshToken = jwtUtils.generateRefreshToken(user.getStudentNo(), claims, tokenVersion);

        Map<String, Object> data = new HashMap<>();
        data.put("token", accessToken);
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        return data;
    }

    private void validateTokenUser(UserDTO user) {
        if (user == null || user.getStudentNo() == null || user.getStudentNo().isBlank() || user.getRole() == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "USER_TOKEN_DATA_INVALID");
        }
    }

    @Data
    public static class LoginRequest {
        private String studentNo;
        private String password;
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
