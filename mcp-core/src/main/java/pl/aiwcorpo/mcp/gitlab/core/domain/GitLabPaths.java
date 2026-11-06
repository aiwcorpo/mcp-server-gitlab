package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.regex.Pattern;

/**
 * What a group or project path must look like before GitLab is asked about it. The shape is the {@code idPattern} of
 * {@code GitLab::Group} and {@code GitLab::Project} in {@code mcp/policies/resource-types.json}; the contracts' grammar for
 * patterns has no lookaround and no counted repetition of a group, so two limits of GitLab are checked here instead:
 * subgroups nest up to 20 levels (a group path has at most 20 segments, a project path 21), and no segment ends in
 * {@code .git} or {@code .atom} (reserved by GitLab for clone URLs and feeds).
 */
public final class GitLabPaths {

    public static final int MAX_LENGTH = 1024;
    public static final int MAX_GROUP_SEGMENTS = 20;
    public static final int MAX_PROJECT_SEGMENTS = MAX_GROUP_SEGMENTS + 1;

    public static final String GROUP_MESSAGE = "group must be a GitLab group path such as 'team-a' or "
            + "'team-a/sub': 1-20 segments of letters, digits, '.', '_' or '-' separated by '/', none ending in "
            + ".git or .atom, at most 1024 characters";
    public static final String PROJECT_MESSAGE = "project must be a GitLab project path such as 'team-a/app': "
            + "group path and project name separated by '/', 2-21 segments of letters, digits, '.', '_' or '-', "
            + "none ending in .git or .atom, at most 1024 characters";

    /** Copies of the {@code idPattern} declarations; a test in mcp-app compares them with resource-types.json. */
    public static final String GROUP_ID_PATTERN = "^[A-Za-z0-9](?:[._/-][A-Za-z0-9]|[A-Za-z0-9])*$";
    public static final String PROJECT_ID_PATTERN =
            "^[A-Za-z0-9](?:[._-][A-Za-z0-9]|[A-Za-z0-9])*/[A-Za-z0-9](?:[._/-][A-Za-z0-9]|[A-Za-z0-9])*$";

    private static final Pattern GROUP = Pattern.compile(GROUP_ID_PATTERN);
    private static final Pattern PROJECT = Pattern.compile(PROJECT_ID_PATTERN);

    private GitLabPaths() {
    }

    public static boolean isGroup(String path) {
        return path != null && path.length() <= MAX_LENGTH && GROUP.matcher(path).matches()
                && withinGitLabLimits(path, MAX_GROUP_SEGMENTS);
    }

    public static boolean isProject(String path) {
        return path != null && path.length() <= MAX_LENGTH && PROJECT.matcher(path).matches()
                && withinGitLabLimits(path, MAX_PROJECT_SEGMENTS);
    }

    private static boolean withinGitLabLimits(String path, int maxSegments) {
        String[] segments = path.split("/", -1);
        if (segments.length > maxSegments) {
            return false;
        }
        for (String segment : segments) {
            if (segment.endsWith(".git") || segment.endsWith(".atom")) {
                return false;
            }
        }
        return true;
    }
}
