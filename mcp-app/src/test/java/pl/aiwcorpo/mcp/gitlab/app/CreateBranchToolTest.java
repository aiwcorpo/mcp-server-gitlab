package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@code create_branch}: the name is checked before GitLab, GitLab's refusals are fixed texts, scope and group are enforced. */
class CreateBranchToolTest extends ServerTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PAT = "Bearer glpat-jkowalski-7f3a";
    private static final String APP = "team-a/app";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    private static Map<String, Object> branch(String name, String ref) {
        return Map.of("project", APP, "branch", name, "ref", ref);
    }

    @Test
    void createsTheBranchFromTheRefInTheProjectAndReturnsIt() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "create_branch", branch("feature/new-one", "v1.4.0"));

            assertThat(result.isError()).as(text(result)).isFalse();
            JsonNode created = JSON.readTree(text(result));
            assertThat(created.path("name").path("text").asString()).isEqualTo("feature/new-one");
            assertThat(created.path("name").path("kind").asString()).isEqualTo("untrusted-data");
            assertThat(created.path("protectedBranch").asBoolean()).isFalse();
            assertThat(created.path("commitId").asString()).isEqualTo(StubBackend.COMMIT);
        }
        StubBackend.Seen post = BACKEND.requests().getLast();
        assertThat(post.method()).isEqualTo("POST");
        assertThat(post.rawPath()).isEqualTo("/api/v4/projects/42/repository/branches");
        assertThat(JSON.readTree(post.body()).path("branch").asString()).isEqualTo("feature/new-one");
        assertThat(JSON.readTree(post.body()).path("ref").asString()).isEqualTo("v1.4.0");
    }

    @Test
    void anExistingBranchAndAMissingRefAreRefusedWithAFixedText() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult existing = call(client, "create_branch", branch("feature/dup", "main"));
            McpSchema.CallToolResult missingRef = call(client, "create_branch", branch("feature/x", "no-such-ref"));

            assertInvalidArgument(existing, "already existing branch");
            assertThat(text(existing)).doesNotContain("Branch already exists");
            assertInvalidArgument(missingRef, "GitLab refused the change");
            assertThat(text(missingRef)).doesNotContain("Invalid reference name");
        }
    }

    @Test
    void anAnswerThatNamesAnotherBranchIsNotConfirmed() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "create_branch", branch("feature/other-name", "main"));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"upstream_unavailable\"", "may have been saved")
                    .doesNotContain("somebody-elses");
        }
    }

    @Test
    void namesGitRefusesAreRefusedBeforeGitLab() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String name : new String[] {"a b", "a..b", "-x", "--force", "/x", ".x", "x/", "x.", "x.lock", "a//b", "feat~1",
                    "feature:x", "a^b", "a?b", "a*b", "a[b", "a\\b", "x@{1}", "HEAD", "x\u200By", "x\ny", "x".repeat(256)}) {
                assertInvalidArgument(call(client, "create_branch", branch(name, "main")), "branch");
            }
            assertInvalidArgument(call(client, "create_branch", branch("ok", "a..b")), "ref must be");
            assertInvalidArgument(call(client, "create_branch", branch("ok", "-x")), "ref must be");
        }
        assertThat(BACKEND.requests()).noneMatch(seen -> seen.method().equals("POST"));
    }

    @Test
    void aProjectOutOfReachIsNotFoundAndNothingIsPosted() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String project : new String[] {"team-a/forbidden", "team-a/none", "team-a/secret-plans"}) {
                assertNotFoundOrDenied(call(client, "create_branch", Map.of("project", project, "branch", "x", "ref", "main")));
            }
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::method).containsOnly("GET");
    }

    @Test
    void aReaderWithoutTheWriteGroupIsDeniedAndAWriterWithoutTheScopeSeesNotFound() {
        try (McpSyncClient reader = clientAs(user -> user.username("jkowalski").groups(List.of(READ_GROUP, SCOPE_GROUP)), PAT,
                BackendCredential.Type.USER_SECRET)) {
            assertThat(text(call(reader, "create_branch", branch("x", "main")))).contains("\"error\":\"access_denied\"");
        }
        assertThat(BACKEND.requests()).isEmpty();
        try (McpSyncClient noScope = clientAs(user -> user.username("jkowalski").groups(List.of(READ_GROUP, WRITE_GROUP)), PAT,
                BackendCredential.Type.USER_SECRET)) {
            assertNotFoundOrDenied(call(noScope, "create_branch", branch("x", "main")));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void anExpiredTokenAsksToReconnect() {
        try (McpSyncClient client = clientWithCredential("Bearer " + StubBackend.EXPIRED_TOKEN)) {
            assertCredentialRequired(call(client, "create_branch", branch("x", "main")));
        }
    }
}
