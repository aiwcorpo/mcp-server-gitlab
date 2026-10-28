package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * A project together with the topics of all the groups on its path, parent group included (see
 * {@link AncestorTopics}). The project's parent group is judged with the same map minus itself.
 */
public record GitLabProjectResource(GitLabProject project, AncestorTopics ancestorTopics) {
}
