package pl.aiwcorpo.mcp.gitlab.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.gitlab.application.service.GitLabService;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabBackendProperties;

/** Assembles the hexagon: framework-free use cases from mcp-application, adapters from mcp-integration. */
@Configuration
public class ApplicationWiringConfiguration {

    @Bean
    GitLabUseCase gitLabUseCase(GitLabPort gitlab, GitLabBackendProperties backend,
            @Value("${gitlab.files.max-bytes:262144}") long maxFileBytes) {
        if (maxFileBytes > backend.maxResponseSize().toBytes()) {
            // The adapter cuts every answer at max-response-size, so a larger permitted file could never be read.
            throw new IllegalStateException("gitlab.files.max-bytes (MCP_GITLAB_FILE_MAX_BYTES) must not exceed "
                    + "gitlab.backend.max-response-size");
        }
        return new GitLabService(gitlab, maxFileBytes);
    }
}
