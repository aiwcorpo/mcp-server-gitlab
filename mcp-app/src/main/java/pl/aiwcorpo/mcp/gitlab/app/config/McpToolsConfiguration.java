package pl.aiwcorpo.mcp.gitlab.app.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.gitlab.tools.GitLabTools;
import pl.aiwcorpo.mcp.gitlab.tools.IssueTools;
import pl.aiwcorpo.mcp.gitlab.tools.MergeRequestTools;
import pl.aiwcorpo.mcp.gitlab.tools.PipelineTools;
import pl.aiwcorpo.mcp.gitlab.tools.RepositoryTools;
import pl.aiwcorpo.mcp.platform.input.SchemaLimitedToolCallbacks;

/**
 * The tool set of the server, as an explicit list: the MCP server exposes only tools registered through a
 * {@link ToolCallbackProvider} (an {@code @Tool} bean alone answers "Unknown tool"), and a reviewed list
 * keeps the public surface from growing by accident. The starter adds the argument limits and
 * {@code additionalProperties: false} to each input schema and checks every call against it.
 */
@Configuration
public class McpToolsConfiguration {

    @Bean
    public ToolCallbackProvider mcpTools(GitLabTools gitLabTools, RepositoryTools repositoryTools,
            MergeRequestTools mergeRequestTools, IssueTools issueTools, PipelineTools pipelineTools) {
        return SchemaLimitedToolCallbacks.of(gitLabTools, repositoryTools, mergeRequestTools, issueTools, pipelineTools);
    }
}
