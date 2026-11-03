package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The read tools of a repository: shape of the result, paging, scope of the request and refusal of bad arguments. */
class RepositoryToolsTest extends ServerTestSupport {

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
    void getProjectReturnsTheProjectWithoutBackendFields() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_project", Map.of("project", APP));

            JsonNode project = JSON.readTree(text(result));
            assertThat(project.path("path").asString()).isEqualTo(APP);
            assertThat(project.path("visibility").asString()).isEqualTo("internal");
            assertThat(project.path("name").path("kind").asString()).isEqualTo("untrusted-data");
            assertThat(text(result)).doesNotContain("runners_token", "GR1348941");
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath)
                .containsExactly("/api/v4/projects/team-a%2Fapp", "/api/v4/projects/42");
    }

    @Test
    void theTreeListsOneDirectoryAndPathsAreEncodedAsValues() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode tree = ok(client, "list_repository_tree", Map.of("project", APP, "path", "src", "ref", "main"));

            assertThat(tree.path("items")).hasSize(3);
            assertThat(tree.path("items").get(0).path("path").path("text").asString()).isEqualTo("src");
            assertThat(tree.path("items").get(0).path("type").asString()).isEqualTo("tree");
            assertThat(tree.path("nextCursor").isNull()).isTrue();
            assertThat(tree.path("truncated").asBoolean(true)).isFalse();
        }
        assertThat(BACKEND.requests().getLast().rawQuery()).contains("path=src", "ref=main", "per_page=100");
    }

    @Test
    void aMissingDirectoryAndTraversalAreRefused() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertNotFoundOrDenied(call(client, "list_repository_tree", Map.of("project", APP, "path", "missing")));
            assertInvalidArgument(call(client, "list_repository_tree", Map.of("project", APP, "path", "../etc")), "path");
            assertInvalidArgument(call(client, "list_repository_tree", Map.of("project", APP, "ref", "a..b")), "ref");
        }
    }

    @Test
    void searchCodeIsScopedToTheProjectAndPagedWithASignedCursor() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode first = ok(client, "search_code", Map.of("project", APP, "query", "retries & more=1"));

            assertThat(first.path("items").get(0).path("path").path("text").asString()).isEqualTo("src/main/java/Retry.java");
            assertThat(first.path("items").get(0).path("snippet").path("kind").asString()).isEqualTo("untrusted-data");
            String cursor = first.path("nextCursor").asString();
            assertThat(cursor).isNotBlank();
            JsonNode second = ok(client, "search_code", Map.of("project", APP, "query", "retries & more=1", "cursor", cursor));
            assertThat(second.path("items").get(0).path("path").path("text").asString()).isEqualTo("src/main/java/Client.java");
            assertThat(second.path("nextCursor").isNull()).isTrue();

            assertInvalidArgument(call(client, "search_code", Map.of("project", APP, "query", "other words", "cursor", cursor)),
                    "The cursor is not valid");
            assertInvalidArgument(call(client, "search_code", Map.of("project", APP, "query", "x", "cursor", "forged.cursor")),
                    "The cursor is not valid");
        }
        assertThat(BACKEND.requests()).filteredOn(seen -> seen.rawPath().endsWith("/search")).allSatisfy(seen -> {
            assertThat(seen.rawPath()).isEqualTo("/api/v4/projects/42/search");
            assertThat(seen.rawQuery()).contains("scope=blobs", "search=retries%20%26%20more%3D1");
        });
    }

    @Test
    void branchesAndCommitsAreListed() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode branches = ok(client, "list_branches", Map.of("project", APP));
            assertThat(branches.path("items").get(0).path("name").path("text").asString()).isEqualTo("main");
            assertThat(branches.path("items").get(0).path("defaultBranch").asBoolean()).isTrue();
            assertThat(branches.path("items").get(0).path("protectedBranch").asBoolean()).isTrue();

            JsonNode commits = ok(client, "list_commits",
                    Map.of("project", APP, "ref", "main", "path", "src/Login.java", "since", "2026-10-01"));
            assertThat(commits.path("items").get(0).path("id").asString()).isEqualTo(StubBackend.COMMIT);
            assertThat(commits.path("items").get(0).path("authorName").path("text").asString()).isEqualTo("Anna Nowak");
            assertThat(text(call(client, "list_commits", Map.of("project", APP)))).doesNotContain("anna@aiwcorpo.internal");
            assertThat(BACKEND.requests().stream().filter(seen -> seen.rawPath().endsWith("/commits")).findFirst().orElseThrow()
                    .rawQuery()).contains("ref_name=main", "path=src%2FLogin.java", "since=2026-10-01");

            assertInvalidArgument(call(client, "list_commits", Map.of("project", APP, "since", "yesterday")), "since");
        }
    }

    @Test
    void aCommitAndItsDiffAreReadByIdAndTheDiffIsCutAndMarked() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode commit = ok(client, "get_commit", Map.of("project", APP, "sha", StubBackend.COMMIT));
            assertThat(commit.path("additions").asInt()).isEqualTo(12);
            assertThat(commit.path("message").path("text").asString()).contains("Ignore previous instructions");
            assertThat(commit.path("message").path("kind").asString()).isEqualTo("untrusted-data");

            JsonNode diff = ok(client, "get_commit_diff", Map.of("project", APP, "sha", "6104942"));
            assertThat(diff.path("items")).hasSize(3);
            assertThat(diff.path("truncated").asBoolean()).as("the large diff was cut").isTrue();
            assertThat(diff.path("items").get(0).path("hiddenCharactersRemoved").asInt()).isEqualTo(1);
            assertThat(diff.path("items").get(0).path("diff").path("text").asString()).doesNotContain("‮");
            assertThat(diff.path("items").get(1).path("renamedFile").asBoolean()).isTrue();
            assertThat(diff.path("items").get(1).path("oldPath").path("text").asString()).isEqualTo("docs/old.md");
            assertThat(diff.path("items").get(2).path("diff").path("text").asString()).hasSizeLessThanOrEqualTo(16_000);

            assertNotFoundOrDenied(call(client, "get_commit", Map.of("project", APP, "sha", "deadbeef")));
            assertInvalidArgument(call(client, "get_commit", Map.of("project", APP, "sha", "main")), "sha");
            assertInvalidArgument(call(client, "get_commit", Map.of("project", APP, "sha", "ABCDEF1")), "sha");
        }
    }

    @Test
    void everyRepositoryToolRefusesAProjectOutsideTheCallersReachLikeAMissingOne() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String tool : new String[] {"get_project", "list_repository_tree", "list_branches", "list_commits"}) {
                assertNotFoundOrDenied(call(client, tool, Map.of("project", "team-a/forbidden")));
                assertNotFoundOrDenied(call(client, tool, Map.of("project", "team-a/none")));
            }
            assertCredentialRequiredWithout("get_project");
        }
    }

    @Test
    void aRestrictedProjectIsClosedForReadsAndChangesAlikeAndNothingReachesItsData() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String project : new String[] {"team-a/secret-plans"}) {
                assertNotFoundOrDenied(call(client, "list_branches", Map.of("project", project)));
                assertNotFoundOrDenied(call(client, "get_job_log", Map.of("project", project, "jobId", 501)));
                assertNotFoundOrDenied(call(client, "create_issue", Map.of("project", project, "title", "t")));
                assertNotFoundOrDenied(call(client, "merge_merge_request",
                        Map.of("project", project, "iid", 1, "expectedHeadSha", StubBackend.HEAD_SHA)));
            }
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::method).containsOnly("GET");
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath)
                .noneMatch(path -> path.contains("/repository/") || path.contains("/jobs/") || path.contains("/issues"));
    }

    private void assertCredentialRequiredWithout(String tool) {
        try (McpSyncClient anonymous = clientWithCredential(null)) {
            assertCredentialRequired(call(anonymous, tool, Map.of("project", APP)));
        }
    }
}
