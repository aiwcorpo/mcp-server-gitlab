package pl.aiwcorpo.mcp.gitlab.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.gitlab.application.service.GitlabService;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitlabUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitlabResourcePort;

/** Assembles the hexagon: framework-free use cases from mcp-application, adapters from mcp-integration. */
@Configuration
public class ApplicationWiringConfiguration {

    @Bean
    GitlabUseCase gitlabUseCase(GitlabResourcePort resource) {
        return new GitlabService(resource);
    }
}
