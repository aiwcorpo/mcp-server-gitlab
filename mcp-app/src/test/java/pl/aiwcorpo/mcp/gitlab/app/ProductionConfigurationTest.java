package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabBackendProperties;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabHttp;
import tools.jackson.core.Version;
import tools.jackson.core.json.PackageVersion;

/** What the production configuration must keep, checked without starting a server. */
class ProductionConfigurationTest {

    @Test
    void theServerAsksForNoClientCertificateAndLeavesTlsToTheStarter() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        // The gateway proves itself with the internal JWT, not with a certificate; the certificate of the server comes from
        // MCP_TLS_*, or the starter generates one (CC-249), so the file sets neither the client auth nor a bundle.
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.containsProperty("server.ssl.client-auth")).isFalse();
            assertThat(document.containsProperty("server.ssl.bundle")).isFalse();
            assertThat(document.containsProperty("spring.ssl.bundle.jks.mcp-server.keystore.location")).isFalse();
        });
    }

    @Test
    void theBaseConfigurationNeedsTheManifestButNeverAllowsTestKeys() throws IOException {
        PropertySource<?> base = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).getFirst();

        assertThat(base.containsProperty("aiwcorpo.mcp.platform.policy-data.url")).as("pushed by the gateway").isFalse();
        assertThat(((Map<String, Object>) base.getSource()).keySet())
                .noneMatch(key -> key.startsWith("aiwcorpo.mcp.platform.policy."));
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.manifest.version")).isTrue();
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.manifest.internal-url")).as("informational and optional").isFalse();
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.manifest.accepted-credential-types")).isTrue();
        assertThat(new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml")))
                .allSatisfy(document -> {
                    assertThat(document.containsProperty("aiwcorpo.mcp.platform.internal-jwt.allow-test-keys")).isFalse();
                });
    }

    @Test
    void theDefaultCredentialTypeOfProductionIsOAuthAndThePersonalTokenFollows() throws IOException {
        PropertySource<?> base = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).getFirst();
        String declared = String.valueOf(base.getProperty("aiwcorpo.mcp.platform.manifest.accepted-credential-types"));

        assertThat(declared).isEqualTo("${MCP_ACCEPTED_CREDENTIAL_TYPES:user-oauth,user-secret}");
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.credential-type")).isFalse();
    }

    @Test
    void theEmbeddedTomcatAndJacksonIncludeTheirSecurityFixes() {
        // The Boot parent stays on the starter's version, so the pom overrides these two; a parent bump must not lower them.
        int[] tomcat = Arrays.stream(ServerInfo.getServerNumber().split("\\.")).mapToInt(Integer::parseInt).toArray();
        assertThat(Arrays.compare(tomcat, new int[] {11, 0, 27})).as("Tomcat %s", ServerInfo.getServerNumber()).isNotNegative();
        assertThat(PackageVersion.VERSION).isGreaterThanOrEqualTo(new Version(3, 1, 7, null, null, null));
    }

    @Test
    void testKeysAndTheLocalJwksAreConfiguredInDevOnly() throws IOException {
        Map<String, Object> dev = (Map<String, Object>) new YamlPropertySourceLoader()
                .load("application-dev.yml", new ClassPathResource("application-dev.yml")).getFirst().getSource();

        assertThat(String.valueOf(dev.get("aiwcorpo.mcp.platform.internal-jwt.allow-test-keys"))).isEqualTo("true");
        assertThat(String.valueOf(dev.get("aiwcorpo.mcp.platform.internal-jwt.jwks-uri"))).contains("http://127.0.0.1:")
                .contains("/.well-known/mcp-jwks.json");
    }

    @Test
    void outsideDevTheBackendMustBeHttps() {
        assertThatThrownBy(() -> adapter("http://backend.internal:8080", new MockEnvironment()))
                .hasMessageContaining("https");
        assertThatCode(() -> adapter("https://backend.internal", new MockEnvironment())).doesNotThrowAnyException();

        assertThatCode(() -> new GitLabHttp(
                new GitLabBackendProperties("http://gitlab.internal:8080", null, null, null, null, null, null, true),
                new DefaultListableBeanFactory().getBeanProvider(SslBundles.class), new MockEnvironment()))
                .as("explicit opt-in for the dev environment's mocks").doesNotThrowAnyException();

        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThatCode(() -> adapter("http://localhost:18081", dev)).doesNotThrowAnyException();
    }

    @Test
    void devIsReachableOnLoopbackOnly() throws IOException {
        Map<String, Object> dev = (Map<String, Object>) new YamlPropertySourceLoader()
                .load("application-dev.yml", new ClassPathResource("application-dev.yml")).getFirst().getSource();
        assertThat(String.valueOf(dev.get("server.address"))).isEqualTo("127.0.0.1");
    }

    private static GitLabHttp adapter(String baseUrl, MockEnvironment environment) {
        return new GitLabHttp(new GitLabBackendProperties(baseUrl, null, null, null, null, null, null, false),
                new DefaultListableBeanFactory().getBeanProvider(SslBundles.class), environment);
    }
}
