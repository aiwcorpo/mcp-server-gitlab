package pl.aiwcorpo.mcp.gitlab.app;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.test.TestGateway;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole server over HTTP, with TestGateway in place of the gateway and {@link StubBackend} in place of
 * the backend system. Shared by the test classes so they run against one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
abstract class ServerTestSupport {

    static final String SERVER_ID = "mcp-gitlab";
    static final String SYSTEM = "gitlab";
    static final String READ_GROUP = "mcp-gitlab-read";
    static final String WRITE_GROUP = "mcp-gitlab-write";

    /** True only for a system outside the contracts' SystemId; gitlab is on the list, so it is false. */
    static final boolean TRIAL_SYSTEM = false;
    static final String ENDPOINT = "/mcp";
    static final Set<String> CONTRACT_SYSTEMS = contractSystems();

    static final TestGateway GATEWAY = TestGateway.start(SERVER_ID);
    static final StubBackend BACKEND = StubBackend.start();

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        GATEWAY.serverProperties().forEach((name, value) -> registry.add(name, () -> value));
        registry.add("gitlab.backend.base-url", BACKEND::baseUrl);
        // Short limits so the timeout and size tests run quickly; StubBackend's slow and huge answers exceed them.
        registry.add("gitlab.backend.read-timeout", () -> "1s");
        registry.add("gitlab.backend.max-response-size", () -> "64KB");
        registry.add("gitlab.files.max-bytes", () -> "65536");
    }

    @LocalServerPort
    int port;

    String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /** An MCP client that reaches the server the way the gateway does, with {@code credential} as C3 header. */
    McpSyncClient clientWithCredential(String credential) {
        return clientWithCredential(credential, BackendCredential.Type.USER_SECRET);
    }

    /** Same, with {@code X-MCP-Credential-Type} set to {@code type}: GitLab is also connected through OAuth. */
    McpSyncClient clientWithCredential(String credential, BackendCredential.Type type) {
        return clientAs(ServerTestSupport::reader, credential, type);
    }

    /** The caller of most tests: a member of the read and write groups of the system (C9). */
    static void reader(TestGateway.Token user) {
        user.username("jkowalski").groups(List.of(READ_GROUP, WRITE_GROUP));
    }

    /** Same, with the JWT claims of the caller set by {@code user} (a call through a link carries {@code vs}). */
    McpSyncClient clientAs(Consumer<TestGateway.Token> user, String credential, BackendCredential.Type type) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl())
                .endpoint(ENDPOINT)
                .openConnectionOnStartup(false)
                .httpRequestCustomizer(GATEWAY.asGateway(user, credential, type))
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
        return client;
    }

    /**
     * The starter sends only C10 errors valid against {@code tool-error.schema.json}, whose {@code system} is the
     * closed {@code SystemId} list of the contracts. A trial system (outside the list) gets {@code internal_error}
     * instead; a real system must get the full error with the portal link, never {@code internal_error}.
     */
    static void assertCredentialRequired(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        if (TRIAL_SYSTEM) {
            assertThat(CONTRACT_SYSTEMS).as("a trial system is outside SystemId").doesNotContain(SYSTEM);
            assertThat(text(result)).contains("\"error\":\"internal_error\"");
        } else {
            assertThat(text(result)).contains("\"error\":\"credential_required\"", "\"system\":\"" + SYSTEM + "\"",
                    "\"connectUrl\":\"http://localhost:18090/connections?system=" + SYSTEM + "\"");
        }
    }

    @Test
    void contractSystemIsKnown() {
        assertThat(CONTRACT_SYSTEMS.contains(SYSTEM)).isEqualTo(!TRIAL_SYSTEM);
    }

    private static Set<String> contractSystems() {
        try (InputStream schema = ServerTestSupport.class.getClassLoader()
                .getResourceAsStream("mcp-contracts/schemas/common.schema.json")) {
            Set<String> systems = new HashSet<>();
            JsonMapper.builder().build().readTree(schema).at("/$defs/SystemId/enum")
                    .forEach(system -> systems.add(system.asString()));
            return Set.copyOf(systems);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** 404 and 403 from GitLab, and every other "not visible", look exactly the same to the client. */
    static void assertNotFoundOrDenied(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo(
                "{\"error\":\"not_found_or_denied\",\"message\":\"The GitLab resource does not exist or you have no access to it.\"}");
    }

    static void assertInvalidArgument(McpSchema.CallToolResult result, String messageFragment) {
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("\"error\":\"invalid_argument\"", messageFragment);
    }

    static McpSchema.CallToolResult call(McpSyncClient client, String tool, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(tool).arguments(arguments).build());
    }

    static String text(McpSchema.CallToolResult result) {
        StringBuilder text = new StringBuilder();
        result.content().forEach(content -> {
            if (content instanceof McpSchema.TextContent t) {
                text.append(t.text());
            }
        });
        return text.toString();
    }
}
