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
 * {@code get_current_user} names no group or project, so the only gate is the read group of the system; what GitLab
 * knows about the user beyond id, name and state stays out of the result.
 */
class CurrentUserToolTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void returnsTheUserOfTheCredentialWithoutEmailOrAdminFlag() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_current_user", Map.of());

            assertThat(result.isError()).as(text(result)).isFalse();
            assertThat(text(result)).contains("\"id\":6", "\"state\":\"active\"")
                    .contains("\"text\":\"jkowalski\"", "\"text\":\"Jan Kowalski\"", "\"kind\":\"untrusted-data\"")
                    .doesNotContain("jan@aiwcorpo.internal", "is_admin", "email", "web_url");
        }
        assertThat(BACKEND.requests()).extracting(seen -> seen.method() + " " + seen.rawPath())
                .containsExactly("GET /api/v4/user");
        assertThat(BACKEND.credentials()).containsExactly("PRIVATE-TOKEN glpat-jkowalski-7f3a");
    }

    @Test
    void theReadGroupIsEnoughAndWithoutItTheCallIsDeniedBeforeGitLab() {
        try (McpSyncClient reader = clientAs(user -> user.username("jkowalski").groups(List.of(READ_GROUP)), PAT,
                BackendCredential.Type.USER_SECRET)) {
            assertThat(call(reader, "get_current_user", Map.of()).isError()).isFalse();
        }
        BACKEND.reset();
        try (McpSyncClient nobody = clientAs(user -> user.username("jkowalski").groups(List.of("mcp-nobody")), PAT,
                BackendCredential.Type.USER_SECRET)) {
            McpSchema.CallToolResult result = call(nobody, "get_current_user", Map.of());

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"access_denied\"");
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void withoutAUsableCredentialTheUserIsAskedToConnectTheAccount() {
        try (McpSyncClient anonymous = clientWithCredential(null)) {
            assertCredentialRequired(call(anonymous, "get_current_user", Map.of()));
        }
        assertThat(BACKEND.requests()).isEmpty();
        try (McpSyncClient expired = clientWithCredential("Bearer " + StubBackend.EXPIRED_TOKEN)) {
            assertCredentialRequired(call(expired, "get_current_user", Map.of()));
        }
    }

    @Test
    void takesNoArguments() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertInvalidArgument(call(client, "get_current_user", Map.of("username", "someone-else")),
                    "unknown arguments are not allowed");
        }
        assertThat(BACKEND.requests()).isEmpty();
    }
}
