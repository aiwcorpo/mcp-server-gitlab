package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** What a caller needs to know about one project; name, description and branch are written by GitLab users. */
public record ProjectDetails(String path, @Nullable UntrustedText name, @Nullable UntrustedText description,
        @Nullable UntrustedText defaultBranch, boolean archived, @Nullable String visibility,
        @Nullable String lastActivityAt, @Nullable String webUrl) {
}
