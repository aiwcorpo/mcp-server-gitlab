package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;

/** A merge request reduced to what a reviewer needs. */
public record MergeRequest(String project, long iid, String title, UntrustedText description,
        String state, boolean draft, String sourceBranch, String targetBranch, String author,
        List<String> assignees, List<String> reviewers, List<String> labels, String createdAt, String updatedAt,
        String mergedAt, String detailedMergeStatus, boolean hasConflicts, String webUrl) {

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
