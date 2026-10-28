package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.AncestorTopics;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProjectResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.platform.resource.ResourceResolver;

/**
 * Tells the starter what a project path is: for an argument and for every project of a listing. The project and its
 * parent group are described as GitLab reports them (canonical path, topics), so a project moved to another group is
 * judged by the group it is in now. Only projects carry topics; every group on the path is listed with an empty one (CC-129).
 */
@Component
public class GitLabProjectResolver implements ResourceResolver<GitLabProjectResource> {

    static final String TYPE = "GitLab::Project";
    static final String GROUP_TYPE = "GitLab::Group";

    private final GitLabPort gitlab;
    GitLabProjectResolver(GitLabPort gitlab) {
        this.gitlab = gitlab;
    }

    @Override
    public String resourceType() {
        return TYPE;
    }

    @Override
    public Optional<GitLabProjectResource> fetch(String path) {
        return gitlab.findProject(path).map(GitLabProjectResolver::withAncestors);
    }

    /**
     * The elements of a listing come from one group, so GitLab is asked for that group's project page once (the page
     * the listing itself read is kept for the call) instead of once per project. A project the page does not hold, such
     * as one under another path, is looked up on its own.
     */
    @Override
    public Map<String, GitLabProjectResource> fetchAll(Collection<String> paths) {
        Map<String, GitLabProject> listed = new LinkedHashMap<>();
        for (String parent : paths.stream().map(AncestorTopics::parentPath).distinct().toList()) {
            Optional<GitLabGroup> group = gitlab.findGroup(parent);
            if (group.isPresent()) {
                ProjectPage page = gitlab.listGroupProjects(group.get().id(), GitLabPort.PAGE_SIZE);
                for (int i = 0; i < page.resources().size(); i++) {
                    if (page.namespaceIds().get(i) == group.get().id()) {
                        GitLabProject project = page.resources().get(i);
                        listed.put(project.pathWithNamespace().toLowerCase(Locale.ROOT), project);
                    }
                }
            }
        }
        Map<String, GitLabProjectResource> fetched = new LinkedHashMap<>();
        for (String path : paths) {
            GitLabProject project = listed.get(path.toLowerCase(Locale.ROOT));
            Optional<GitLabProjectResource> resource = project != null
                    ? Optional.of(withAncestors(project)) : fetch(path);
            resource.ifPresent(found -> fetched.put(path, found));
        }
        return fetched;
    }

    private static GitLabProjectResource withAncestors(GitLabProject project) {
        return new GitLabProjectResource(project, AncestorLookup.above(project.pathWithNamespace()));
    }

    @Override
    public ResolvedResource describe(GitLabProjectResource resource) {
        String path = resource.project().pathWithNamespace();
        String group = AncestorTopics.parentPath(path);
        Map<String, Object> projectAttributes = new LinkedHashMap<>();
        projectAttributes.put("path", path);
        projectAttributes.put("classification", resource.project().topics());
        projectAttributes.put("ancestorClassifications", resource.ancestorTopics().byPath());
        Map<String, Object> groupAttributes = new LinkedHashMap<>();
        groupAttributes.put("path", group);
        groupAttributes.put("classification", resource.ancestorTopics().byPath().getOrDefault(group, List.of()));
        groupAttributes.put("ancestorClassifications", resource.ancestorTopics().without(group).byPath());
        return ResolvedResource.of(TYPE, path, projectAttributes)
                .withParent(ResolvedResource.of(GROUP_TYPE, group, groupAttributes));
    }
}
