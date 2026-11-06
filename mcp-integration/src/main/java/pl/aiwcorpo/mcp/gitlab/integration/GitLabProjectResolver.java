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
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabPaths;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProjectResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.resource.ResourceResolver;

/**
 * Tells the starter what a project path is: for an argument and for every project of a listing. The project and its
 * parent group are described as GitLab reports them (canonical path, topics), so a project moved to another group is
 * judged by the group it is in now. Only projects carry topics; every group on the path is listed with an empty one (CC-129).
 */
@Component
public class GitLabProjectResolver implements ResourceResolver<GitLabProjectResource> {

    static final String TYPE = GitLabResourceDescription.PROJECT_TYPE;

    private final GitLabPort gitlab;
    GitLabProjectResolver(GitLabPort gitlab) {
        this.gitlab = gitlab;
    }

    @Override
    public String resourceType() {
        return TYPE;
    }

    /**
     * The pattern of the declaration has already accepted the path; the depth and the reserved endings it cannot express are
     * checked here, so a path GitLab cannot have is refused as an invalid argument without a request to GitLab.
     */
    @Override
    public Optional<GitLabProjectResource> fetch(String path) {
        if (!GitLabPaths.isProject(path)) {
            throw new InvalidToolArgumentException(GitLabPaths.PROJECT_MESSAGE);
        }
        return gitlab.findProject(path).map(GitLabProjectResolver::withAncestors);
    }

    /**
     * The elements of a listing come from one group, so the page the listing itself read (kept for the call) is used
     * instead of asking GitLab once per project. A project the page does not hold, such
     * as one under another path, is looked up on its own.
     */
    @Override
    public Map<String, GitLabProjectResource> fetchAll(Collection<String> allPaths) {
        List<String> paths = allPaths.stream().filter(GitLabPaths::isProject).toList();
        Map<String, GitLabProject> listed = new LinkedHashMap<>();
        for (String parent : paths.stream().map(AncestorTopics::parentPath).distinct().toList()) {
            Optional<GitLabGroup> group = gitlab.findGroup(parent);
            if (group.isPresent()) {
                ProjectPage page = gitlab.listedGroupProjects(group.get().id(), GitLabPort.PAGE_SIZE);
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
        return GitLabResourceDescription.of(resource);
    }

    @Override
    public Optional<ResolvedResource> probe(String path) {
        return GitLabResourceDescription.probeOfProject(path);
    }
}
