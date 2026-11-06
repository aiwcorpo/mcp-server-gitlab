package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.cedar.entities.Scopes;
import pl.aiwcorpo.mcp.gitlab.core.domain.AncestorTopics;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroupResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabPaths;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProjectResource;

/**
 * The group and the project as the policy sees them (C7, entity-builder.md). Id and parent come from GitLab's answer,
 * never from the tool argument: GitLab paths are case-insensitive and a project may have moved. Both types carry the scope
 * of every group on the path ({@code GitLab::Group/team-a}, {@code GitLab::Group/team-a/sub}), so a grant on a group
 * covers its subgroups and projects and not a sibling that merely starts with the same letters. The builder does not
 * know {@code inheritedClassification}: it is computed here from the topics of the groups above the resource.
 */
final class GitLabResourceDescription {

    static final String GROUP_TYPE = "GitLab::Group";
    static final String PROJECT_TYPE = "GitLab::Project";

    private GitLabResourceDescription() {
    }

    static ResolvedResource of(GitLabGroupResource resource) {
        return group(resource.group().fullPath(), resource.group().topics(), resource.ancestorTopics());
    }

    static ResolvedResource of(GitLabProjectResource resource) {
        String path = resource.project().pathWithNamespace();
        String groupPath = AncestorTopics.parentPath(path);
        AncestorTopics ancestors = resource.ancestorTopics();
        ResolvedResource group = group(groupPath, ancestors.byPath().getOrDefault(groupPath, List.of()),
                ancestors.without(groupPath));
        return project(path, resource.project().topics(), ancestors, group);
    }

    /**
     * The group as its path alone describes it, with no classification and nothing inherited, so that the grant can be
     * judged before GitLab is asked (CC-146). Empty for a path the server would refuse anyway.
     */
    static Optional<ResolvedResource> probeOfGroup(String path) {
        if (!GitLabPaths.isGroup(path)) {
            return Optional.empty();
        }
        return Optional.of(group(path, null, new AncestorTopics(Map.of())));
    }

    /** The project as its path alone describes it: the group is the path up to the last {@code /}. */
    static Optional<ResolvedResource> probeOfProject(String path) {
        if (!GitLabPaths.isProject(path)) {
            return Optional.empty();
        }
        ResolvedResource group = group(AncestorTopics.parentPath(path), null, new AncestorTopics(Map.of()));
        return Optional.of(project(path, null, new AncestorTopics(Map.of()), group));
    }

    private static ResolvedResource group(String path, List<String> topics, AncestorTopics ancestors) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("path", path);
        attributes.put("classification", topics);
        attributes.put("inheritedClassification", ancestors.inheritedClassification());
        return ResolvedResource.of(GROUP_TYPE, path, attributes).withScopes(scopesOfGroup(path));
    }

    private static ResolvedResource project(String path, List<String> topics, AncestorTopics ancestors, ResolvedResource group) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("path", path);
        attributes.put("classification", topics);
        attributes.put("inheritedClassification", ancestors.inheritedClassification());
        return ResolvedResource.of(PROJECT_TYPE, path, attributes).withParent(group).withScopes(group.scopes());
    }

    private static List<String> scopesOfGroup(String groupPath) {
        List<String> scopes = new ArrayList<>();
        Scopes.pathPrefixes(groupPath).forEach(prefix -> scopes.add(Scopes.id(GROUP_TYPE, prefix)));
        return scopes;
    }
}
