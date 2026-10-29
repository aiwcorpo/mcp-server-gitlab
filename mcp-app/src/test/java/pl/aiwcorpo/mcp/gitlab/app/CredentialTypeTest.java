package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GitLab accepts {@code user-oauth} (default, the first of the set) and {@code user-secret}. Every other type, and
 * any request whose {@code X-MCP-Credential-Type} is missing, spelled differently or outside the set, ends in
 * {@code internal_error} from the starter before the tool, the policy engine and the backend run.
 */
class CredentialTypeTest extends ServerTestSupport {

    private static final Map<String, Object> README = Map.of("project", "team-a/app", "path", "README.md");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void anOAuthTokenIsAcceptedAndSentAsBearer() {
        try (McpSyncClient client = clientWithCredential("Bearer gloas-oauth-of-jkowalski", BackendCredential.Type.USER_OAUTH)) {
            assertThat(call(client, "get_file", README).isError()).isFalse();
        }
        assertThat(BACKEND.requests()).isNotEmpty().allSatisfy(seen -> {
            assertThat(seen.authorization()).isEqualTo("Bearer gloas-oauth-of-jkowalski");
            assertThat(seen.privateToken()).isNull();
        });
    }

    @Test
    void aPersonalTokenIsAcceptedAndSentInPrivateToken() {
        try (McpSyncClient client = clientWithCredential("Token glpat-jkowalski-7f3a", BackendCredential.Type.USER_SECRET)) {
            assertThat(call(client, "get_file", README).isError()).isFalse();
        }
        assertThat(BACKEND.requests()).isNotEmpty().allSatisfy(seen -> {
            assertThat(seen.privateToken()).isEqualTo("glpat-jkowalski-7f3a");
            assertThat(seen.authorization()).isNull();
        });
    }

    @Test
    void aServiceAccountIsRefusedOnEveryToolBeforeTheBackend() {
        try (McpSyncClient client = clientWithCredential("Bearer svc-token", BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertRefusedAsInternalError(call(client, "get_file", README));
            assertRefusedAsInternalError(call(client, "list_projects", Map.of("group", "team-a")));
            assertRefusedAsInternalError(call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 1)));
            assertRefusedAsInternalError(call(client, "comment_merge_request",
                    Map.of("project", "team-a/app", "iid", 1, "body", "ok")));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void aTokenExchangeIsRefusedBeforeTheBackend() {
        try (McpSyncClient client = clientWithCredential("Bearer exchanged-token", BackendCredential.Type.TOKEN_EXCHANGE)) {
            assertRefusedAsInternalError(call(client, "get_file", README));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void aMissingOrMisspelledTypeHeaderIsRefusedBeforeTheBackend() throws Exception {
        assertRefusedAsInternalError(rawGetFile(Map.of("X-MCP-Credential", "Bearer gloas-oauth-of-jkowalski")));
        assertRefusedAsInternalError(rawGetFile(Map.of("X-MCP-Credential", "Bearer gloas-oauth-of-jkowalski",
                "X-MCP-Credential-Type", "User-OAuth")));
        assertRefusedAsInternalError(rawGetFile(Map.of("X-MCP-Credential", "Bearer gloas-oauth-of-jkowalski",
                "X-MCP-Credential-Type", "pat")));
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void aRequestWithoutACredentialStillAsksToConnect() {
        try (McpSyncClient client = clientAs(ServerTestSupport::reader, null, BackendCredential.Type.USER_OAUTH)) {
            assertCredentialRequired(call(client, "get_file", README));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    private static void assertRefusedAsInternalError(io.modelcontextprotocol.spec.McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("\"error\":\"internal_error\"").doesNotContain("credential_required");
    }

    private static void assertRefusedAsInternalError(String toolResultText) {
        assertThat(toolResultText).contains("\"error\":\"internal_error\"");
    }

    /** A get_file call the way the gateway sends it, with exactly the credential headers given. */
    private String rawGetFile(Map<String, String> credentialHeaders) throws Exception {
        Map<String, String> initHeaders = GATEWAY.user().username("jkowalski")
                .groups(List.of(READ_GROUP, WRITE_GROUP, SCOPE_GROUP)).mint().headers();
        HttpResponse<String> initialize = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                "capabilities":{},"clientInfo":{"name":"credential-type-test","version":"1"}}}""", initHeaders, null);
        assertThat(initialize.statusCode()).isEqualTo(200);
        String session = initialize.headers().firstValue("Mcp-Session-Id").orElseThrow();
        post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", initHeaders, session);

        Map<String, String> callHeaders = new java.util.HashMap<>(GATEWAY.user().username("jkowalski")
                .groups(List.of(READ_GROUP, WRITE_GROUP, SCOPE_GROUP)).tool("get_file").mint().headers());
        callHeaders.putAll(credentialHeaders);
        String body = post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_file",
                "arguments":{"project":"team-a/app","path":"README.md"}}}""", callHeaders, session).body();
        int data = body.indexOf("data:");
        JsonNode message = JSON.readTree(data >= 0 ? body.substring(data + 5).strip() : body);
        assertThat(message.at("/result/isError").asBoolean()).isTrue();
        return message.at("/result/content/0/text").asString();
    }

    private HttpResponse<String> post(String body, Map<String, String> headers, String session) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        if (session != null) {
            request.header("Mcp-Session-Id", session);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
