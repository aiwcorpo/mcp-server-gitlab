package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;

/**
 * Projects directly in one group.
 *
 * @param group     canonical full path of the group
 * @param projects  at most one page, ordered by path
 * @param truncated whether GitLab has more projects than {@code projects} holds
 */
public record ProjectList(String group, List<ProjectSummary> projects, boolean truncated) {

    public ProjectList {
        projects = List.copyOf(projects);
    }
}
