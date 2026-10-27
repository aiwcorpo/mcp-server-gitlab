package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** GitLab answers that cannot be used: the client sees internal_error and nothing of the answer. */
@ExtendWith(OutputCaptureExtension.class)
class BackendResponseTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    private McpSchema.CallToolResult mergeRequestOf(String project) {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            return call(client, "get_merge_request", Map.of("project", project, "iid", 1));
        }
    }

    private static void assertInternalError(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("\"error\":\"internal_error\"");
    }

    @Test
    void anUnreadableAnswerIsAnInternalErrorWithoutItsContent(CapturedOutput output) {
        McpSchema.CallToolResult result = mergeRequestOf("team-a/bad-json");

        assertInternalError(result);
        assertThat(text(result)).doesNotContain("login", "glpat-jkowalski");
        assertThat(output.getAll()).doesNotContain("login ...", "glpat-jkowalski");
    }

    @Test
    void aRedirectIsNotFollowedAndNotReadAsMissing() {
        assertInternalError(mergeRequestOf("team-a/redirect"));

        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath).noneMatch(path -> path.equals("/elsewhere"));
    }

    @Test
    void aSlowBackendTimesOut() {
        McpSchema.CallToolResult result = mergeRequestOf("team-a/slow");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("\"error\":\"upstream_unavailable\"").doesNotContain("team-a/slow");
    }

    @Test
    void anAnswerOverTheSizeLimitIsRefused() {
        McpSchema.CallToolResult result = mergeRequestOf("team-a/huge");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).doesNotContain("xxxxxxxx");
        assertThat(text(result)).containsAnyOf("\"error\":\"internal_error\"", "\"error\":\"upstream_unavailable\"");
    }
}
