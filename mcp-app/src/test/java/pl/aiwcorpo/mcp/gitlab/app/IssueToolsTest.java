package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class IssueToolsTest extends ServerTestSupport {

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
    void theListingDropsAnIssueThatNamesAnotherProjectAndPages() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode first = ok(client, "list_issues", Map.of("project", APP, "state", "opened", "search", "crash"));

            assertThat(first.path("items")).extracting(item -> item.path("iid").asInt()).containsExactly(1, 2);
            assertThat(BACKEND.requests().getLast().rawQuery()).contains("state=opened", "search=crash", "per_page=20");
            JsonNode second = ok(client, "list_issues", Map.of("project", APP, "state", "opened", "search", "crash",
                    "cursor", first.path("nextCursor").asString()));
            assertThat(second.path("items")).hasSize(1);
            assertInvalidArgument(call(client, "list_issues", Map.of("project", APP, "state", "merged")), "state");
        }
    }

    @Test
    void anIssueIsReadAsDataAndAnIssueOfAnotherProjectIsNotFound() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode issue = ok(client, "get_issue", Map.of("project", APP, "iid", 1));
            assertThat(issue.path("project").asString()).isEqualTo(APP);
            assertThat(issue.path("description").path("kind").asString()).isEqualTo("untrusted-data");
            assertThat(issue.path("labels")).hasSize(2);
            assertThat(issue.path("issueType").asString()).isEqualTo("issue");

            assertNotFoundOrDenied(call(client, "get_issue", Map.of("project", APP, "iid", 2)));
            assertNotFoundOrDenied(call(client, "get_issue", Map.of("project", APP, "iid", 404)));
        }
    }

    @Test
    void notesAreListedUnderTheProject() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode notes = ok(client, "list_issue_notes", Map.of("project", APP, "iid", 1));
            assertThat(notes.path("items")).hasSize(1);
            assertThat(notes.path("items").get(0).path("author").path("text").asString()).isEqualTo("jkowalski");
            assertNotFoundOrDenied(call(client, "list_issue_notes", Map.of("project", APP, "iid", 99)));
        }
        assertThat(BACKEND.requests().getLast().rawPath()).startsWith("/api/v4/projects/42/issues/");
    }

    @Test
    void createUpdateAndCommentChangeOnlyWhatIsGiven() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode created = ok(client, "create_issue", Map.of("project", APP, "title", "New issue", "labels", List.of("bug")));
            assertThat(created.path("iid").asInt()).isEqualTo(30);
            assertThat(BACKEND.requests().getLast().body()).contains("\"title\":\"New issue\"", "\"labels\":\"bug\"")
                    .doesNotContain("description");

            JsonNode closed = ok(client, "update_issue", Map.of("project", APP, "iid", 1, "state", "closed"));
            assertThat(closed.path("state").asString()).isEqualTo("closed");
            assertThat(BACKEND.requests().getLast().body()).isEqualTo("{\"state_event\":\"close\"}");
            ok(client, "update_issue", Map.of("project", APP, "iid", 1, "state", "opened"));
            assertThat(BACKEND.requests().getLast().body()).isEqualTo("{\"state_event\":\"reopen\"}");
            assertInvalidArgument(call(client, "update_issue", Map.of("project", APP, "iid", 1)), "at least one");

            JsonNode note = ok(client, "comment_issue", Map.of("project", APP, "iid", 1, "body", "Fixed in !20"));
            assertThat(note.path("noteId").asInt()).isEqualTo(402);
            assertThat(text(call(client, "comment_issue", Map.of("project", APP, "iid", 1, "body", "ok")))).doesNotContain("(echo)");

            assertInvalidArgument(call(client, "comment_issue", Map.of("project", APP, "iid", 1, "body", "/close")), "quick action");
            assertInvalidArgument(call(client, "comment_issue", Map.of("project", APP, "iid", 1, "body", "cc @all")), "@all");
            assertInvalidArgument(call(client, "create_issue", Map.of("project", APP, "title", "t", "description", "x\n/assign @me")),
                    "quick action");
            assertNotFoundOrDenied(call(client, "comment_issue", Map.of("project", APP, "iid", 99, "body", "x")));

            McpSchema.CallToolResult unconfirmed = call(client, "create_issue", Map.of("project", APP, "title", "foreign-answer"));
            assertThat(unconfirmed.isError()).isTrue();
            assertThat(text(unconfirmed)).contains("\"error\":\"upstream_unavailable\"", "may have been saved")
                    .doesNotContain("not_found_or_denied", "New issue");
        }
    }
}
