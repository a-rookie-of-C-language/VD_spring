package site.arookieofc.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaginationUtilsTest {

    @Test
    void normalizePageDefaultsAndBoundsLowerValues() {
        assertEquals(1, PaginationUtils.normalizePage((Integer) null));
        assertEquals(1, PaginationUtils.normalizePage(0));
        assertEquals(1, PaginationUtils.normalizePage(-5));
        assertEquals(3, PaginationUtils.normalizePage(3));
    }

    @Test
    void normalizePageSizeDefaultsAndCapsLargeValues() {
        assertEquals(10, PaginationUtils.normalizePageSize((Integer) null));
        assertEquals(1, PaginationUtils.normalizePageSize(0));
        assertEquals(1, PaginationUtils.normalizePageSize(-20));
        assertEquals(100, PaginationUtils.normalizePageSize(500));
        assertEquals(50, PaginationUtils.normalizePageSize(500, 50));
        assertEquals(5, PaginationUtils.normalizePageSize(null, 5));
    }

    @Test
    void offsetNormalizesPageAndCapsOverflow() {
        assertEquals(0, PaginationUtils.offset(-1, 10));
        assertEquals(20, PaginationUtils.offset(3, 10));
        assertEquals(Integer.MAX_VALUE, PaginationUtils.offset(Integer.MAX_VALUE, 100));
    }
}
