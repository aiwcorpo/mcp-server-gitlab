package pl.aiwcorpo.mcp.gitlab.integration;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.Pipeline;
import pl.aiwcorpo.mcp.gitlab.core.domain.PipelineJob;
import pl.aiwcorpo.mcp.gitlab.core.port.out.PipelinePort;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.JobResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.PipelineResponse;

/** CI pipelines and jobs. */
@Component
public class PipelineRestAdapter implements PipelinePort {

    static final int LIST_PAGE = 30;
    static final int JOB_PAGE = 100;

    private static final ParameterizedTypeReference<List<PipelineResponse>> PIPELINES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<JobResponse>> JOBS = new ParameterizedTypeReference<>() {
    };

    private final GitLabHttp http;

    public PipelineRestAdapter(GitLabHttp http) {
        this.http = http;
    }

    @Override
    public Optional<BackendPage<Pipeline>> list(long projectId, @Nullable String ref, @Nullable String status, int page) {
        return http.getPage(PIPELINES, new GitLabQuery("/projects/{id}/pipelines").with("id", projectId)
                        .param("ref", ref).param("status", status)
                        .fixed("order_by", "id").fixed("sort", "desc").page(page, LIST_PAGE))
                .map(result -> new BackendPage<>(result.items().stream().filter(pipeline -> pipeline.belongsTo(projectId))
                        .map(PipelineResponse::toDomain).toList(), result.nextPage()));
    }

    @Override
    public Optional<Pipeline> find(long projectId, long pipelineId) {
        return http.get(PipelineResponse.class, new GitLabQuery("/projects/{id}/pipelines/{pid}")
                        .with("id", projectId).with("pid", pipelineId))
                .filter(pipeline -> pipeline.belongsTo(projectId)).map(PipelineResponse::toDomain);
    }

    @Override
    public Optional<BackendPage<PipelineJob>> jobs(long projectId, long pipelineId, int page) {
        return http.getPage(JOBS, new GitLabQuery("/projects/{id}/pipelines/{pid}/jobs").with("id", projectId)
                        .with("pid", pipelineId).page(page, JOB_PAGE))
                .map(result -> new BackendPage<>(result.items().stream().filter(job -> job.belongsTo(projectId))
                        .map(JobResponse::toDomain).toList(), result.nextPage()));
    }

    @Override
    public Optional<String> jobLog(long projectId, long jobId) {
        // The log is addressed under the project, and the job is first confirmed to belong to it: a log is read only for
        // a job whose own record names a pipeline of this project.
        Optional<JobResponse> job = http.get(JobResponse.class,
                new GitLabQuery("/projects/{id}/jobs/{jid}").with("id", projectId).with("jid", jobId));
        if (job.isEmpty() || !job.get().belongsTo(projectId)) {
            return Optional.empty();
        }
        return http.getLog(new GitLabQuery("/projects/{id}/jobs/{jid}/trace").with("id", projectId).with("jid", jobId))
                .map(PipelineRestAdapter::text);
    }

    @Override
    public Optional<Pipeline> retry(long projectId, long pipelineId) {
        return http.send(HttpMethod.POST, PipelineResponse.class, null,
                        new GitLabQuery("/projects/{id}/pipelines/{pid}/retry").with("id", projectId).with("pid", pipelineId),
                        false)
                .map(GitLabHttp::bodyOf).map(pipeline -> GitLabHttp.confirmed(pipeline, pipeline.belongsTo(projectId)))
                .map(PipelineResponse::toDomain);
    }

    @Override
    public Optional<Pipeline> cancel(long projectId, long pipelineId) {
        return http.send(HttpMethod.POST, PipelineResponse.class, null,
                        new GitLabQuery("/projects/{id}/pipelines/{pid}/cancel").with("id", projectId).with("pid", pipelineId),
                        false)
                .map(GitLabHttp::bodyOf).map(pipeline -> GitLabHttp.confirmed(pipeline, pipeline.belongsTo(projectId)))
                .map(PipelineResponse::toDomain);
    }

    /** A log is text, but a runner can write any bytes into it; those become the replacement character. */
    private static String text(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
