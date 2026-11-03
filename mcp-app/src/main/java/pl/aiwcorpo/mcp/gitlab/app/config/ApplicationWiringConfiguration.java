package pl.aiwcorpo.mcp.gitlab.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.gitlab.application.service.GitLabService;
import pl.aiwcorpo.mcp.gitlab.application.service.IssueService;
import pl.aiwcorpo.mcp.gitlab.application.service.MergeRequestService;
import pl.aiwcorpo.mcp.gitlab.application.service.PipelineService;
import pl.aiwcorpo.mcp.gitlab.application.service.RepositoryService;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.in.IssueUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.in.MergeRequestUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.in.PipelineUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.in.RepositoryUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.gitlab.core.port.out.IssuePort;
import pl.aiwcorpo.mcp.gitlab.core.port.out.MergeRequestPort;
import pl.aiwcorpo.mcp.gitlab.core.port.out.PipelinePort;
import pl.aiwcorpo.mcp.gitlab.core.port.out.RepositoryPort;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabBackendProperties;

/** Assembles the hexagon: framework-free use cases from mcp-application, adapters from mcp-integration. */
@Configuration
public class ApplicationWiringConfiguration {

    /**
     * Largest file the server may be configured to return. The content is JSON-escaped in the result, where a quote or
     * a backslash takes two characters (control characters are dropped by the text hygiene before), and the result may
     * not exceed 1 MiB, so twice this limit plus the other fields stays below it.
     */
    static final long MAX_CONFIGURABLE_FILE_BYTES = 400L * 1024;

    @Bean
    GitLabUseCase gitLabUseCase(GitLabPort gitlab, GitLabBackendProperties backend,
            @Value("${gitlab.files.max-bytes:262144}") long maxFileBytes) {
        if (maxFileBytes > backend.maxResponseSize().toBytes()) {
            // The adapter cuts every answer at max-response-size, so a larger permitted file could never be read.
            throw new IllegalStateException("gitlab.files.max-bytes (MCP_GITLAB_FILE_MAX_BYTES) must not exceed "
                    + "gitlab.backend.max-response-size");
        }
        if (maxFileBytes > MAX_CONFIGURABLE_FILE_BYTES) {
            throw new IllegalStateException("gitlab.files.max-bytes (MCP_GITLAB_FILE_MAX_BYTES) must not exceed "
                    + MAX_CONFIGURABLE_FILE_BYTES + " bytes: the JSON-escaped content must fit the 1 MiB result");
        }
        return new GitLabService(gitlab, maxFileBytes);
    }

    @Bean
    RepositoryUseCase repositoryUseCase(RepositoryPort repository) {
        return new RepositoryService(repository);
    }

    @Bean
    MergeRequestUseCase mergeRequestUseCase(MergeRequestPort mergeRequests, GitLabPort gitlab) {
        return new MergeRequestService(mergeRequests, gitlab);
    }

    @Bean
    IssueUseCase issueUseCase(IssuePort issues) {
        return new IssueService(issues);
    }

    @Bean
    PipelineUseCase pipelineUseCase(PipelinePort pipelines) {
        return new PipelineService(pipelines);
    }
}
