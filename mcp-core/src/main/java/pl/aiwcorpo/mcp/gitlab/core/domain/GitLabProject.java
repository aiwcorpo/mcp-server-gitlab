package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;

/**
 * A project as GitLab resolved it: numeric id for every later call, canonical {@code path_with_namespace}
 * (the argument may differ in case or name a path the project was moved away from), the default branch,
 * {@code null} for an empty repository, and the project's own topics (its classification).
 */
public record GitLabProject(long id, String pathWithNamespace, String defaultBranch, List<String> topics) {

    public GitLabProject {
        topics = List.copyOf(topics);
    }
}
