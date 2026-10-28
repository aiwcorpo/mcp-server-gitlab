package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;

/**
 * A group as GitLab resolved it. {@code fullPath} is GitLab's canonical spelling, which may differ in case from
 * the argument; every later call uses {@code id}. {@code topics} are the group's own classification, empty when
 * GitLab reports none.
 */
public record GitLabGroup(long id, String fullPath, List<String> topics) {

    public GitLabGroup {
        topics = List.copyOf(topics);
    }
}
