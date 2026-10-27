package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;

/**
 * A merge request reduced to what a reviewer needs. Everything a GitLab user types (title, description,
 * branches, labels, user names) is {@link UntrustedText}; states, flags, timestamps and the URL are the system's.
 */
public record MergeRequest(String project, long iid, UntrustedText title, UntrustedText description,
        String state, boolean draft, UntrustedText sourceBranch, UntrustedText targetBranch, UntrustedText author,
        List<UntrustedText> assignees, List<UntrustedText> reviewers, List<UntrustedText> labels, String createdAt,
        String updatedAt, String mergedAt, String detailedMergeStatus, boolean hasConflicts, String webUrl) {

    public MergeRequest {
        assignees = List.copyOf(assignees);
        reviewers = List.copyOf(reviewers);
        labels = List.copyOf(labels);
    }

    public MergeRequest inProject(String canonicalPath) {
        return new MergeRequest(canonicalPath, iid, title, description, state, draft, sourceBranch,
                targetBranch, author, assignees, reviewers, labels, createdAt, updatedAt, mergedAt,
                detailedMergeStatus, hasConflicts, webUrl);
    }
}
