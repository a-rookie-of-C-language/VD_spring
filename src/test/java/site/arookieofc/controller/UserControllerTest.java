package site.arookieofc.controller;

import org.junit.jupiter.api.Test;
import site.arookieofc.common.exception.BusinessException;
import site.arookieofc.controller.UserController.LoginRequest;
import site.arookieofc.security.UserPrincipal;
import site.arookieofc.service.UserService;
import site.arookieofc.service.dto.UserDTO;
import site.arookieofc.util.JWTUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void loginRejectsUserRecordWithoutRoleBeforeGeneratingToken() {
        UserService userService = userService();
        JWTUtils jwtUtils = jwtUtils();
        UserController controller = newController(userService, jwtUtils);
        LoginRequest request = new LoginRequest();
        request.setStudentNo("20260001");
        request.setPassword("secret");
        when(userService.login("20260001", "secret")).thenReturn(Optional.of(
                UserDTO.builder()
                        .studentNo("20260001")
                        .username("Alice")
                        .build()));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.login(request));

        assertEquals("USER_TOKEN_DATA_INVALID", exception.getErrorCode());
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void getUserRejectsMissingPrincipalBeforeServiceCall() {
        UserService userService = userService();
        UserController controller = newController(userService);

        assertThrows(BusinessException.class, () -> controller.getUser(null));

        verify(userService, never()).getUserByStudentNo(anyString());
    }

    @Test
    void logoutRejectsBlankStudentNoBeforeTokenVersionIncrement() {
        UserService userService = userService();
        UserController controller = newController(userService);

        assertThrows(BusinessException.class, () ->
                controller.logout(new UserPrincipal(" ", "user", "User")));

        verify(userService, never()).incrementTokenVersion(anyString());
    }

    private UserService userService() {
        return mock(UserService.class);
    }

    private JWTUtils jwtUtils() {
        return mock(JWTUtils.class);
    }

    private UserController newController(UserService userService) {
        return newController(userService, jwtUtils());
    }

    private UserController newController(UserService userService, JWTUtils jwtUtils) {
        return new UserController(userService, jwtUtils);
    }
}
