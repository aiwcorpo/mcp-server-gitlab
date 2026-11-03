package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import pl.aiwcorpo.mcp.platform.resource.ResourceContext;

/**
 * Answers of GitLab that are kept for the duration of one tool call: the group of a listing and its page of projects,
 * which the listing and the resolution of its elements both need.
 *
 * <p>The entries hang on the {@link ResourceContext} the platform opens around each call and are dropped with it.
 * Outside a call (no context) nothing is kept. A failed load is not kept.
 */
final class CallCache {

    private final Map<ResourceContext, Map<String, Object>> entries = Collections.synchronizedMap(new WeakHashMap<>());

    /** Keeps a value for the rest of the call; outside a call nothing is kept. */
    void put(String key, Object value) {
        ResourceContext call = ResourceContext.current().orElse(null);
        if (call != null) {
            entries.computeIfAbsent(call, ignored -> Collections.synchronizedMap(new HashMap<>())).put(key, value);
        }
    }

    /** What the current call kept under {@code key}, {@code null} when nothing. */
    @SuppressWarnings("unchecked")
    <T> T peek(String key) {
        ResourceContext call = ResourceContext.current().orElse(null);
        Map<String, Object> forCall = call == null ? null : entries.get(call);
        return forCall == null ? null : (T) forCall.get(key);
    }

    @SuppressWarnings("unchecked")
    <T> T get(String key, Supplier<T> load) {
        ResourceContext call = ResourceContext.current().orElse(null);
        if (call == null) {
            return load.get();
        }
        Map<String, Object> forCall = entries.computeIfAbsent(call, ignored -> Collections.synchronizedMap(new HashMap<>()));
        Object kept = forCall.get(key);
        if (kept != null) {
            return (T) kept;
        }
        T loaded = load.get();
        forCall.put(key, loaded);
        return loaded;
    }
}
