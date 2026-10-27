package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * A project as GitLab resolved it: numeric id for every later call, canonical {@code path_with_namespace}
 * (the argument may differ in case or name a path the project was moved away from) and the default branch,
 * {@code null} for an empty repository.
 */
public record GitLabProject(long id, String pathWithNamespace, String defaultBranch) {
}
