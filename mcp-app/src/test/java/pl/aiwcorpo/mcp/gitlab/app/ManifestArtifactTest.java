package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import pl.aiwcorpo.mcp.manifest.ManifestRules;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code mcp-app/target/mcp-manifest.json}, the manifest CI registers with the gateway (CC-133, CC-140), and
 * checks it. It is the only test that sets {@code output-file}, and the pom runs it in its own execution in the
 * package phase, so a build with {@code -DskipTests} still produces the file. The values are the production ones:
 * the version of the pom (system property {@code manifest.version}) and the credential types of application.yml;
 * only the internal URL comes from the dev profile (production sets it with MCP_INTERNAL_URL).
 */
class ManifestArtifactTest extends ServerTestSupport {

    static final Path ARTIFACT = Path.of("target", "mcp-manifest.json");

    /**
     * Called by {@link ServerTestSupport} after the TestGateway values (a subclass's own dynamic properties would be
     * overridden by them). Only the package-phase execution sets {@code manifest.version}, so other contexts keep the
     * test values and never write the file.
     */
    static void useProductionManifestValues(DynamicPropertyRegistry registry) {
        String version = System.getProperty("manifest.version");
        if (version == null) {
            return;
        }
        registry.add("aiwcorpo.mcp.platform.manifest.output-file", () -> ARTIFACT.toString());
        registry.add("aiwcorpo.mcp.platform.manifest.version", () -> version);
        registry.add("aiwcorpo.mcp.platform.manifest.accepted-credential-types", ManifestArtifactTest::productionTypes);
    }

    static String productionTypes() {
        try {
            List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));
            String declared = String.valueOf(documents.getFirst()
                    .getProperty("aiwcorpo.mcp.platform.manifest.accepted-credential-types"));
            return new StandardEnvironment().resolvePlaceholders(declared);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    @Test
    void theArtifactIsWrittenFromTheProductionValuesAndEqualsTheServedManifest() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        assertThat(ARTIFACT).exists();
        JsonNode artifact = json.readTree(Files.readString(ARTIFACT));

        HttpResponse<String> served = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp-manifest"))
                        .headers(GATEWAY.service().mint().headers().entrySet().stream()
                                .flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue()))
                                .toArray(String[]::new))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(served.statusCode()).isEqualTo(200);

        assertThat(artifact).isEqualTo(json.readTree(served.body()));
        assertThat(artifact.has("environment")).as("outside devstack the field is absent").isFalse();
        assertThat(artifact.path("version").asString())
                .isEqualTo(System.getProperty("manifest.version")).isNotEqualTo("1.0.0");
        assertThat(artifact.at("/credential/acceptedTypes")).extracting(JsonNode::asString)
                .containsExactly("user-oauth", "user-secret");
        assertThat(ManifestRules.violations(artifact.toString())).isEmpty();
        assertThat(artifact.get("tools")).hasSize(4);
    }
}
