package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** An issue reduced to what a reader needs; user-written fields are {@link UntrustedText}. */
public record Issue(String project, long iid, @Nullable UntrustedText title, @Nullable UntrustedText description,
        @Nullable String state, @Nullable String issueType, @Nullable UntrustedText author,
        List<UntrustedText> assignees, List<UntrustedText> labels, @Nullable String createdAt,
        @Nullable String updatedAt, @Nullable String closedAt, @Nullable String webUrl) {

    public Issue {
        assignees = List.copyOf(assignees);
        labels = List.copyOf(labels);
    }

    public Issue inProject(String canonicalPath) {
        return new Issue(canonicalPath, iid, title, description, state, issueType, author, assignees, labels,
                createdAt, updatedAt, closedAt, webUrl);
    }
}
