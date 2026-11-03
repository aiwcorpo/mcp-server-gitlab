package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * A commit. A listing leaves {@code message} and the statistics out; {@code get_commit} fills them. The author is the
 * name written in the commit, which anybody can set to anything, so it is data like the message.
 */
public record Commit(String id, @Nullable UntrustedText title, @Nullable UntrustedText message,
        @Nullable UntrustedText authorName, @Nullable String authoredAt, @Nullable Long additions,
        @Nullable Long deletions, @Nullable String webUrl) {
}
