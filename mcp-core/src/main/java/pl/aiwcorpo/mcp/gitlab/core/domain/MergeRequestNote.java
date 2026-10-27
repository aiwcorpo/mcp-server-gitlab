package pl.aiwcorpo.mcp.gitlab.core.domain;

/** The comment just added to a merge request; its body is what the caller sent, so it is not echoed. */
public record MergeRequestNote(String project, long mergeRequestIid, long noteId, String author, String createdAt) {

    public MergeRequestNote inProject(String canonicalPath) {
        return new MergeRequestNote(canonicalPath, mergeRequestIid, noteId, author, createdAt);
    }
}
