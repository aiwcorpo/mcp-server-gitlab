package pl.aiwcorpo.mcp.gitlab.tools;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.paging.CursorCodec;
import pl.aiwcorpo.mcp.platform.paging.PagedResult;

/**
 * The cursor of a listing is the platform's signed token around GitLab's page number. It is tied to the caller, the tool
 * and the query (the fingerprint), so a cursor cannot be moved to another listing, and it grants nothing: every page is
 * read with the caller's own credential again.
 */
@Component
public class PageCursors {

    private static final Pattern POSITION = Pattern.compile("page:([1-9][0-9]{0,5})");

    private final CursorCodec codec;

    public PageCursors(CursorCodec codec) {
        this.codec = codec;
    }

    /** The page a cursor stands for; the first one when there is none. */
    int pageOf(@Nullable String cursor, String tool, String fingerprint) {
        if (cursor == null) {
            return 1;
        }
        Matcher position = POSITION.matcher(codec.open(cursor, tool, fingerprint));
        if (!position.matches()) {
            throw new InvalidToolArgumentException(CursorCodec.REFUSED_MESSAGE);
        }
        return Integer.parseInt(position.group(1));
    }

    <T> PagedResult<T> result(BackendPage<T> page, String tool, String fingerprint) {
        return result(page, tool, fingerprint, item -> false);
    }

    /** @param cut tells whether the server cut the content of an element, which the result then reports as {@code truncated} */
    <T> PagedResult<T> result(BackendPage<T> page, String tool, String fingerprint, Predicate<T> cut) {
        String next = page.nextPage() == null ? null : codec.issue(tool, fingerprint, "page:" + page.nextPage());
        return new PagedResult<>(page.items(), next, page.items().stream().anyMatch(cut), java.util.List.of());
    }
}
