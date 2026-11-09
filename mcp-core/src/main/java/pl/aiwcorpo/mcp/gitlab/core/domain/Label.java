package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** A label available in a project (its own and those of its groups); the counts are of open issues and merge requests. */
public record Label(UntrustedText name, @Nullable String color, @Nullable UntrustedText description,
        @Nullable Long openIssues, @Nullable Long openMergeRequests) {
}
