package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** The issue after a create or update; the text is what GitLab stored, which the caller already knows. */
public record IssueChange(String project, long iid, @Nullable UntrustedText title, @Nullable String state,
        @Nullable String webUrl) {

    public IssueChange inProject(String canonicalPath) {
        return new IssueChange(canonicalPath, iid, title, state, webUrl);
    }
}
