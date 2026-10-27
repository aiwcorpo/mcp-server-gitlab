package pl.aiwcorpo.mcp.gitlab.integration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where GitLab is and how long the server waits for it.
 *
 * @param baseUrl        root URL of the GitLab instance, without {@code /api/v4} ({@code MCP_GITLAB_URL}); the
 *                       server does not start without it
 * @param sslBundle      Spring SSL bundle trusted for an HTTPS GitLab (internal CA); empty = JDK default trust
 * @param connectTimeout time to open the connection
 * @param readTimeout    time for the whole response; the gateway gives up on the call soon after anyway
 */
@ConfigurationProperties("gitlab.backend")
public record GitLabBackendProperties(String baseUrl, String sslBundle, Duration connectTimeout, Duration readTimeout) {

    public GitLabBackendProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }
}
