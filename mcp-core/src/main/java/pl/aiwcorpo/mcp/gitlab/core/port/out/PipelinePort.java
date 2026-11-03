package pl.aiwcorpo.mcp.gitlab.core.port.out;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.Pipeline;
import pl.aiwcorpo.mcp.gitlab.core.domain.PipelineJob;

/** CI pipelines and jobs of a project as the calling user; empty means GitLab did not show the thing to the caller. */
public interface PipelinePort {

    Optional<BackendPage<Pipeline>> list(long projectId, @Nullable String ref, @Nullable String status, int page);

    Optional<Pipeline> find(long projectId, long pipelineId);

    Optional<BackendPage<PipelineJob>> jobs(long projectId, long pipelineId, int page);

    /**
     * The raw log of a job. GitLab cannot return only its end, so the adapter reads all of it up to its configured
     * limit and throws {@link pl.aiwcorpo.mcp.gitlab.core.domain.BackendRefusedException} with {@code LOG_TOO_LARGE}
     * beyond it.
     */
    Optional<String> jobLog(long projectId, long jobId);

    Optional<Pipeline> retry(long projectId, long pipelineId);

    Optional<Pipeline> cancel(long projectId, long pipelineId);
}
