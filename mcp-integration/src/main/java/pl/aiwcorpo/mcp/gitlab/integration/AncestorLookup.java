package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.aiwcorpo.mcp.gitlab.core.domain.AncestorTopics;

/**
 * The {@code ancestorClassifications} of a group or project (CC-129): every group above it, each with an empty list.
 * Only projects are classified in GitLab (decision of 2026-10-28), so the map is built from the canonical path alone,
 * without asking GitLab about the groups, and a group the caller cannot see changes nothing.
 */
final class AncestorLookup {

    private AncestorLookup() {
    }

    /** @param canonicalPath path as GitLab spells it, of the group or project whose ancestors are wanted */
    static AncestorTopics above(String canonicalPath) {
        Map<String, List<String>> topics = new LinkedHashMap<>();
        for (String path : AncestorTopics.groupsAbove(canonicalPath)) {
            topics.put(path, List.of());
        }
        return new AncestorTopics(topics);
    }
}
