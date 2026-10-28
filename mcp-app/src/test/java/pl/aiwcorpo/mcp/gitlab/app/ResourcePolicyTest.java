package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * The policy (C7) on groups and projects, with the sample bundle of the contracts. A resource is judged by what GitLab
 * says about it and about every group above it, and every refusal looks like a resource that does not exist.
 */
class ResourcePolicyTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";
    private static final String MISSING_PROJECT = "nowhere/none";
    private static final String MISSING_GROUP = "no-such-group";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    private static Map<String, Object> mergeRequest(String project) {
        return Map.of("project", project, "iid", 1);
    }

    private static Map<String, Object> file(String project) {
        return Map.of("project", project, "path", "README.md");
    }

    @Test
    void aProjectThreeLevelsBelowARestrictedGroupIsNotFoundOrDeniedLikeAMissingOne() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String tool : List.of("get_file", "get_merge_request")) {
                Map<String, Object> deep = tool.equals("get_file") ? file("hr/payroll/team/app") : mergeRequest("hr/payroll/team/app");
                Map<String, Object> missing = tool.equals("get_file") ? file(MISSING_PROJECT) : mergeRequest(MISSING_PROJECT);

                McpSchema.CallToolResult denied = call(client, tool, deep);

                assertNotFoundOrDenied(denied);
                assertThat(text(denied)).as(tool).isEqualTo(text(call(client, tool, missing)));
            }
        }
        assertThat(BACKEND.requests()).as("no file or merge request was read")
                .noneMatch(seen -> seen.rawPath().contains("/repository/") || seen.rawPath().contains("/merge_requests/"));
    }

    @Test
    void theGroupsBetweenTheRestrictedOneAndTheProjectAreRestrictedToo() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult missing = call(client, "list_projects", Map.of("group", MISSING_GROUP));

            for (String group : List.of("hr", "hr/payroll", "hr/payroll/team")) {
                McpSchema.CallToolResult denied = call(client, "list_projects", Map.of("group", group));
                assertNotFoundOrDenied(denied);
                assertThat(text(denied)).as(group).isEqualTo(text(missing));
            }
        }
        assertThat(BACKEND.requests()).noneMatch(seen -> seen.rawPath().endsWith("/projects"));
    }

    @Test
    void aRestrictedGroupDoesNotCloseAGroupWhoseNameOnlyStartsTheSame() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "list_projects", Map.of("group", "hr-eu"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"group\":\"hr-eu\"", "\"path\":\"hr-eu/app\"");
        }
    }

    @Test
    void aProjectIsRestrictedByItsOwnTopicWhateverTheCase() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult missing = call(client, "get_merge_request", mergeRequest(MISSING_PROJECT));

            assertThat(text(call(client, "get_merge_request", mergeRequest("team-a/secret-plans")))).isEqualTo(text(missing));
            assertThat(text(call(client, "comment_merge_request",
                    Map.of("project", "team-a/secret-plans", "iid", 1, "body", "ok")))).isEqualTo(text(missing));
        }
        assertThat(BACKEND.requests()).noneMatch(seen -> seen.method().equals("POST"));
    }

    @Test
    void aListingLeavesOutTheRestrictedProjectAndTheForeignOne() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "list_projects", Map.of("group", "team-a"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("team-a/app", "team-a/lib")
                    .doesNotContain("secret-plans", "finance/secret");
        }
    }

    @Test
    void aListingAsksForTheTopicsOfTheGroupsOnceNotOncePerProject() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            call(client, "list_projects", Map.of("group", "team-a"));
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath)
                .containsExactly("/api/v4/groups/team-a", "/api/v4/groups/7/projects");
    }

    @Test
    void aTopLevelGroupHasNoAncestorsAndASubgroupHasItsParent() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertThat(call(client, "list_projects", Map.of("group", "team-a")).isError()).isFalse();
            BACKEND.reset();

            McpSchema.CallToolResult sub = call(client, "list_projects", Map.of("group", "team-a/sub"));

            assertThat(sub.isError()).isFalse();
            assertThat(text(sub)).contains("team-a/sub/deep");
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath)
                .filteredOn(path -> path.startsWith("/api/v4/groups/team-a")).hasSize(2)
                .as("the parent is read once for the group and the project below it").containsOnlyOnce("/api/v4/groups/team-a");
    }

    @Test
    void aGroupAboveThatGitLabHidesMakesTheResourceNotFoundBecauseItsTopicsAreUnknown() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult missing = call(client, "list_projects", Map.of("group", MISSING_GROUP));

            assertThat(text(call(client, "list_projects", Map.of("group", "locked/inner")))).isEqualTo(text(missing));
            assertThat(text(call(client, "get_merge_request", mergeRequest("locked/inner/app"))))
                    .isEqualTo(text(call(client, "get_merge_request", mergeRequest(MISSING_PROJECT))));
        }
    }

    @Test
    void aGroupAboveThatFailsIsAFailureNotADenial() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "list_projects", Map.of("group", "broken-parent/inner"));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).doesNotContain("not_found_or_denied");
        }
    }

    @Test
    void aCallerWithoutTheGroupOfTheToolGetsAccessDeniedAndNothingReachesGitLab() {
        try (McpSyncClient client = clientAs(user -> user.username("jkowalski").groups(List.of("mcp-nobody")), PAT,
                BackendCredential.Type.USER_SECRET)) {
            for (String tool : List.of("list_projects", "get_file", "get_merge_request", "comment_merge_request")) {
                Map<String, Object> arguments = switch (tool) {
                    case "list_projects" -> Map.of("group", "team-a");
                    case "get_file" -> file("team-a/app");
                    case "get_merge_request" -> mergeRequest("team-a/app");
                    default -> Map.of("project", "team-a/app", "iid", 1, "body", "ok");
                };
                McpSchema.CallToolResult result = call(client, tool, arguments);

                assertThat(result.isError()).as(tool).isTrue();
                assertThat(text(result)).as(tool).contains("\"error\":\"access_denied\"");
            }
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void aReaderWithoutTheWriteGroupCannotComment() {
        try (McpSyncClient client = clientAs(user -> user.username("jkowalski").groups(List.of(READ_GROUP, SCOPE_GROUP)), PAT,
                BackendCredential.Type.USER_SECRET)) {
            McpSchema.CallToolResult result = call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 1, "body", "ok"));

            assertThat(text(result)).contains("\"error\":\"access_denied\"");
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void aWriterOutsideTheScopeOfTheProjectCannotCommentAndSeesNotFoundOrDenied() {
        try (McpSyncClient client = clientAs(user -> user.username("jkowalski").groups(List.of(READ_GROUP, WRITE_GROUP)), PAT,
                BackendCredential.Type.USER_SECRET)) {
            assertNotFoundOrDenied(call(client, "comment_merge_request", Map.of("project", "team-a/app", "iid", 1, "body", "ok")));
        }
        assertThat(BACKEND.requests()).noneMatch(seen -> seen.method().equals("POST"));
    }

    @Test
    void aLinkNarrowedToASubgroupHidesTheGroupAboveAndItsProjects() {
        try (McpSyncClient client = clientAs(user -> { reader(user); user.virtualServer("sub-only", Map.of("gitlabGroups", List.of("team-a/sub"))); },
                PAT, BackendCredential.Type.USER_SECRET)) {
            McpSchema.CallToolResult missing = call(client, "list_projects", Map.of("group", MISSING_GROUP));

            McpSchema.CallToolResult inside = call(client, "list_projects", Map.of("group", "team-a/sub"));
            assertThat(inside.isError()).isFalse();
            assertThat(text(inside)).contains("team-a/sub/deep");
            assertThat(text(call(client, "list_projects", Map.of("group", "team-a")))).isEqualTo(text(missing));
            assertThat(text(call(client, "get_merge_request", mergeRequest("team-a/app"))))
                    .isEqualTo(text(call(client, "get_merge_request", mergeRequest(MISSING_PROJECT))));
        }
    }

    @Test
    void aLinkThatCoversTheGroupStillHidesTheProjectsTheLinkDoesNotCover() {
        try (McpSyncClient client = clientAs(user -> { reader(user); user.virtualServer("a-only", Map.of("gitlabGroups", List.of("team-a"))); },
                PAT, BackendCredential.Type.USER_SECRET)) {
            McpSchema.CallToolResult result = call(client, "list_projects", Map.of("group", "team-a"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("team-a/app").doesNotContain("finance/secret", "secret-plans");
        }
    }

    /**
     * Documents a known gap, not a wanted behaviour. GitLab's API has no topics for groups (only projects have them), so a
     * real group answers without the field and the server reads that as "no classification". Marking a group restricted
     * therefore does nothing until the contract names another source for group classification (STATUS, Propozycje CC 1).
     * Topics of projects work (see aProjectIsRestrictedByItsOwnTopicWhateverTheCase). When the source is decided, this
     * test changes together with the resolver.
     */
    @Test
    void knownGapAGroupThatReportsNoTopicsIsNotRestrictedWhateverItIsMeantToProtect() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "list_projects", Map.of("group", "legal"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("legal/app");
        }
    }
}
