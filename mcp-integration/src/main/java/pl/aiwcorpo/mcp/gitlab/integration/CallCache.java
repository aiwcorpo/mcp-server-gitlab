package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import pl.aiwcorpo.mcp.platform.resource.ResourceContext;

/**
 * Answers of GitLab that are kept for the duration of one tool call. The policy needs the topics of every group on
 * the path of every project of a result, and projects of one group share all of them, so without this a listing of
 * 100 projects would ask GitLab for the same ancestors 100 times.
 *
 * <p>The entries hang on the {@link ResourceContext} the platform opens around each call and are dropped with it.
 * Outside a call (no context) nothing is kept. A failed load is not kept.
 */
final class CallCache {

    private final Map<ResourceContext, Map<String, Object>> entries = Collections.synchronizedMap(new WeakHashMap<>());

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
