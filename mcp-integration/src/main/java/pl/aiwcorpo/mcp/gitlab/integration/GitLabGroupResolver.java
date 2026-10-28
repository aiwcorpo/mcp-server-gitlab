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
 * answer is described for the policy with the canonical path and the topics of every group above it.
 */
@Component
public class GitLabGroupResolver implements ResourceResolver<GitLabGroupResource> {

    static final String TYPE = "GitLab::Group";

    private final GitLabPort gitlab;
    private final AncestorLookup ancestors;

    GitLabGroupResolver(GitLabPort gitlab, AncestorLookup ancestors) {
        this.gitlab = gitlab;
        this.ancestors = ancestors;
    }

    @Override
    public String resourceType() {
        return TYPE;
    }

    @Override
    public Optional<GitLabGroupResource> fetch(String path) {
        Optional<GitLabGroup> group = gitlab.findGroup(path);
        if (group.isEmpty()) {
            return Optional.empty();
        }
        return ancestors.above(group.get().fullPath()).map(above -> new GitLabGroupResource(group.get(), above));
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
