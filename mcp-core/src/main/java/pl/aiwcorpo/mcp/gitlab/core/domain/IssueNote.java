package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;

/** The comment just added to an issue; its body is what the caller sent, so it is not echoed. */
public record IssueNote(String project, long issueIid, long noteId, @Nullable String author,
        @Nullable String createdAt) {

    public IssueNote inProject(String canonicalPath) {
        return new IssueNote(canonicalPath, issueIid, noteId, author, createdAt);
    }
}
