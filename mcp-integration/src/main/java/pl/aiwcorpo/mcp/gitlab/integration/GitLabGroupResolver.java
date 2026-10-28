package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroupResource;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.platform.resource.ResourceResolver;

/**
 * Tells the starter what a group path is before the tool runs: GitLab is asked with the caller's own credential, the
 * answer is described for the policy with the canonical path. Groups are not classified, so the classification is empty and
 * every group above is listed with an empty one.
 */
@Component
public class GitLabGroupResolver implements ResourceResolver<GitLabGroupResource> {

    static final String TYPE = "GitLab::Group";

    private final GitLabPort gitlab;
    GitLabGroupResolver(GitLabPort gitlab) {
        this.gitlab = gitlab;
    }

    @Override
    public String resourceType() {
        return TYPE;
    }

    @Override
    public Optional<GitLabGroupResource> fetch(String path) {
        return gitlab.findGroup(path).map(group -> new GitLabGroupResource(group, AncestorLookup.above(group.fullPath())));
    }

    @Override
    public ResolvedResource describe(GitLabGroupResource resource) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("path", resource.group().fullPath());
        attributes.put("classification", resource.group().topics());
        attributes.put("ancestorClassifications", resource.ancestorTopics().byPath());
        return ResolvedResource.of(TYPE, resource.group().fullPath(), attributes);
    }
}
