package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * A merge request with the id of the project GitLab says it belongs to. The service compares that id with
 * the resolved project; the id itself is not handed out.
 */
public record FoundMergeRequest(long projectId, MergeRequest mergeRequest) {
}
