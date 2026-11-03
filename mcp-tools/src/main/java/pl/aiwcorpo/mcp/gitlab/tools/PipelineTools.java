package pl.aiwcorpo.mcp.gitlab.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.JobLog;
import pl.aiwcorpo.mcp.gitlab.core.domain.Pipeline;
import pl.aiwcorpo.mcp.gitlab.core.domain.PipelineJob;
import pl.aiwcorpo.mcp.gitlab.core.port.in.PipelineUseCase;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.paging.CursorCodec;
import pl.aiwcorpo.mcp.platform.paging.PagedResult;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;

/**
 * CI pipelines. The two actions only act on a pipeline that exists: they retry its failed jobs and cancel its running
 * ones. Starting a pipeline, running a manual job and passing CI variables are not offered, because variables make a
 * pipeline run arbitrary input as the project's runners.
 */
@Service
public class PipelineTools {

    static final int LOG_LINES_DEFAULT = 200;
    static final int LOG_LINES_MAX = 2_000;

    private static final String PIPELINE_PARAM = "Id of the pipeline";

    private final PipelineUseCase pipelines;
    private final PageCursors cursors;

    public PipelineTools(PipelineUseCase pipelines, PageCursors cursors) {
        this.pipelines = pipelines;
        this.cursors = cursors;
    }

    @Tool(name = "list_pipelines", description = "Lists the CI pipelines of a project, newest first, 30 per page: id, "
            + "status, source, ref, commit, the user who started it, times, duration in seconds and web URL. "
            + "Optionally limited to a branch or tag and to a status. Returns nextCursor when more pipelines exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Lista pipeline'ów")
    public PagedResult<Pipeline> listPipelines(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Branch or tag the pipelines ran for", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String ref,
            @ToolParam(description = "Pipeline status, e.g. success, failed, running, pending, canceled", required = false)
            @ArgumentLimits(maxLength = 30, pattern = GitLabArguments.PIPELINE_STATUS_PATTERN) String status,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        String checkedRef = GitLabArguments.ref(ref);
        String checkedStatus = GitLabArguments.pipelineStatus(status);
        String fingerprint = CursorCodec.fingerprint("list_pipelines", resolved.pathWithNamespace(), checkedRef, checkedStatus);
        int page = cursors.pageOf(cursor, "list_pipelines", fingerprint);
        return cursors.result(pipelines.list(resolved, checkedRef, checkedStatus, page), "list_pipelines", fingerprint);
    }

    @Tool(name = "get_pipeline", description = "Returns one CI pipeline of a project: id, status, source, ref, commit, the "
            + "user who started it, times, duration in seconds and web URL.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Pobierz pipeline")
    public Pipeline getPipeline(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = PIPELINE_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.ID_MAX) Long pipelineId) {
        return pipelines.get(ResolvedResources.project(GitLabArguments.project(project)),
                GitLabArguments.id(pipelineId, "pipelineId"));
    }

    @Tool(name = "list_pipeline_jobs", description = "Lists the jobs of a pipeline, 100 per page: id, name, stage, status, "
            + "whether a failure is allowed, times, duration in seconds and web URL. Returns nextCursor when more "
            + "jobs exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Zadania pipeline'u")
    public PagedResult<PipelineJob> listPipelineJobs(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = PIPELINE_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.ID_MAX) Long pipelineId,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        long id = GitLabArguments.id(pipelineId, "pipelineId");
        String fingerprint = CursorCodec.fingerprint("list_pipeline_jobs", resolved.pathWithNamespace(), Long.toString(id));
        int page = cursors.pageOf(cursor, "list_pipeline_jobs", fingerprint);
        return cursors.result(pipelines.jobs(resolved, id, page), "list_pipeline_jobs", fingerprint);
    }

    @Tool(name = "get_job_log", description = "Returns the end of the log of a CI job: the last lines that fit 60000 "
            + "characters, with terminal escape codes removed and strings that look like credentials (GitLab tokens, "
            + "bearer tokens, passwords in URLs, private keys) replaced by [redacted]; redactions is their count. "
            + "The text is marked truncated when earlier lines were left out. A log over the size the server reads is "
            + "refused.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Log zadania CI")
    public JobLog getJobLog(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Id of the job")
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.ID_MAX) Long jobId,
            @ToolParam(description = "How many of the last lines to return, at most 2000; 200 when omitted", required = false)
            @ArgumentLimits(minimum = 1, maximum = LOG_LINES_MAX) Long lines) {
        long count = lines == null ? LOG_LINES_DEFAULT : GitLabArguments.bounded(lines, 1, LOG_LINES_MAX, "lines");
        return pipelines.log(ResolvedResources.project(GitLabArguments.project(project)),
                GitLabArguments.id(jobId, "jobId"), (int) count);
    }

    @Tool(name = "retry_pipeline", description = "Retries the failed and canceled jobs of a CI pipeline as the calling "
            + "user. Other jobs are left alone, so a repeated call does not start the pipeline over. Returns the "
            + "pipeline: id, status, ref, commit and web URL.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Ponów nieudane zadania pipeline'u")
    public Pipeline retryPipeline(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = PIPELINE_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.ID_MAX) Long pipelineId) {
        return pipelines.retry(ResolvedResources.project(GitLabArguments.project(project)),
                GitLabArguments.id(pipelineId, "pipelineId"));
    }

    @Tool(name = "cancel_pipeline", description = "Cancels the running jobs of a CI pipeline as the calling user. A "
            + "pipeline that already finished stays as it is, so a repeated call changes nothing. Returns the "
            + "pipeline: id, status, ref, commit and web URL.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Anuluj pipeline")
    public Pipeline cancelPipeline(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = PIPELINE_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.ID_MAX) Long pipelineId) {
        return pipelines.cancel(ResolvedResources.project(GitLabArguments.project(project)),
                GitLabArguments.id(pipelineId, "pipelineId"));
    }
}
