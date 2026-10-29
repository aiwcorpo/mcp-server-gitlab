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

    /**
     * Decision of 2026-10-28: only projects are classified in GitLab. A group is never restricted, even when GitLab reports
     * topics for it, and neither are the projects below it; a project is restricted by its own topics (see
     * aProjectIsRestrictedByItsOwnTopicWhateverTheCase).
     */
    @Test
    void aGroupIsNotClassifiedEvenWhenGitLabReportsTopicsForIt() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String group : List.of("hr", "hr/payroll/team")) {
                McpSchema.CallToolResult result = call(client, "list_projects", Map.of("group", group));

                assertThat(result.isError()).as(group).isFalse();
            }
            assertThat(text(call(client, "list_projects", Map.of("group", "hr/payroll/team")))).contains("hr/payroll/team/app");
            assertThat(text(call(client, "list_projects", Map.of("group", "hr-eu")))).contains("hr-eu/app");
        }
    }

    @Test
    void theGroupsAboveAProjectAreNeverAskedAbout() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "list_projects", Map.of("group", "team-a/sub"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("team-a/sub/deep");
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath)
                .noneMatch(path -> path.equals("/api/v4/groups/team-a"));
    }

    @Test
    void aGroupAboveThatGitLabHidesOrFailsDoesNotChangeTheAnswer() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String group : List.of("locked/inner", "broken-parent/inner")) {
                assertThat(call(client, "list_projects", Map.of("group", group)).isError()).as(group).isFalse();
            }
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath)
                .noneMatch(path -> path.equals("/api/v4/groups/locked") || path.equals("/api/v4/groups/broken-parent"));
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
    void aProjectOutsideTheLinkIsRefusedBeforeTheResolverAndLooksLikeAMissingOne() {
        try (McpSyncClient client = clientAs(user -> { reader(user); user.virtualServer("sub-only", Map.of("gitlabGroups", List.of("team-a/sub"))); },
                PAT, BackendCredential.Type.USER_SECRET)) {
            assertNotFoundOrDenied(call(client, "get_file", file("team-a/app")));
            assertNotFoundOrDenied(call(client, "get_merge_request", mergeRequest("team-a/app")));
            assertNotFoundOrDenied(call(client, "get_file", file(MISSING_PROJECT)));
        }
        assertThat(BACKEND.requests()).as("no grant, so GitLab is not asked about the project").isEmpty();
    }

    @Test
    void aWriterWithoutAScopeGrantIsRefusedBeforeTheResolverAndNothingReachesGitLab() {
        try (McpSyncClient client = clientAs(user -> user.username("jkowalski").groups(List.of(READ_GROUP, WRITE_GROUP)), PAT,
                BackendCredential.Type.USER_SECRET)) {
            assertNotFoundOrDenied(call(client, "comment_merge_request", Map.of("project", "team-a/app", "iid", 1, "body", "ok")));
            assertNotFoundOrDenied(call(client, "comment_merge_request", Map.of("project", MISSING_PROJECT, "iid", 1, "body", "ok")));
        }
        assertThat(BACKEND.requests()).as("no grant on the project, so GitLab is not asked about it").isEmpty();
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

}
