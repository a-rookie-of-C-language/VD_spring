package site.arookieofc.common.cache;

import org.springframework.context.ApplicationEvent;

/**
 * Spring event fired after a write operation to trigger immediate cache invalidation.
 * Listeners (services with caches) subscribe and evict stale entries.
 */
public class CacheInvalidateEvent extends ApplicationEvent {

    public enum Scope {
        ACTIVITY,       // activity created/updated/deleted/enrolled/reviewed
        USER,           // user data changed (hours, profile)
        MONITORING,     // any change that affects dashboard/overview stats
        ALL             // broad invalidation (e.g. batch import)
    }

    private final Scope scope;
    private final String detail;

    public CacheInvalidateEvent(Object source, Scope scope, String detail) {
        super(source);
        this.scope = scope;
        this.detail = detail;
    }

    public CacheInvalidateEvent(Object source, Scope scope) {
        this(source, scope, "");
    }

    public Scope getScope() { return scope; }
    public String getDetail() { return detail; }
}
