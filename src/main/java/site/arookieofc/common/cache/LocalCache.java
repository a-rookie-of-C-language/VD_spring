package site.arookieofc.common.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Thread-safe local cache with built-in protection against:
 *
 * 1. 缓存穿透 (Cache Penetration): Caches null/empty results with a short TTL.
 * 2. 缓存击穿 (Cache Breakdown): computeIfAbsent ensures single-thread rebuild per key.
 * 3. 缓存雪崩 (Cache Avalanche): Random TTL jitter spreads expiration times.
 * 4. 内存溢出 (Unbounded Growth): Max size cap + periodic expired-entry eviction.
 *
 * @param <V> value type
 */
public class LocalCache<V> {

    private static final Object NULL_SENTINEL = new Object();
    private static final int DEFAULT_MAX_SIZE = 10_000;
    private static final int CLEANUP_INTERVAL = 100; // clean every N get() calls

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final long baseTtlMs;
    private final long nullTtlMs;
    private final double jitterRatio;
    private final int maxSize;
    private final LongAdder accessCount = new LongAdder();

    public LocalCache(long baseTtlMs, long nullTtlMs, double jitterRatio, int maxSize) {
        this.baseTtlMs = baseTtlMs;
        this.nullTtlMs = nullTtlMs;
        this.jitterRatio = Math.min(0.5, Math.max(0, jitterRatio));
        this.maxSize = Math.max(100, maxSize);
    }

    public LocalCache(long baseTtlMs, long nullTtlMs, double jitterRatio) {
        this(baseTtlMs, nullTtlMs, jitterRatio, DEFAULT_MAX_SIZE);
    }

    public LocalCache(long baseTtlMs) {
        this(baseTtlMs, baseTtlMs / 5, 0.2);
    }

    /**
     * Get cached value or compute it. Thread-safe.
     */
    @SuppressWarnings("unchecked")
    public V get(String key, Supplier<V> supplier) {
        maybeCleanup();
        CacheEntry entry = store.get(key);

        if (entry != null && !entry.isExpired()) {
            entry.touch(); // LRU: update last access time
            return entry.value == NULL_SENTINEL ? null : (V) entry.value;
        }

        // Evict if at capacity before inserting
        if (store.size() >= maxSize) {
            evictExpired();
            if (store.size() >= maxSize) {
                evictLru();
            }
        }

        CacheEntry computed = store.computeIfAbsent(key, k -> {
            V value = supplier.get();
            long ttl = value == null ? nullTtlMs : jitteredTtl();
            return new CacheEntry(value == null ? NULL_SENTINEL : value, System.currentTimeMillis() + ttl);
        });

        if (computed.isExpired()) {
            store.remove(key, computed);
            return get(key, supplier);
        }

        return computed.value == NULL_SENTINEL ? null : (V) computed.value;
    }

    public void invalidate(String key) {
        store.remove(key);
    }

    public void invalidateAll() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    private void maybeCleanup() {
        accessCount.increment();
        if (accessCount.sum() % CLEANUP_INTERVAL == 0) {
            evictExpired();
        }
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CacheEntry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt < now) {
                it.remove();
            }
        }
    }

    /**
     * Evict the least recently used entry (oldest lastAccessedAt).
     */
    private void evictLru() {
        String lruKey = null;
        long oldestAccess = Long.MAX_VALUE;
        for (Map.Entry<String, CacheEntry> e : store.entrySet()) {
            long accessed = e.getValue().lastAccessedAt;
            if (accessed < oldestAccess) {
                oldestAccess = accessed;
                lruKey = e.getKey();
            }
        }
        if (lruKey != null) store.remove(lruKey);
    }

    private long jitteredTtl() {
        if (jitterRatio == 0) return baseTtlMs;
        double factor = 1.0 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitterRatio;
        return (long) (baseTtlMs * factor);
    }

    private static class CacheEntry {
        final Object value;
        final long expiresAt;
        volatile long lastAccessedAt;

        CacheEntry(Object value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
            this.lastAccessedAt = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
