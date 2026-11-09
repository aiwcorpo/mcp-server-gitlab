package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every tool of the catalog, called with a project (or group) the caller cannot reach: GitLab hides it (403), it does not
 * exist (404), or the policy closes it (a restricted topic). All three look the same, and nothing below the project is
 * ever requested.
 */
class OutOfReachTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";

    /** The arguments a tool needs besides the project, valid ones: a refusal must not come from a bad argument. */
    private static final Map<String, Map<String, Object>> EXTRA = Map.ofEntries(
            Map.entry("get_file", Map.of("path", "README.md")),
            Map.entry("search_code", Map.of("query", "retries")),
            Map.entry("get_commit", Map.of("sha", StubBackend.COMMIT)),
            Map.entry("get_commit_diff", Map.of("sha", StubBackend.COMMIT)),
            Map.entry("get_merge_request", Map.of("iid", 1)),
            Map.entry("list_merge_request_diffs", Map.of("iid", 1)),
            Map.entry("list_merge_request_notes", Map.of("iid", 1)),
            Map.entry("comment_merge_request", Map.of("iid", 1, "body", "ok")),
            Map.entry("create_merge_request", Map.of("sourceBranch", "a", "targetBranch", "main", "title", "t")),
            Map.entry("update_merge_request", Map.of("iid", 1, "title", "t")),
            Map.entry("approve_merge_request", Map.of("iid", 1, "expectedHeadSha", StubBackend.HEAD_SHA)),
            Map.entry("merge_merge_request", Map.of("iid", 1, "expectedHeadSha", StubBackend.HEAD_SHA)),
            Map.entry("get_issue", Map.of("iid", 1)),
            Map.entry("list_issue_notes", Map.of("iid", 1)),
            Map.entry("create_issue", Map.of("title", "t")),
            Map.entry("update_issue", Map.of("iid", 1, "title", "t")),
            Map.entry("comment_issue", Map.of("iid", 1, "body", "ok")),
            Map.entry("get_pipeline", Map.of("pipelineId", 100)),
            Map.entry("list_pipeline_jobs", Map.of("pipelineId", 100)),
            Map.entry("get_job_log", Map.of("jobId", 501)),
            Map.entry("get_job_artifact_file", Map.of("jobId", 501, "artifactPath", "reports/junit.xml")),
            Map.entry("create_branch", Map.of("branch", "feature/x", "ref", "main")),
            Map.entry("retry_pipeline", Map.of("pipelineId", 100)),
            Map.entry("cancel_pipeline", Map.of("pipelineId", 100)));

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    static Stream<String> tools() {
        return ToolCatalog.TOOLS.keySet().stream().filter(tool -> ToolCatalog.TOOLS.get(tool).argument() != null);
    }

    @ParameterizedTest
    @MethodSource("tools")
    void aProjectOrGroupOutOfReachIsNotFoundOrDeniedAndNothingBelowItIsRequested(String tool) {
        ToolCatalog.Entry entry = ToolCatalog.TOOLS.get(tool);
        List<String> targets = entry.argument().equals("group")
                ? List.of("hidden", "no-such-group")
                : List.of("team-a/forbidden", "team-a/none", "team-a/secret-plans");
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String target : targets) {
                Map<String, Object> arguments = new LinkedHashMap<>(EXTRA.getOrDefault(tool, Map.of()));
                arguments.put(entry.argument(), target);

                assertNotFoundOrDenied(call(client, tool, arguments));
            }
        }
        assertThat(BACKEND.requests()).as(tool).extracting(StubBackend.Seen::method).containsOnly("GET");
        assertThat(BACKEND.requests()).as(tool).extracting(StubBackend.Seen::rawPath)
                .noneMatch(path -> path.matches("/api/v4/projects/\\d+/.+") || path.matches("/api/v4/groups/\\d+/projects"));
    }
}
