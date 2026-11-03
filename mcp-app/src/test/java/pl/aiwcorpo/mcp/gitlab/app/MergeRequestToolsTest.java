package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabCapabilities;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Merge request listing, diffs, notes and the changes: create, update, approve, merge. */
class MergeRequestToolsTest extends ServerTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PAT = "Bearer glpat-jkowalski-7f3a";
    private static final String APP = "team-a/app";

    @Autowired
    GitLabCapabilities capabilities;

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
        capabilities.forget();
    }

    @AfterEach
    void forgetTheVersion() {
        capabilities.forget();
    }

    private JsonNode ok(McpSyncClient client, String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = call(client, tool, arguments);
        assertThat(result.isError()).as(text(result)).isFalse();
        return JSON.readTree(text(result));
    }

    private static String listQuery() {
        return BACKEND.requests().stream().filter(seen -> seen.rawPath().equals("/api/v4/projects/42/merge_requests"))
                .findFirst().orElseThrow().rawQuery();
    }

    @Test
    void theListingReadsTheShapeOfGitLab17AndPagesThroughASignedCursor() {
        BACKEND.useFixtures("gitlab-17.8");
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode first = ok(client, "list_merge_requests", Map.of("project", APP));

            JsonNode items = first.path("items");
            assertThat(items).hasSize(3);
            assertThat(items.get(0).path("iid").asInt()).isEqualTo(13);
            assertThat(items.get(0).path("draft").asBoolean()).isFalse();
            assertThat(items.get(1).path("draft").asBoolean()).isTrue();
            assertThat(items.get(1).path("title").path("kind").asString()).isEqualTo("untrusted-data");
            assertThat(text(call(client, "list_merge_requests", Map.of("project", APP)))).doesNotContain("Anna Nowak", "avatar");
            JsonNode second = ok(client, "list_merge_requests",
                    Map.of("project", APP, "cursor", first.path("nextCursor").asString()));
            assertThat(second.path("items")).hasSize(1);
            assertThat(second.path("nextCursor").isNull()).isTrue();
        }
        assertThat(listQuery()).contains("order_by=updated_at", "per_page=20");
    }

    @Test
    void theListingReadsTheShapeOfGitLab19() {
        BACKEND.useFixtures("gitlab-19.5");
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode page = ok(client, "list_merge_requests", Map.of("project", APP));

            assertThat(page.path("items")).hasSize(3);
            assertThat(page.path("items").get(1).path("draft").asBoolean()).isTrue();
            assertThat(page.path("items").get(2).path("state").asString()).isEqualTo("merged");
        }
    }

    @Test
    void filtersGoToGitLabAsClosedParametersAndTheCursorIsTiedToThem() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode first = ok(client, "list_merge_requests", Map.of("project", APP, "state", "opened",
                    "author", "anowak", "reviewer", "pzielinski", "labels", List.of("backend", "needs review"),
                    "targetBranch", "main"));
            assertThat(listQuery()).contains("state=opened", "author_username=anowak", "reviewer_username=pzielinski",
                    "labels=backend%2Cneeds%20review", "target_branch=main");

            assertInvalidArgument(call(client, "list_merge_requests", Map.of("project", APP, "state", "opened",
                    "cursor", first.path("nextCursor").asString())), "The cursor is not valid");
            assertInvalidArgument(call(client, "list_merge_requests", Map.of("project", APP, "state", "draft")), "state");
            assertInvalidArgument(call(client, "list_merge_requests", Map.of("project", APP, "author", "a&b=c")), "author");
            assertInvalidArgument(call(client, "list_merge_requests", Map.of("project", APP, "labels", List.of("a,b"))), "labels");
        }
    }

    @Test
    void theDraftFilterFollowsTheVersionGitLabReports() {
        for (Object[] row : new Object[][] {{"gitlab-17.8", "wip=yes"}, {"gitlab-19.5", "draft=yes"}}) {
            BACKEND.reset();
            capabilities.forget();
            BACKEND.useFixtures((String) row[0]);
            try (McpSyncClient client = clientWithCredential(PAT)) {
                ok(client, "list_merge_requests", Map.of("project", APP, "draft", true));
            }
            assertThat(listQuery()).as((String) row[0]).contains((String) row[1]);
            assertThat(capabilities.current().state().name()).isEqualTo("SUPPORTED");
        }
    }

    @Test
    void anUntestedVersionFiltersDraftsHereInsteadOfSendingAParameterItMayHaveDropped() {
        BACKEND.useFixtures("gitlab-legacy");
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode drafts = ok(client, "list_merge_requests", Map.of("project", APP, "draft", true));
            assertThat(drafts.path("items")).extracting(item -> item.path("iid").asInt()).containsExactly(12, 11);
            assertThat(listQuery()).doesNotContain("draft=", "wip=");

            JsonNode others = ok(client, "list_merge_requests", Map.of("project", APP, "draft", false));
            assertThat(others.path("items")).extracting(item -> item.path("iid").asInt()).containsExactly(13);
        }
        assertThat(capabilities.current().state().name()).isEqualTo("DEGRADED");
    }

    @Test
    void theVersionIsLearnedFromTheUsersOwnCredentialOnceAndNotWithoutOne() {
        assertThat(capabilities.current().state().name()).isEqualTo("DEGRADED");
        try (McpSyncClient client = clientWithCredential("Bearer gloas-oauth-of-jkowalski", pl.aiwcorpo.mcp.platform.security.BackendCredential.Type.USER_OAUTH)) {
            ok(client, "get_project", Map.of("project", APP));
            ok(client, "get_project", Map.of("project", APP));
        }
        assertThat(BACKEND.versionRequests()).hasSize(1);
        assertThat(BACKEND.versionRequests().getFirst().credential()).isEqualTo("Authorization Bearer gloas-oauth-of-jkowalski");
        assertThat(capabilities.current().backendVersion()).isEqualTo("17.8.2-ee");
        capabilities.forget();
        try (McpSyncClient anonymous = clientWithCredential(null)) {
            assertCredentialRequired(call(anonymous, "get_project", Map.of("project", APP)));
        }
        assertThat(BACKEND.versionRequests()).as("no credential, no call").hasSize(1);
    }

    @Test
    void aPageWithLongDescriptionsIsReadAboveTheSizeOfAFileAndNotRefused() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode page = ok(client, "list_merge_requests", Map.of("project", APP, "labels", List.of("big")));

            assertThat(page.path("items")).extracting(item -> item.path("iid").asInt()).containsExactly(101, 102);
            assertThat(text(call(client, "list_merge_requests", Map.of("project", APP, "labels", List.of("big")))))
                    .as("the listing leaves the descriptions out").doesNotContain("word word");
        }
    }

    @Test
    void getMergeRequestReadsBothNamesOfTheFieldsGitLabRenamed() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode mr = ok(client, "get_merge_request", Map.of("project", APP, "iid", 7));
            assertThat(mr.path("mergedBy").path("text").asString()).as("merge_user").isEqualTo("jkowalski");
            assertThat(mr.path("headSha").asString()).isEqualTo("7777777777777777777777777777777777777777");
            assertThat(mr.path("state").asString()).isEqualTo("merged");
        }
    }

    @Test
    void diffsAndNotesAreNestedUnderTheProjectAndForeignNotesAreDropped() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode diffs = ok(client, "list_merge_request_diffs", Map.of("project", APP, "iid", 1));
            assertThat(diffs.path("items")).hasSize(3);
            assertThat(diffs.path("truncated").asBoolean()).isTrue();

            JsonNode notes = ok(client, "list_merge_request_notes", Map.of("project", APP, "iid", 1));
            assertThat(notes.path("items")).extracting(note -> note.path("id").asInt()).containsExactly(301, 302, 303);
            assertThat(notes.path("items").get(1).path("system").asBoolean()).isTrue();
            assertThat(notes.path("items").get(2).path("body").path("truncated").asBoolean()).isTrue();
            assertThat(notes.path("truncated").asBoolean()).isTrue();
            assertThat(text(call(client, "list_merge_request_notes", Map.of("project", APP, "iid", 1))))
                    .as("a note that names another merge request").doesNotContain("confused backend");

            assertNotFoundOrDenied(call(client, "list_merge_request_diffs", Map.of("project", APP, "iid", 99)));
            assertNotFoundOrDenied(call(client, "list_merge_request_notes", Map.of("project", APP, "iid", 99)));
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath)
                .filteredOn(path -> path.contains("/merge_requests/")).allMatch(path -> path.startsWith("/api/v4/projects/42/"));
    }

    @Test
    void createSendsOnlyTheGivenFieldsAndTurnsGitLabsRefusalsIntoFixedMessages() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode created = ok(client, "create_merge_request", Map.of("project", APP, "sourceBranch", "feature/retry",
                    "targetBranch", "main", "title", "Add retries"));
            assertThat(created.path("iid").asInt()).isEqualTo(20);
            assertThat(created.path("project").asString()).isEqualTo(APP);
            StubBackend.Seen sent = BACKEND.requests().getLast();
            assertThat(sent.method()).isEqualTo("POST");
            assertThat(sent.body()).contains("\"source_branch\":\"feature/retry\"").doesNotContain("description");

            McpSchema.CallToolResult duplicate = call(client, "create_merge_request", Map.of("project", APP,
                    "sourceBranch", "feature/dup", "targetBranch", "main", "title", "Again"));
            assertInvalidArgument(duplicate, "conflicts with the current state");
            assertThat(text(duplicate)).doesNotContain("Another open merge request", "!19");
            assertInvalidArgument(call(client, "create_merge_request", Map.of("project", APP, "sourceBranch", "no-such-branch",
                    "targetBranch", "main", "title", "x")), "not valid in the current state");

            assertInvalidArgument(call(client, "create_merge_request", Map.of("project", APP, "sourceBranch", "a",
                    "targetBranch", "main", "title", "x", "description", "ok\n/merge")), "quick action");
            assertInvalidArgument(call(client, "create_merge_request", Map.of("project", APP, "sourceBranch", "a",
                    "targetBranch", "main", "title", "two\nlines")), "title");
        }
    }

    @Test
    void updateSetsTheGivenFieldsAndNeedsAtLeastOne() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode changed = ok(client, "update_merge_request", Map.of("project", APP, "iid", 1, "title", "New title",
                    "labels", List.of("backend")));
            assertThat(changed.path("title").path("text").asString()).isEqualTo("New title");
            StubBackend.Seen sent = BACKEND.requests().getLast();
            assertThat(sent.method()).isEqualTo("PUT");
            assertThat(sent.body()).contains("\"title\":\"New title\"", "\"labels\":\"backend\"").doesNotContain("description");

            ok(client, "update_merge_request", Map.of("project", APP, "iid", 1, "labels", List.of()));
            assertThat(BACKEND.requests().getLast().body()).contains("\"labels\":\"\"");

            assertInvalidArgument(call(client, "update_merge_request", Map.of("project", APP, "iid", 1)), "at least one");
            assertNotFoundOrDenied(call(client, "update_merge_request", Map.of("project", APP, "iid", 99, "title", "x")));
        }
    }

    @Test
    void approveIsBoundToTheReviewedCommit() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode approved = ok(client, "approve_merge_request",
                    Map.of("project", APP, "iid", 1, "expectedHeadSha", StubBackend.HEAD_SHA));
            assertThat(approved.path("approvals").asInt()).isEqualTo(2);
            assertThat(approved.path("headSha").asString()).isEqualTo(StubBackend.HEAD_SHA);
            assertThat(BACKEND.requests().getLast().body()).contains(StubBackend.HEAD_SHA);

            assertInvalidArgument(call(client, "approve_merge_request",
                    Map.of("project", APP, "iid", 1, "expectedHeadSha", "9".repeat(40))), "conflicts with the current state");
            assertInvalidArgument(call(client, "approve_merge_request",
                    Map.of("project", APP, "iid", 1, "expectedHeadSha", "abc")), "expectedHeadSha");
            assertInvalidArgument(call(client, "approve_merge_request", Map.of("project", APP, "iid", 1)), "expectedHeadSha");
        }
    }

    @Test
    void aRepeatedApprovalByTheSameUserIsReportedAsApproved() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode again = ok(client, "approve_merge_request", Map.of("project", APP, "iid", 6, "expectedHeadSha", "6".repeat(40)));
            assertThat(again.path("approvals").asInt()).isEqualTo(2);
            assertThat(again.path("headSha").asString()).isEqualTo("6".repeat(40));

            assertNotFoundOrDenied(call(client, "approve_merge_request",
                    Map.of("project", APP, "iid", 6, "expectedHeadSha", "5".repeat(40))));
        }
    }

    @Test
    void anApprovalGitLabAnswersWith401IsADenialWhenTheTokenStillWorksAndAReconnectWhenItDoesNot() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertNotFoundOrDenied(call(client, "approve_merge_request",
                    Map.of("project", APP, "iid", 2, "expectedHeadSha", StubBackend.HEAD_SHA)));
        }
        assertThat(BACKEND.requests()).extracting(StubBackend.Seen::rawPath).contains("/api/v4/user");
        try (McpSyncClient client = clientWithCredential("Bearer " + StubBackend.EXPIRED_TOKEN)) {
            assertCredentialRequired(call(client, "approve_merge_request",
                    Map.of("project", APP, "iid", 1, "expectedHeadSha", StubBackend.HEAD_SHA)));
        }
    }

    @Test
    void mergeNeedsTheReviewedCommitAndARepeatReportsTheStateInsteadOfAnError() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            JsonNode merged = ok(client, "merge_merge_request", Map.of("project", APP, "iid", 1,
                    "expectedHeadSha", StubBackend.HEAD_SHA, "squash", true));
            assertThat(merged.path("state").asString()).isEqualTo("merged");
            assertThat(merged.path("mergeCommitId").asString()).isEqualTo("9".repeat(40));
            assertThat(merged.path("alreadyMerged").asBoolean()).isFalse();
            StubBackend.Seen sent = BACKEND.requests().getLast();
            assertThat(sent.method()).isEqualTo("PUT");
            assertThat(sent.body()).contains("\"sha\":\"" + StubBackend.HEAD_SHA + "\"", "\"squash\":true")
                    .as("a merge through the server never deletes a branch").contains("\"should_remove_source_branch\":false");

            assertInvalidArgument(call(client, "merge_merge_request",
                    Map.of("project", APP, "iid", 1, "expectedHeadSha", "9".repeat(40))), "conflicts with the current state");

            JsonNode again = ok(client, "merge_merge_request",
                    Map.of("project", APP, "iid", 7, "expectedHeadSha", "7".repeat(40)));
            assertThat(again.path("alreadyMerged").asBoolean()).isTrue();

            assertInvalidArgument(call(client, "merge_merge_request",
                    Map.of("project", APP, "iid", 7, "expectedHeadSha", "6".repeat(40))), "not valid in the current state");
            assertInvalidArgument(call(client, "merge_merge_request",
                    Map.of("project", APP, "iid", 8, "expectedHeadSha", "8080808080808080808080808080808080808080")),
                    "not valid in the current state");
            assertNotFoundOrDenied(call(client, "merge_merge_request",
                    Map.of("project", APP, "iid", 99, "expectedHeadSha", StubBackend.HEAD_SHA)));
        }
    }

    @Test
    void aRateLimitOfGitLabIsReportedWithTheWaitItAskedFor() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_merge_request", Map.of("project", APP, "iid", 10));

            assertThat(result.isError()).isTrue();
            JsonNode error = JSON.readTree(text(result));
            assertThat(error.path("error").asString()).isEqualTo("rate_limited");
            assertThat(error.path("retryAfterSeconds").asInt()).isEqualTo(17);
            assertThat(text(result)).doesNotContain("Retry later");
        }
    }

    @Test
    void theWritesNeverReachGitLabWithoutTheWriteGroup() {
        try (McpSyncClient client = clientAs(user -> user.username("reader").groups(List.of(READ_GROUP, SCOPE_GROUP)), PAT,
                pl.aiwcorpo.mcp.platform.security.BackendCredential.Type.USER_SECRET)) {
            for (String tool : new String[] {"merge_merge_request", "approve_merge_request"}) {
                McpSchema.CallToolResult result = call(client, tool,
                        Map.of("project", APP, "iid", 1, "expectedHeadSha", StubBackend.HEAD_SHA));
                assertThat(result.isError()).as(tool).isTrue();
                assertThat(text(result)).as(tool).doesNotContain("merged", "approvals");
            }
            assertThat(call(client, "create_merge_request", Map.of("project", APP, "sourceBranch", "a", "targetBranch", "b",
                    "title", "t")).isError()).isTrue();
        }
        assertThat(BACKEND.requests()).noneMatch(seen -> !seen.method().equals("GET"));
    }
}
