package site.arookieofc.util;

public final class PaginationUtils {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
    }

    public static int normalizePage(Integer page) {
        return page == null ? DEFAULT_PAGE : normalizePage(page.intValue());
    }

    public static int normalizePage(int page) {
        return Math.max(DEFAULT_PAGE, page);
    }

    public static int normalizePageSize(Integer pageSize) {
        return pageSize == null ? DEFAULT_PAGE_SIZE : normalizePageSize(pageSize.intValue());
    }

    public static int normalizePageSize(int pageSize) {
        return normalizePageSize(pageSize, DEFAULT_MAX_PAGE_SIZE);
    }

    public static int normalizePageSize(Integer pageSize, int maxPageSize) {
        return normalizePageSize(pageSize == null ? DEFAULT_PAGE_SIZE : pageSize.intValue(), maxPageSize);
    }

    public static int normalizePageSize(int pageSize, int maxPageSize) {
        int safeMax = Math.max(1, maxPageSize);
        return Math.max(1, Math.min(pageSize, safeMax));
    }

    public static int offset(int page, int pageSize) {
        long offset = (long) (normalizePage(page) - 1) * Math.max(1, pageSize);
        return offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset;
    }
}
