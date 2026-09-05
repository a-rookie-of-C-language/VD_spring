package site.arookieofc.security;

import org.springframework.security.core.Authentication;
import site.arookieofc.common.exception.BusinessException;

public final class AuthorizationGuards {
    private AuthorizationGuards() {
    }

    public static boolean isAdmin(UserPrincipal principal) {
        if (principal == null || principal.getRole() == null) {
            return false;
        }
        String role = principal.getRole();
        return "admin".equalsIgnoreCase(role) || "superAdmin".equalsIgnoreCase(role);
    }

    public static void requireAdmin(UserPrincipal principal) {
        if (!isAdmin(principal)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }
    }

    public static boolean isSuperAdmin(UserPrincipal principal) {
        return principal != null
                && principal.getRole() != null
                && "SUPERADMIN".equalsIgnoreCase(principal.getRole());
    }

    public static void requireSuperAdmin(UserPrincipal principal, Authentication authentication) {
        if (isSuperAdmin(principal) || hasSuperAdminAuthority(authentication)) {
            return;
        }
        throw BusinessException.forbidden("FORBIDDEN");
    }

    private static boolean hasSuperAdminAuthority(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPERADMIN".equalsIgnoreCase(authority.getAuthority()));
    }

    public static String requireStudentNo(UserPrincipal principal) {
        if (principal == null || principal.getStudentNo() == null || principal.getStudentNo().isBlank()) {
            throw BusinessException.forbidden("FORBIDDEN");
        }
        return principal.getStudentNo();
    }

    public static void requireSelfOrAdmin(UserPrincipal principal, String ownerStudentNo) {
        if (!isAdmin(principal) && !requireStudentNo(principal).equals(ownerStudentNo)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }
    }
}
