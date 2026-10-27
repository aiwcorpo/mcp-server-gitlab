package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code get_merge_request}: one merge request of a resolved project, only the reviewer's fields. */
class GetMergeRequestToolTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void returnsTheMergeRequestWithTheDescriptionAsData() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 1));

            assertThat(result.isError()).isFalse();
            assertThat(text(result))
                    .contains("\"project\":\"team-a/app\"", "\"iid\":1", "\"title\":\"Add retry to the client\"",
                            "\"state\":\"opened\"", "\"sourceBranch\":\"feature/retry\"", "\"author\":\"anowak\"",
                            "\"assignees\":[\"jkowalski\"]", "\"reviewers\":[\"pzielinski\"]",
                            "\"labels\":[\"backend\",\"needs-review\"]", "\"detailedMergeStatus\":\"mergeable\"",
                            "\"description\":{\"kind\":\"untrusted-data\",\"origin\":\"gitlab-merge-request-description\"")
                    .as("fields outside the domain stay in the adapter")
                    .doesNotContain("projectId", "avatar_url", "diff_refs", "Anna Nowak");
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath)
                .containsExactly("/api/v4/projects/team-a%2Fapp", "/api/v4/projects/42/merge_requests/1");
    }

    @Test
    void withoutCredentialAsksToConnectTheAccount() {
        try (McpSyncClient client = clientWithCredential(null)) {
            assertCredentialRequired(call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 1)));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void anExpiredTokenAsksToReconnect() {
        try (McpSyncClient client = clientWithCredential("Bearer " + StubBackend.EXPIRED_TOKEN)) {
            assertCredentialRequired(call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 1)));
        }
    }

    @Test
    void missingAndForbiddenLookTheSame() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertNotFoundOrDenied(call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 2)));
            assertNotFoundOrDenied(call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 3)));
            assertNotFoundOrDenied(call(client, "get_merge_request", Map.of("project", "team-a/forbidden", "iid", 1)));
        }
    }

    @Test
    void invalidArgumentsNeverReachGitLab() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (Object iid : new Object[] {0, -1, 2_147_483_648L}) {
                assertInvalidArgument(call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", iid)),
                        "iid must be the merge request number");
            }
            McpSchema.CallToolResult notANumber = call(client, "get_merge_request",
                    Map.of("project", "team-a/app", "iid", "1/notes"));
            assertThat(notANumber.isError()).isTrue();
            assertThat(text(notANumber)).contains("\"error\":\"invalid_argument\"");
            assertInvalidArgument(call(client, "get_merge_request", Map.of("project", "team-a", "iid", 1)),
                    "project must be a GitLab project path");
        }
        assertThat(BACKEND.requests()).isEmpty();
    }
}
