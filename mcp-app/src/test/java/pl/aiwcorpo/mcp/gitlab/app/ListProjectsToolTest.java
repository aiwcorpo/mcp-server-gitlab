package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code list_projects}: one group, resolved by GitLab, listing by group id without shared projects. */
class ListProjectsToolTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void listsTheGroupsOwnProjectsByGroupId() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "list_projects", Map.of("group", "Team-A"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result))
                    .contains("\"path\":\"team-a/app\"", "\"path\":\"team-a/lib\"", "\"nextCursor\":\"",
                            "\"format\":\"text/markdown\"")
                    .as("the name and the default branch are data too")
                    .contains("{\"kind\":\"untrusted-data\",\"format\":\"text/plain\","
                            + "\"text\":\"App\",\"truncated\":false}")
                    .contains("\"text\":\"main\",\"truncated\":false")
                    .as("a project of another namespace").doesNotContain("finance/secret")
                    .as("fields outside the domain stay in the adapter").doesNotContain("runners_token", "GR1348941");
        }
        assertThat(BACKEND.requests()).as("two projects of a page are hidden or foreign, so the page is filled from the next ones")
                .extracting(StubBackend.Seen::rawPath)
                .containsExactly("/api/v4/groups/Team-A", "/api/v4/groups/7/projects", "/api/v4/groups/7/projects",
                        "/api/v4/groups/7/projects");
        assertThat(BACKEND.requests().get(1).rawQuery()).contains("with_shared=false", "include_subgroups=false",
                "per_page=100", "page=1");
        assertThat(BACKEND.requests().get(3).rawQuery()).contains("page=3");
        assertThat(BACKEND.credentials()).containsOnly("PRIVATE-TOKEN glpat-jkowalski-7f3a");
    }

    @Test
    void theCursorOfTheFirstPageOpensTheSecondOnlyForTheSameListing() throws Exception {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            String cursor = tools.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(text(call(client, "list_projects", Map.of("group", "team-a")))).path("nextCursor").asString();

            McpSchema.CallToolResult second = call(client, "list_projects", Map.of("group", "team-a", "cursor", cursor));
            assertThat(second.isError()).isFalse();
            assertThat(BACKEND.requests().getLast().rawQuery()).as("the filler ended at page 3, the cursor continues at 4").contains("page=6");

            assertInvalidArgument(call(client, "list_projects", Map.of("group", "team-a/sub", "cursor", cursor)),
                    "The cursor is not valid");
        }
    }

    @Test
    void withoutCredentialAsksToConnectTheAccount() {
        try (McpSyncClient client = clientWithCredential(null)) {
            assertCredentialRequired(call(client, "list_projects", Map.of("group", "team-a")));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void anExpiredTokenAsksToReconnect() {
        try (McpSyncClient client = clientWithCredential("Bearer " + StubBackend.EXPIRED_TOKEN)) {
            assertCredentialRequired(call(client, "list_projects", Map.of("group", "team-a")));
        }
    }

    @Test
    void missingAndForbiddenGroupsLookTheSame() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertNotFoundOrDenied(call(client, "list_projects", Map.of("group", "no-such-group")));
            assertNotFoundOrDenied(call(client, "list_projects", Map.of("group", "hidden")));
        }
    }

    @Test
    void anInvalidGroupNeverReachesGitLab() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String group : new String[] {"team-a/", "../team-b", "team a", "team-a?x=1", "*", ""}) {
                assertInvalidArgument(call(client, "list_projects", Map.of("group", group)), "Invalid arguments: group");
            }
            assertInvalidArgument(call(client, "list_projects", Map.of()), "Invalid arguments: group");
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void aGroupBeyondTwentyLevelsOrEndingInGitOrAtomIsRefusedByTheServerBeforeGitLab() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String group : new String[] {"g/".repeat(20) + "g", "team-a/sub.git", "team.atom/sub"}) {
                assertInvalidArgument(call(client, "list_projects", Map.of("group", group)), "group must be a GitLab group path");
            }
            assertNotFoundOrDenied(call(client, "list_projects", Map.of("group", "g/".repeat(19) + "g")));
        }
        assertThat(BACKEND.requests()).as("only the deepest allowed path reaches GitLab, and it does not exist there")
                .hasSize(1);
    }
}
