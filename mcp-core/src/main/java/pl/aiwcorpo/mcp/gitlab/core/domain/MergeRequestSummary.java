package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** One row of a merge request listing; {@code get_merge_request} has the rest. */
public record MergeRequestSummary(long iid, @Nullable UntrustedText title, @Nullable String state, boolean draft,
        @Nullable UntrustedText author, @Nullable UntrustedText sourceBranch, @Nullable UntrustedText targetBranch,
        @Nullable String updatedAt, @Nullable String webUrl) {
}
