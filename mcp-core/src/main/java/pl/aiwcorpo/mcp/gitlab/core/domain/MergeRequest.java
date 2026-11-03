package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * A merge request reduced to what a reviewer needs. Everything a GitLab user types (title, description,
 * branches, labels, user names) is {@link UntrustedText}; states, flags, timestamps and the URL are the system's.
 * {@code headSha} is the commit the source branch points to: {@code approve_merge_request} and
 * {@code merge_merge_request} take it back as the commit the caller reviewed.
 */
public record MergeRequest(String project, long iid, @Nullable UntrustedText title,
        @Nullable UntrustedText description, @Nullable String state, boolean draft,
        @Nullable UntrustedText sourceBranch, @Nullable UntrustedText targetBranch, @Nullable UntrustedText author,
        List<UntrustedText> assignees, List<UntrustedText> reviewers, List<UntrustedText> labels,
        @Nullable String createdAt, @Nullable String updatedAt, @Nullable String mergedAt,
        @Nullable String detailedMergeStatus, boolean hasConflicts, @Nullable UntrustedText mergedBy,
        @Nullable String headSha, @Nullable String webUrl) {

    public MergeRequest {
        assignees = List.copyOf(assignees);
        reviewers = List.copyOf(reviewers);
        labels = List.copyOf(labels);
    }

    public MergeRequest inProject(String canonicalPath) {
        return new MergeRequest(canonicalPath, iid, title, description, state, draft, sourceBranch,
                targetBranch, author, assignees, reviewers, labels, createdAt, updatedAt, mergedAt,
                detailedMergeStatus, hasConflicts, mergedBy, headSha, webUrl);
    }
}
