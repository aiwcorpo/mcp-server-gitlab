package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of a group's projects as the adapter read it.
 *
 * @param projects      what a caller sees of each project
 * @param resources     the same projects as GitLab identifies them (id, canonical path, topics), same order
 * @param namespaceIds  namespace id of each project, same order, so the service can drop foreign ones
 * @param nextPage      number of the next page, absent on the last one
 */
public record ProjectPage(List<ProjectSummary> projects, List<GitLabProject> resources, List<Long> namespaceIds,
        @Nullable Integer nextPage) {

    public ProjectPage {
        projects = List.copyOf(projects);
        resources = List.copyOf(resources);
        namespaceIds = List.copyOf(namespaceIds);
        if (projects.size() != namespaceIds.size() || projects.size() != resources.size()) {
            throw new IllegalArgumentException("one resource and one namespace id per project");
        }
    }
}
