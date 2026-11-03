package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** A comment on a merge request or an issue; {@code system} notes are GitLab's own log lines ("changed the title"). */
public record NoteView(long id, @Nullable UntrustedText author, @Nullable UntrustedText body, boolean system,
        @Nullable String createdAt) {
}
