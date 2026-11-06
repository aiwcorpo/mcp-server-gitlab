package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroupResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabPaths;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.resource.ResourceResolver;

/**
 * Tells the starter what a group path is before the tool runs: GitLab is asked with the caller's own credential, the
 * answer is described for the policy with the canonical path. Groups are not classified, so the classification is empty and
 * every group above is listed with an empty one.
 */
@Component
public class GitLabGroupResolver implements ResourceResolver<GitLabGroupResource> {

    static final String TYPE = GitLabResourceDescription.GROUP_TYPE;

    private final GitLabPort gitlab;

    GitLabGroupResolver(GitLabPort gitlab) {
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
    public Optional<GitLabGroupResource> fetch(String path) {
        if (!GitLabPaths.isGroup(path)) {
            throw new InvalidToolArgumentException(GitLabPaths.GROUP_MESSAGE);
        }
        return gitlab.findGroup(path).map(group -> new GitLabGroupResource(group, AncestorLookup.above(group.fullPath())));
    }

    @Override
    public ResolvedResource describe(GitLabGroupResource resource) {
        return GitLabResourceDescription.of(resource);
    }

    @Override
    public Optional<ResolvedResource> probe(String path) {
        return GitLabResourceDescription.probeOfGroup(path);
    }
}
