package site.arookieofc.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import site.arookieofc.common.exception.BusinessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationGuardsTest {

    @Test
    void isAdminReturnsFalseForNullPrincipalOrRole() {
        assertFalse(AuthorizationGuards.isAdmin(null));
        assertFalse(AuthorizationGuards.isAdmin(principal("u1", null, "User")));
    }

    @Test
    void isAdminAcceptsRoleCaseVariants() {
        assertTrue(AuthorizationGuards.isAdmin(principal("admin", "ADMIN", "Admin")));
        assertTrue(AuthorizationGuards.isAdmin(principal("root", "SUPERADMIN", "Root")));
    }

    @Test
    void requireSelfOrAdminRejectsNullPrincipalAsForbidden() {
        assertThrows(BusinessException.class, () -> AuthorizationGuards.requireSelfOrAdmin(null, "u1"));
    }

    @Test
    void requireStudentNoRejectsMissingIdentity() {
        assertThrows(BusinessException.class, () -> AuthorizationGuards.requireStudentNo(null));
        assertThrows(BusinessException.class, () ->
                AuthorizationGuards.requireStudentNo(principal(" ", "user", "User")));
    }

    @Test
    void requireSuperAdminAcceptsPrincipalRole() {
        AuthorizationGuards.requireSuperAdmin(principal("root", "SUPERADMIN", "Root"), null);
    }

    @Test
    void requireSuperAdminAcceptsSecurityAuthorityFallback() {
        TestingAuthenticationToken authentication = authenticationWithRole("root", "ROLE_SUPERADMIN");

        AuthorizationGuards.requireSuperAdmin(null, authentication);
    }

    @Test
    void requireSuperAdminRejectsNonSuperAdmin() {
        TestingAuthenticationToken authentication = authenticationWithRole("admin", "ROLE_ADMIN");

        assertThrows(BusinessException.class, () -> AuthorizationGuards.requireSuperAdmin(null, authentication));
    }

    private TestingAuthenticationToken authenticationWithRole(String name, String role) {
        return new TestingAuthenticationToken(name, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private UserPrincipal principal(String studentNo, String role, String username) {
        return new UserPrincipal(studentNo, role, username);
    }
}
