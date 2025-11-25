package site.arookieofc.security;

public class RequestContext {
    private static final ThreadLocal<String> STUDENT_NO = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    public static void setStudentNo(String studentNo) {
        STUDENT_NO.set(studentNo);
    }

    public static String getStudentNo() {
        return STUDENT_NO.get();
    }

    public static void setRole(String role) {
        ROLE.set(role);
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static void clear() {
        STUDENT_NO.remove();
        ROLE.remove();
    }
}
