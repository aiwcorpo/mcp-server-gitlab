package pl.aiwcorpo.mcp.gitlab.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.regex.Pattern;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks of tool arguments, before anything reaches GitLab. Group and project paths use the patterns of the
 * contract (mcp-contracts {@code common.schema.json}, {@code GitLabGroupPath} and {@code GitLabProjectPath},
 * CC-056), the same ones the tools publish in their input schemas; a test runs the contract's vectors.
 */
final class GitLabArguments {

    static final int PATH_MAX_LENGTH = 1024;
    static final int REF_MAX_LENGTH = 255;
    static final int BODY_MAX_LENGTH = 10_000;
    static final long IID_MAX = Integer.MAX_VALUE;

    static final Pattern GROUP_PATH = contractPattern("GitLabGroupPath");
    static final Pattern PROJECT_PATH = contractPattern("GitLabProjectPath");

    /** Branch and tag names, a conservative subset of git-check-ref-format; commit SHAs fit too. */
    private static final Pattern REF = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9._/-]*");

    /**
     * A GitLab quick action is a line starting with {@code /command}; in a note it would run as the user
     * (merge, approve, close, reassign), turning a comment into a different, unreviewed action.
     */
    private static final Pattern QUICK_ACTION = Pattern.compile("(?m)^[ \\t]*/[A-Za-z]");

    /**
     * {@code @all} notifies every member of the project from one comment. Only a word character before it
     * defeats the mention in GitLab (an e-mail address), so anything else, another {@code @} included, is refused;
     * case is ignored because it is not known to matter to GitLab.
     */
    private static final Pattern MENTION_ALL = Pattern.compile("(?<!\\w)@all(?![\\w-])", Pattern.CASE_INSENSITIVE);

    private GitLabArguments() {
    }

    /**
     * The second line of defence behind the schema the starter enforces: the same pattern, read from the
     * contract's {@code common.schema.json} instead of copied.
     */
    private static Pattern contractPattern(String definition) {
        String resource = "/mcp-contracts/schemas/common.schema.json";
        try (InputStream in = GitLabArguments.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing: mcp-contracts must be on the classpath");
            }
            String pattern = JsonMapper.builder().build().readTree(in).path("$defs").path(definition)
                    .path("pattern").asString(null);
            if (pattern == null) {
                throw new IllegalStateException(definition + " has no pattern in common.schema.json");
            }
            return Pattern.compile(pattern);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static String group(String group) {
        if (group == null || group.length() > PATH_MAX_LENGTH || !GROUP_PATH.matcher(group).matches()) {
            throw new InvalidToolArgumentException("group must be a GitLab group path such as 'team-a' or "
                    + "'team-a/sub': 1-20 segments of letters, digits, '.', '_' or '-' separated by '/', "
                    + "at most 1024 characters");
        }
        return group;
    }

    static String project(String project) {
        if (project == null || project.length() > PATH_MAX_LENGTH || !PROJECT_PATH.matcher(project).matches()) {
            throw new InvalidToolArgumentException("project must be a GitLab project path such as 'team-a/app': "
                    + "group path and project name separated by '/', segments of letters, digits, '.', '_' or '-', "
                    + "at most 1024 characters");
        }
        return project;
    }

    static String filePath(String path) {
        if (path == null || path.isEmpty() || path.length() > PATH_MAX_LENGTH) {
            throw new InvalidToolArgumentException("path must be 1-1024 characters");
        }
        if (path.startsWith("/") || path.endsWith("/") || path.indexOf('\\') >= 0 || path.indexOf('%') >= 0
                || hasControlCharacter(path)) {
            throw new InvalidToolArgumentException("path must be relative to the repository root, without a leading "
                    + "or trailing '/', '\\', '%' or control characters");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new InvalidToolArgumentException("path must not contain empty, '.' or '..' segments");
            }
        }
        return path;
    }

    /** @return {@code null} when no ref was given (the project's default branch is used) */
    static String ref(String ref) {
        if (ref == null) {
            return null;
        }
        if (ref.isEmpty() || ref.length() > REF_MAX_LENGTH || !REF.matcher(ref).matches() || ref.contains("..")
                || ref.contains("//") || ref.endsWith("/") || ref.endsWith(".") || ref.endsWith(".lock")) {
            throw new InvalidToolArgumentException("ref must be a branch, tag or commit SHA of at most 255 characters: "
                    + "letters, digits, '.', '_', '-' and '/', not starting with '.', '-' or '/', without '..' or '//'");
        }
        return ref;
    }

    static long iid(Long iid) {
        if (iid == null || iid < 1 || iid > IID_MAX) {
            throw new InvalidToolArgumentException("iid must be the merge request number within the project, "
                    + "1-2147483647");
        }
        return iid;
    }

    static String commentBody(String body) {
        if (body == null || body.isBlank() || body.length() > BODY_MAX_LENGTH) {
            throw new InvalidToolArgumentException("body must be 1-10000 characters and not blank");
        }
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (isControl(c) && c != '\n' && c != '\r' && c != '\t') {
                throw new InvalidToolArgumentException("body must not contain control characters other than "
                        + "line breaks and tabs");
            }
        }
        if (QUICK_ACTION.matcher(body).find()) {
            throw new InvalidToolArgumentException("body must not contain a line starting with '/': GitLab would "
                    + "run it as a quick action");
        }
        if (MENTION_ALL.matcher(body).find()) {
            throw new InvalidToolArgumentException("body must not mention @all: it notifies every member of the project");
        }
        return body;
    }

    private static boolean hasControlCharacter(String text) {
        return text.chars().anyMatch(c -> isControl((char) c));
    }

    private static boolean isControl(char c) {
        return c < 0x20 || c == 0x7f || (c >= 0x80 && c < 0xa0) || c == 0x2028 || c == 0x2029;
    }
}
