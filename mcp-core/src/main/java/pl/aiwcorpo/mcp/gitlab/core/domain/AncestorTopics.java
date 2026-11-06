package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Topics of the groups above a group or project, by the full path of each (CC-129). The key set is exactly the proper
 * path prefixes of the resource: {@code hr} and {@code hr/payroll} for {@code hr/payroll/app}, nothing for a
 * top-level group. {@link #inheritedClassification()} is what the policy sees of them.
 */
public record AncestorTopics(Map<String, List<String>> byPath) {

    public AncestorTopics {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        byPath.forEach((path, topics) -> copy.put(path, List.copyOf(topics)));
        byPath = java.util.Collections.unmodifiableMap(copy);
    }

    /** Paths of the groups above {@code path}, outermost first: {@code a/b/c} gives {@code a}, {@code a/b}. */
    public static List<String> groupsAbove(String path) {
        List<String> groups = new ArrayList<>();
        int slash = path.indexOf('/');
        while (slash >= 0) {
            groups.add(path.substring(0, slash));
            slash = path.indexOf('/', slash + 1);
        }
        return groups;
    }

    /** Path of the group a group or project lies directly in, {@code null} for a top-level group. */
    public static String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? null : path.substring(0, slash);
    }

    /** The same map without {@code path}: what the parent group of a project is judged with. */
    public AncestorTopics without(String path) {
        Map<String, List<String>> rest = new LinkedHashMap<>(byPath);
        rest.remove(path);
        return new AncestorTopics(rest);
    }

    /**
     * The {@code inheritedClassification} attribute of {@code GitLab::Group} and {@code GitLab::Project}: the union of the
     * topics of every group in this map, normalized the way the entity builder normalizes {@code classification}
     * (trimmed, ASCII letters lower-cased, no duplicates), because the builder does not touch this attribute.
     */
    public List<String> inheritedClassification() {
        TreeSet<String> union = new TreeSet<>();
        byPath.values().forEach(topics -> topics.forEach(topic -> union.add(normalized(topic))));
        return List.copyOf(union);
    }

    private static String normalized(String topic) {
        StringBuilder out = new StringBuilder();
        for (char c : topic.trim().toCharArray()) {
            out.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return out.toString();
    }
}
