package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * The gateway puts {@code vs} and an empty {@code vsc} into every JWT of a call made through a link (C2, CC-027);
 * the server has to accept them. Narrowing by link scopes is not implemented here (M2).
 */
class GatewayLinkClaimsTest extends ServerTestSupport {

    @Test
    void aCallThroughALinkWithoutNarrowingIsServed() {
        try (McpSyncClient client = clientAs(
                user -> { reader(user); user.virtualServer("team-a-review", Map.of()); },
                "Bearer glpat-jkowalski-7f3a", BackendCredential.Type.USER_SECRET)) {
            McpSchema.CallToolResult result = call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 1));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"iid\":1");
        }
    }
}
