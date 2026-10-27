package pl.aiwcorpo.mcp.gitlab.integration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where GitLab is and how long the server waits for it.
 *
 * @param baseUrl         root URL of the GitLab instance, without {@code /api/v4} ({@code MCP_GITLAB_URL});
 *                        the server does not start without it, and outside the dev profile it must be https
 * @param sslBundle       Spring SSL bundle trusted for an HTTPS GitLab (internal CA); empty = JDK default trust
 * @param connectTimeout  time to open the connection
 * @param readTimeout     time for the whole response; the gateway gives up on the call soon after anyway
 * @param maxResponseSize larger bodies are cut off with an error instead of filling the heap; has to leave room
 *                        for {@code gitlab.files.max-bytes}
 * @param allowPlainHttp  {@code http} outside the dev profile, for the mocks of mcp-platform-dev only
 *                        ({@code MCP_GITLAB_ALLOW_PLAIN_HTTP}); the credential then crosses the network in clear text
 */
@ConfigurationProperties("gitlab.backend")
public record GitLabBackendProperties(String baseUrl, String sslBundle, Duration connectTimeout, Duration readTimeout,
        DataSize maxResponseSize, boolean allowPlainHttp) {

    public GitLabBackendProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        maxResponseSize = maxResponseSize == null ? DataSize.ofMegabytes(1) : maxResponseSize;
    }
}
