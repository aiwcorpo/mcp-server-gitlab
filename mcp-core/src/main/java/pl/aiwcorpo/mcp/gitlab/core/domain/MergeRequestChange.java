package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** The merge request after a create or update. */
public record MergeRequestChange(String project, long iid, @Nullable UntrustedText title, @Nullable String state,
        boolean draft, @Nullable String webUrl) {

    public MergeRequestChange inProject(String canonicalPath) {
        return new MergeRequestChange(canonicalPath, iid, title, state, draft, webUrl);
    }
}
