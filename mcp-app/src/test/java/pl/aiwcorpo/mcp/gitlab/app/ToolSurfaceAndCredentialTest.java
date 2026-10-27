package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.platform.security.CredentialRequiredException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What tools/list publishes, and how each kind of C3 credential is presented to GitLab. */
@ExtendWith(OutputCaptureExtension.class)
class ToolSurfaceAndCredentialTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";
    private static final Map<String, Object> README = Map.of("project", "team-a/app", "path", "README.md");

    @Autowired
    GitLabPort gitlab;

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void publishesExactlyTheFourToolsWithStrictSchemasAndRiskHints() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            List<McpSchema.Tool> tools = client.listTools().tools();

            assertThat(tools).extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("list_projects", "get_file", "get_merge_request", "comment_merge_request");
            for (McpSchema.Tool tool : tools) {
                JsonNode schema = JsonMapper.builder().build().valueToTree(tool.inputSchema());
                assertThat(schema.path("additionalProperties").asBoolean(true)).as(tool.name()).isFalse();
                schema.path("properties").properties().forEach(property -> {
                    JsonNode limits = property.getValue();
                    assertThat(limits.has("maxLength") || limits.has("maximum"))
                            .as(tool.name() + "." + property.getKey() + " has a limit").isTrue();
                });
                assertThat(tool.description().toLowerCase())
                        .as("descriptions say what the tool does, not what the model should do")
                        .doesNotContain("always", "you must", "you should", "first call", "ignore");
            }
            McpSchema.Tool comment = byName(tools, "comment_merge_request");
            assertThat(comment.annotations().readOnlyHint()).isFalse();
            assertThat(comment.annotations().destructiveHint()).isFalse();
            assertThat(JsonMapper.builder().build().valueToTree(comment.inputSchema()).at("/properties/body/maxLength").asInt())
                    .isEqualTo(10_000);
            assertThat(JsonMapper.builder().build().valueToTree(byName(tools, "get_file").inputSchema()).path("required"))
                    .extracting(JsonNode::asString).containsExactlyInAnyOrder("project", "path");
            for (String readOnly : List.of("list_projects", "get_file", "get_merge_request")) {
                assertThat(byName(tools, readOnly).annotations().readOnlyHint()).as(readOnly).isTrue();
            }
        }
    }

    @Test
    void undeclaredArgumentsAndWrongTypesAreInvalidArguments() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertInvalidArgument(call(client, "get_file",
                    Map.of("project", "team-a/app", "path", "README.md", "private_token", "x")), "unknown arguments are not allowed");
            assertInvalidArgument(call(client, "get_file", Map.of("project", "team-a/app", "path", List.of("a", "b"))),
                    "path has the wrong type");
            assertInvalidArgument(call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 1.5)),
                    "iid has the wrong type");
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void identifierPatternsInTheSchemaAreTheContractPatterns() throws java.io.IOException {
        JsonNode defs;
        try (java.io.InputStream in = getClass().getResourceAsStream("/mcp-contracts/schemas/common.schema.json")) {
            defs = JsonMapper.builder().build().readTree(in).path("$defs");
        }
        try (McpSyncClient client = clientWithCredential(PAT)) {
            List<McpSchema.Tool> tools = client.listTools().tools();
            JsonMapper json = JsonMapper.builder().build();

            assertThat(json.valueToTree(byName(tools, "list_projects").inputSchema()).at("/properties/group/pattern").asString())
                    .isEqualTo(defs.path("GitLabGroupPath").path("pattern").asString());
            for (String tool : List.of("get_file", "get_merge_request", "comment_merge_request")) {
                assertThat(json.valueToTree(byName(tools, tool).inputSchema()).at("/properties/project/pattern").asString())
                        .as(tool).isEqualTo(defs.path("GitLabProjectPath").path("pattern").asString());
            }
        }
    }

    @Test
    void anUnknownArgumentNameIsNeitherEchoedNorLogged(CapturedOutput output) {
        String forged = "x\n{\"event\":\"tool_call\",\"outcome\":\"success\"}";
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_file",
                    Map.of("project", "team-a/app", "path", "README.md", forged, "x"));

            assertInvalidArgument(result, "unknown arguments are not allowed");
            assertThat(text(result)).doesNotContain("outcome");
        }
        assertThat(output.getAll()).doesNotContain("\"outcome\":\"success\"}\n");
        assertThat(output.getAll()).doesNotContain(forged);
    }

    @Test
    void anOAuthTokenGoesInTheAuthorizationHeader() {
        try (McpSyncClient client = clientWithCredential("Bearer gloas-oauth-of-jkowalski", "user-oauth")) {
            assertThat(call(client, "get_file", README).isError()).isFalse();
        }
        assertThat(BACKEND.credentials()).containsOnly("Authorization Bearer gloas-oauth-of-jkowalski");
    }

    @Test
    void aPersonalAccessTokenGoesInPrivateToken() {
        try (McpSyncClient client = clientWithCredential("Token glpat-jkowalski-7f3a", "user-secret")) {
            assertThat(call(client, "get_file", README).isError()).isFalse();
        }
        assertThat(BACKEND.requests()).allSatisfy(seen -> {
            assertThat(seen.privateToken()).isEqualTo("glpat-jkowalski-7f3a");
            assertThat(seen.authorization()).isNull();
        });
    }

    @Test
    void anExpiredOAuthTokenAsksToReconnect() {
        try (McpSyncClient client = clientWithCredential("Bearer " + StubBackend.EXPIRED_TOKEN, "user-oauth")) {
            assertCredentialRequired(call(client, "get_file", README));
        }
    }

    @Test
    void credentialsGitLabDoesNotTakeAreRefusedBeforeTheBackend() {
        try (McpSyncClient serviceAccount = clientWithCredential("Bearer svc-token", "service-account");
                McpSyncClient basic = clientWithCredential("Basic amtvd2Fsc2tpOnNlY3JldA==", "user-secret");
                McpSyncClient oauthAsApiKey = clientWithCredential("ApiKey gloas-oauth", "user-oauth")) {
            for (McpSyncClient client : List.of(serviceAccount, basic, oauthAsApiKey)) {
                McpSchema.CallToolResult result = call(client, "get_file", README);
                assertThat(result.isError()).isTrue();
                assertThat(text(result)).contains("\"error\":\"internal_error\"");
            }
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void theAdapterHasNoCredentialOutsideAGatewayRequest() {
        assertThatThrownBy(() -> gitlab.findProject("team-a/app")).isInstanceOf(CredentialRequiredException.class);
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void theCredentialAndTheBackendErrorBodyNeverAppearInTheResultOrTheLog(CapturedOutput output) {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_file", Map.of("project", "team-a/broken", "path", "README.md"));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"internal_error\"").doesNotContain("glpat-jkowalski");
        }
        assertThat(output.getAll()).contains("\"tool\":\"get_file\"").doesNotContain("glpat-jkowalski")
                .as("the backend's error body stays out of the log").doesNotContain("500 Internal Server Error");
    }

    private static McpSchema.Tool byName(List<McpSchema.Tool> tools, String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
    }
}
