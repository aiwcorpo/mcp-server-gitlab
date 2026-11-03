package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PipelineToolsTest extends ServerTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PAT = "Bearer glpat-jkowalski-7f3a";
    private static final String APP = "team-a/app";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    private JsonNode ok(McpSyncClient client, String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = call(client, tool, arguments);
        assertThat(result.isError()).as(text(result)).isFalse();
        return JSON.readTree(text(result));
    }

    @Test
    void pipelinesAreListedWithoutThoseOfAnotherProject() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode first = ok(client, "list_pipelines", Map.of("project", APP, "ref", "main", "status", "failed"));

            assertThat(first.path("items")).extracting(item -> item.path("id").asInt()).containsExactly(100, 101);
            assertThat(first.path("items").get(0).path("durationSeconds").asDouble()).isEqualTo(595.4);
            assertThat(BACKEND.requests().getLast().rawQuery()).contains("ref=main", "status=failed", "per_page=30");
            assertThat(ok(client, "list_pipelines", Map.of("project", APP, "ref", "main", "status", "failed", "cursor",
                    first.path("nextCursor").asString())).path("items")).hasSize(1);
            assertInvalidArgument(call(client, "list_pipelines", Map.of("project", APP, "status", "weird")), "status");
        }
    }

    @Test
    void aPipelineOfAnotherProjectIsNotFoundWhateverItsId() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertThat(ok(client, "get_pipeline", Map.of("project", APP, "pipelineId", 100)).path("status").asString())
                    .isEqualTo("failed");
            assertNotFoundOrDenied(call(client, "get_pipeline", Map.of("project", APP, "pipelineId", 101)));
            assertNotFoundOrDenied(call(client, "get_pipeline", Map.of("project", APP, "pipelineId", 999)));
            assertNotFoundOrDenied(call(client, "retry_pipeline", Map.of("project", APP, "pipelineId", 101)));
            assertNotFoundOrDenied(call(client, "cancel_pipeline", Map.of("project", APP, "pipelineId", 101)));
            assertInvalidArgument(call(client, "get_pipeline", Map.of("project", APP, "pipelineId", 0)), "pipelineId");
        }
    }

    @Test
    void jobsOfAnotherProjectAreLeftOutOfTheList() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode jobs = ok(client, "list_pipeline_jobs", Map.of("project", APP, "pipelineId", 100));

            assertThat(jobs.path("items")).extracting(item -> item.path("id").asInt()).containsExactly(501, 502);
            assertNotFoundOrDenied(call(client, "list_pipeline_jobs", Map.of("project", APP, "pipelineId", 555)));
        }
    }

    @Test
    void theLogHasEscapesAndSecretsRemovedAndIsCutAtTheEnd() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode all = ok(client, "get_job_log", Map.of("project", APP, "jobId", 501, "lines", 2000));
            String text = all.path("log").path("text").asString();
            assertThat(text).contains("Preparing the environment", "ERROR: Job failed: exit code 1", "[redacted]")
                    .doesNotContain("\u001b", "[36;1m", "section_start", "s3cr3tpassw0rd", "glpat-", "GR1348941",
                            "eyJhbGciOiJIUzI1NiJ9", "MIIEowIBAAKCAQEA");
            assertThat(all.path("redactions").asInt()).isGreaterThanOrEqualTo(6);
            assertThat(all.path("log").path("truncated").asBoolean()).isFalse();
            assertThat(all.path("log").path("kind").asString()).isEqualTo("untrusted-data");

            JsonNode tail = ok(client, "get_job_log", Map.of("project", APP, "jobId", 501, "lines", 3));
            assertThat(tail.path("log").path("text").asString()).isEqualTo(
                    "test line 399\ntest line 400\nERROR: Job failed: exit code 1");
        }
    }

    @Test
    void theDefaultTailIsTwoHundredLines() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode log = ok(client, "get_job_log", Map.of("project", APP, "jobId", 501));
            assertThat(log.path("log").path("text").asString().split("\n")).hasSize(200);
            assertThat(log.path("log").path("truncated").asBoolean()).isTrue();
            assertInvalidArgument(call(client, "get_job_log", Map.of("project", APP, "jobId", 501, "lines", 5000)), "lines");
        }
    }

    @Test
    void aLogOverTheSizeTheServerReadsIsRefusedAndAJobOfAnotherProjectIsNotRead() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult tooLarge = call(client, "get_job_log", Map.of("project", APP, "jobId", 502));
            assertInvalidArgument(tooLarge, "larger than the server reads");
            assertThat(text(tooLarge)).doesNotContain("x\nx\n");

            BACKEND.reset();
            assertNotFoundOrDenied(call(client, "get_job_log", Map.of("project", APP, "jobId", 503)));
            assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath).doesNotContain("/api/v4/projects/42/jobs/503/trace");
            assertNotFoundOrDenied(call(client, "get_job_log", Map.of("project", APP, "jobId", 999)));
        }
    }

    @Test
    void retryAndCancelActOnAPipelineOfTheProject() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertThat(ok(client, "retry_pipeline", Map.of("project", APP, "pipelineId", 100)).path("status").asString())
                    .isEqualTo("running");
            assertThat(BACKEND.requests().getLast().rawPath()).isEqualTo("/api/v4/projects/42/pipelines/100/retry");
            assertThat(ok(client, "cancel_pipeline", Map.of("project", APP, "pipelineId", 100)).path("status").asString())
                    .isEqualTo("canceled");
            assertThat(BACKEND.requests().getLast().method()).isEqualTo("POST");
        }
    }
}
