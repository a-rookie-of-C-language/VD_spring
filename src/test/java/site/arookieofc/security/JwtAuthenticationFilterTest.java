package site.arookieofc.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import site.arookieofc.dao.mapper.UserMapper;
import site.arookieofc.util.JWTUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidJwtContinuesFilterChainWithoutAuthentication() throws Exception {
        JWTUtils jwtUtils = jwtUtils();
        UserMapper userMapper = userMapper();
        when(jwtUtils.parseToken("bad")).thenThrow(new MalformedJwtException("bad token"));
        JwtAuthenticationFilter filter = newFilter(jwtUtils, userMapper);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBearer("bad"), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void userLookupFailureIsNotSwallowedAsInvalidJwt() throws Exception {
        JWTUtils jwtUtils = jwtUtils();
        UserMapper userMapper = userMapper();
        Claims claims = mock(Claims.class);
        when(jwtUtils.parseToken("valid")).thenReturn(claims);
        when(jwtUtils.isTokenType(claims, JWTUtils.TYPE_ACCESS)).thenReturn(true);
        String subject = "s1";
        when(claims.getSubject()).thenReturn(subject);
        when(userMapper.getUserByStudentNo(subject)).thenThrow(new IllegalStateException("db down"));
        JwtAuthenticationFilter filter = newFilter(jwtUtils, userMapper);

        assertThrows(IllegalStateException.class,
                () -> filter.doFilter(requestWithBearer("valid"), new MockHttpServletResponse(), new MockFilterChain()));
        verify(userMapper).getUserByStudentNo(subject);
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private JWTUtils jwtUtils() {
        return mock(JWTUtils.class);
    }

    private UserMapper userMapper() {
        return mock(UserMapper.class);
    }

    private JwtAuthenticationFilter newFilter(JWTUtils jwtUtils, UserMapper userMapper) {
        return new JwtAuthenticationFilter(jwtUtils, userMapper);
    }
}
