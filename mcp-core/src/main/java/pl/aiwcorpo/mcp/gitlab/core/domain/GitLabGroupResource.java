package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * A group together with what the resource policy needs to judge it. Topics are not inherited in GitLab, so the
 * topics of every ancestor group are carried along; the map is empty for a top-level group.
 */
public record GitLabGroupResource(GitLabGroup group, AncestorTopics ancestorTopics) {
}
