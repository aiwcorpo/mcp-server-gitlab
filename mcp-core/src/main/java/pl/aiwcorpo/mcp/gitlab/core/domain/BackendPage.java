package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of a GitLab list as the adapter read it. {@code nextPage} is the number from the {@code X-Next-Page}
 * header, absent on the last page; the tools turn it into a signed cursor, GitLab's own offsets never reach the model.
 */
public record BackendPage<T>(List<T> items, @Nullable Integer nextPage) {

    public BackendPage {
        items = List.copyOf(items);
    }

    public <R> BackendPage<R> map(java.util.function.Function<T, R> mapping) {
        return new BackendPage<>(items.stream().map(mapping).toList(), nextPage);
    }
}
