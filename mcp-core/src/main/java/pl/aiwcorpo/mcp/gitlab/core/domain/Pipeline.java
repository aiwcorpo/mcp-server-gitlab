package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** A CI pipeline; {@code ref} is a branch or tag name, so it is data. */
public record Pipeline(long id, @Nullable String status, @Nullable String source, @Nullable UntrustedText ref,
        @Nullable String sha, @Nullable UntrustedText user, @Nullable String createdAt, @Nullable String startedAt,
        @Nullable String finishedAt, @Nullable Double durationSeconds, @Nullable String webUrl) {
}
