package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code comment_merge_request} (UPDATE): a note on a merge request of a resolved project, no quick actions. */
class CommentMergeRequestToolTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void addsTheNoteAsTheUser() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 1, "body", "Retry count should come from config.\n\n- [ ] add test"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"project\":\"team-a/app\"", "\"mergeRequestIid\":1", "\"noteId\":302",
                    "\"author\":\"jkowalski\"").doesNotContain("(echo)");
        }
        StubBackend.Seen post = BACKEND.requests().getLast();
        assertThat(post.method()).isEqualTo("POST");
        assertThat(post.rawPath()).isEqualTo("/api/v4/projects/42/merge_requests/1/notes");
        assertThat(post.body()).isEqualTo("{\"body\":\"Retry count should come from config.\\n\\n- [ ] add test\"}");
        assertThat(post.credential()).isEqualTo("PRIVATE-TOKEN glpat-jkowalski-7f3a");
    }

    @Test
    void lineBreaksAreSentAsTheyCame() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 1, "body", "first\r\nsecond\r\n"));

            assertThat(result.isError()).isFalse();
        }
        assertThat(BACKEND.requests().getLast().body()).isEqualTo("{\"body\":\"first\\r\\nsecond\\r\\n\"}");
    }

    @Test
    void withoutCredentialAsksToConnectTheAccount() {
        try (McpSyncClient client = clientWithCredential(null)) {
            assertCredentialRequired(call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 1, "body", "ok")));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void anExpiredTokenAsksToReconnect() {
        try (McpSyncClient client = clientWithCredential("Bearer " + StubBackend.EXPIRED_TOKEN)) {
            assertCredentialRequired(call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 1, "body", "ok")));
        }
    }

    @Test
    void missingAndForbiddenLookTheSame() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertNotFoundOrDenied(call(client, "comment_merge_request", Map.of("project", "team-a/app", "iid", 2, "body", "ok")));
            assertNotFoundOrDenied(call(client, "comment_merge_request", Map.of("project", "team-a/app", "iid", 3, "body", "ok")));
            assertNotFoundOrDenied(call(client, "comment_merge_request",
                    Map.of("project", "team-a/forbidden", "iid", 1, "body", "ok")));
        }
        assertThat(BACKEND.requests()).noneMatch(seen -> seen.method().equals("POST") && seen.rawPath().contains("forbidden"));
    }

    @Test
    void quickActionsAndInvalidArgumentsNeverReachGitLab() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String body : new String[] {"/merge", "LGTM\n/approve", "fine\r\n  /assign_reviewer @boss"}) {
                assertInvalidArgument(call(client, "comment_merge_request",
                        Map.of("project", "team-a/app", "iid", 1, "body", body)), "quick action");
            }
            assertInvalidArgument(call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 1, "body", "x".repeat(10_001))), "body is longer than 10000 characters");
            assertInvalidArgument(call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 1, "body", " ")), "body must be 1-10000 characters and not blank");
            assertInvalidArgument(call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 0, "body", "ok")), "iid is below the minimum 1");
            assertInvalidArgument(call(client, "comment_merge_request",
                    Map.of("project", "/team-a/app", "iid", 1, "body", "ok")), "project does not match the required format");
        }
        assertThat(BACKEND.requests()).as("no note was posted; only the resolver's lookups")
                .noneMatch(seen -> seen.method().equals("POST"));
    }
}
