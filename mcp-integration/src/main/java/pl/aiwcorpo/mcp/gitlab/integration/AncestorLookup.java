package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.gitlab.core.domain.AncestorTopics;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;

/**
 * Reads the topics of the groups above a group or project, which the policy needs because GitLab does not inherit
 * topics (CC-129). One lookup per group on the path, as the caller; {@link GitLabRestAdapter} keeps the answers for
 * the duration of the call, so the projects of one group share them.
 *
 * <p>Fails closed: when any group above is not visible to the caller (404/403) its topics are unknown, and a hidden
 * group may be the restricted one, so there is no answer and the resource is treated as not found.
 */
@Component
class AncestorLookup {

    private final GitLabPort gitlab;

    AncestorLookup(GitLabPort gitlab) {
        this.gitlab = gitlab;
    }

    /** @param canonicalPath path as GitLab spells it, of the group or project whose ancestors are wanted */
    Optional<AncestorTopics> above(String canonicalPath) {
        Map<String, List<String>> topics = new LinkedHashMap<>();
        for (String path : AncestorTopics.groupsAbove(canonicalPath)) {
            Optional<GitLabGroup> group = gitlab.findGroup(path);
            if (group.isEmpty()) {
                return Optional.empty();
            }
            topics.put(path, group.get().topics());
        }
        return Optional.of(new AncestorTopics(topics));
    }
}
