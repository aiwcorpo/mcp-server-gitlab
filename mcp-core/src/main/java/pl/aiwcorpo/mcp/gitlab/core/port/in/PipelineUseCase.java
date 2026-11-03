package pl.aiwcorpo.mcp.gitlab.core.port.in;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.JobLog;
import pl.aiwcorpo.mcp.gitlab.core.domain.Pipeline;
import pl.aiwcorpo.mcp.gitlab.core.domain.PipelineJob;

/** CI pipelines and jobs of a resolved project. */
public interface PipelineUseCase {

    BackendPage<Pipeline> list(GitLabProject project, @Nullable String ref, @Nullable String status, int page);

    Pipeline get(GitLabProject project, long pipelineId);

    BackendPage<PipelineJob> jobs(GitLabProject project, long pipelineId, int page);

    /** The last {@code maxLines} lines of a job log that fit the size limit, with credentials redacted. */
    JobLog log(GitLabProject project, long jobId, int maxLines);

    Pipeline retry(GitLabProject project, long pipelineId);

    Pipeline cancel(GitLabProject project, long pipelineId);
}
