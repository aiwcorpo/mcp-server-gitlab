package pl.aiwcorpo.mcp.gitlab.application.service;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.JobLog;
import pl.aiwcorpo.mcp.gitlab.core.domain.Pipeline;
import pl.aiwcorpo.mcp.gitlab.core.domain.PipelineJob;
import pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException;
import pl.aiwcorpo.mcp.gitlab.core.port.in.PipelineUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.PipelinePort;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** CI reads and the two actions on a pipeline, for a resolved project. */
public class PipelineService implements PipelineUseCase {

    /** The characters of a log tail; the JSON-escaped text must stay far below the 1 MiB limit of a result. */
    static final int LOG_MAX_CHARS = 60_000;

    private final PipelinePort pipelines;

    public PipelineService(PipelinePort pipelines) {
        this.pipelines = pipelines;
    }

    @Override
    public BackendPage<Pipeline> list(GitLabProject project, @Nullable String ref, @Nullable String status, int page) {
        return pipelines.list(project.id(), ref, status, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public Pipeline get(GitLabProject project, long pipelineId) {
        return pipelines.find(project.id(), pipelineId).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<PipelineJob> jobs(GitLabProject project, long pipelineId, int page) {
        return pipelines.jobs(project.id(), pipelineId, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public JobLog log(GitLabProject project, long jobId, int maxLines) {
        String raw = pipelines.jobLog(project.id(), jobId).orElseThrow(ResourceNotFoundException::new);
        JobLogText.Cleaned cleaned = JobLogText.clean(raw);
        JobLogText.Tail tail = JobLogText.tail(cleaned.text(), maxLines, LOG_MAX_CHARS);
        return new JobLog(project.pathWithNamespace(), jobId, cleaned.redactions(),
                UntrustedText.plainCut(tail.text(), tail.cut()));
    }

    @Override
    public Pipeline retry(GitLabProject project, long pipelineId) {
        return pipelines.retry(project.id(), pipelineId).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public Pipeline cancel(GitLabProject project, long pipelineId) {
        return pipelines.cancel(project.id(), pipelineId).orElseThrow(ResourceNotFoundException::new);
    }
}
