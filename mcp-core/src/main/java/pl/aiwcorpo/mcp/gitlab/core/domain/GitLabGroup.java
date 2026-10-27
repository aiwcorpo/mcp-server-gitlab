package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * A group as GitLab resolved it. {@code fullPath} is GitLab's canonical spelling, which may differ in case from
 * the argument; every later call uses {@code id}.
 */
public record GitLabGroup(long id, String fullPath) {
}
