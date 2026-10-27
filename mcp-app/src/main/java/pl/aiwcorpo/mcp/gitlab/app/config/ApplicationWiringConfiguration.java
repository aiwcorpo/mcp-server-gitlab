package pl.aiwcorpo.mcp.gitlab.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.gitlab.application.service.GitLabService;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.gitlab.core.port.out.ProjectResultFilter;

/** Assembles the hexagon: framework-free use cases from mcp-application, adapters from mcp-integration. */
@Configuration
public class ApplicationWiringConfiguration {

    @Bean
    GitLabUseCase gitLabUseCase(GitLabPort gitlab, ProjectResultFilter resultFilter,
            @Value("${gitlab.files.max-bytes:262144}") long maxFileBytes) {
        return new GitLabService(gitlab, resultFilter, maxFileBytes);
    }

    /**
     * Starter 3.0.0-alpha carries no resource policy, so the gateway's per-call decision and GitLab's own
     * permissions are the whole check for a listing. Replaced by the starter beta's ResultFilter (M3).
     */
    @Bean
    ProjectResultFilter projectResultFilter() {
        return projects -> projects;
    }
}
