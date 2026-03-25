package site.arookieofc.security;

import site.arookieofc.common.exception.BusinessException;

public final class AuthorizationGuards {
    private AuthorizationGuards() {
    }

    public static boolean isAdmin(UserPrincipal principal) {
        String role = principal.getRole();
        return "admin".equals(role) || "superAdmin".equals(role);
    }

    public static void requireAdmin(UserPrincipal principal) {
        if (!isAdmin(principal)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }
    }

    public static void requireSelfOrAdmin(UserPrincipal principal, String ownerStudentNo) {
        if (!isAdmin(principal) && !principal.getStudentNo().equals(ownerStudentNo)) {
            throw BusinessException.forbidden("FORBIDDEN");
        }
    }
}
