package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;

/**
 * One page of a group's projects as the adapter read it.
 *
 * @param projects    projects whose namespace is the requested group
 * @param namespaceIds namespace id of each project, same order, so the service can drop foreign ones
 * @param hasMore     whether GitLab announced a next page
 */
public record ProjectPage(List<ProjectSummary> projects, List<Long> namespaceIds, boolean hasMore) {

    public ProjectPage {
        projects = List.copyOf(projects);
        namespaceIds = List.copyOf(namespaceIds);
        if (projects.size() != namespaceIds.size()) {
            throw new IllegalArgumentException("one namespace id per project");
        }
    }
}
