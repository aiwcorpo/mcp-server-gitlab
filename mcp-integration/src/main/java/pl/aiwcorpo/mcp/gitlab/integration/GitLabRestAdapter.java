package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileMetadata;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.GroupResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.MergeRequestResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.NoteResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.ProjectResponse;

/**
 * GitLab REST API v4 as the user: with the credential the gateway resolved for this request (C3), never with
 * an account of the server. The HTTP client and the mapping of statuses are in {@link GitLabHttp}.
 *
 * <p>Every path segment taken from an argument is percent-encoded as a whole ({@code team-a/app} becomes
 * {@code team-a%2Fapp}, as GitLab expects for {@code :id} and {@code :file_path}), so an argument can never
 * add a path segment or a query parameter.
 */
@Component
public class GitLabRestAdapter implements GitLabPort {

    private static final ParameterizedTypeReference<List<ProjectResponse>> PROJECT_LIST = new ParameterizedTypeReference<>() {
    };

    private final GitLabHttp http;
    private final GitLabCapabilities capabilities;
    private final CallCache callCache = new CallCache();

    public GitLabRestAdapter(GitLabHttp http, GitLabCapabilities capabilities) {
        this.http = http;
        this.capabilities = capabilities;
    }

    /**
     * Groups are not classified (decision of 2026-10-28): only projects carry topics, so a group's own classification is
     * always empty and any {@code topics} GitLab might send for a group are not read.
     *
     * GitLab paths ignore case, so the cache does too: {@code Team-A} and {@code team-a} are one lookup. */
    @Override
    public Optional<GitLabGroup> findGroup(String fullPath) {
        return callCache.get("group:" + fullPath.toLowerCase(Locale.ROOT), () -> http
                .get(GroupResponse.class, new GitLabQuery("/groups/{id}").with("id", fullPath).fixed("with_projects", "false"))
                .map(group -> new GitLabGroup(group.id(), GitLabHttp.required(group.fullPath()), List.of())));
    }

    @Override
    public Optional<GitLabProject> findProject(String pathWithNamespace) {
        capabilities.learn();
        return http.get(ProjectResponse.class, new GitLabQuery("/projects/{id}").with("id", pathWithNamespace))
                .map(ProjectResponse::toDomain);
    }

    @Override
    public ProjectPage listGroupProjects(long groupId, int pageSize, int page) {
        ProjectPage listed = callCache.get("projects:" + groupId + ":" + pageSize + ":" + page, () -> {
            // A group that resolved a moment ago and now answers 404/403 is treated like an empty one: the caller
            // learns nothing beyond what the lookup already told it.
            Optional<BackendPage<ProjectResponse>> response = http.getPage(PROJECT_LIST,
                    new GitLabQuery("/groups/{id}/projects").with("id", groupId)
                            .fixed("per_page", Integer.toString(pageSize)).fixed("page", Integer.toString(page))
                            .fixed("order_by", "path").fixed("sort", "asc")
                            .fixed("include_subgroups", "false").fixed("with_shared", "false"));
            List<ProjectResponse> projects = response.map(BackendPage::items).orElse(List.of());
            return new ProjectPage(
                    projects.stream().map(ProjectResponse::toSummary).toList(),
                    projects.stream().map(ProjectResponse::toDomain).toList(),
                    projects.stream().map(ProjectResponse::namespaceId).toList(),
                    response.map(BackendPage::nextPage).orElse(null));
        });
        callCache.put("listed:" + groupId + ":" + pageSize, listed);
        return listed;
    }

    @Override
    public ProjectPage listedGroupProjects(long groupId, int pageSize) {
        ProjectPage listed = callCache.peek("listed:" + groupId + ":" + pageSize);
        return listed != null ? listed : listGroupProjects(groupId, pageSize, 1);
    }

    @Override
    public Optional<FileMetadata> fileMetadata(long projectId, String filePath, String ref) {
        // HEAD answers with the X-Gitlab-* headers and no content, so a large file is refused before it is read.
        return http.head(new GitLabQuery("/projects/{id}/repository/files/{path}").with("id", projectId)
                        .with("path", filePath).param("ref", ref))
                .filter(headers -> filePath.equals(headers.getFirst("X-Gitlab-File-Path")))
                .map(headers -> new FileMetadata(
                        GitLabHttp.required(headers.getFirst("X-Gitlab-File-Path")),
                        GitLabHttp.required(headers.getFirst("X-Gitlab-Commit-Id")),
                        size(headers)));
    }

    @Override
    public Optional<byte[]> fileContent(long projectId, String filePath, String commitId) {
        return http.getBytes(new GitLabQuery("/projects/{id}/repository/files/{path}/raw").with("id", projectId)
                .with("path", filePath).param("ref", commitId));
    }

    @Override
    public Optional<FoundMergeRequest> findMergeRequest(long projectId, long iid) {
        return http.get(MergeRequestResponse.class, new GitLabQuery("/projects/{id}/merge_requests/{iid}")
                        .with("id", projectId).with("iid", iid))
                .map(MergeRequestResponse::toDomain);
    }

    @Override
    public Optional<MergeRequestNote> addMergeRequestNote(long projectId, long iid, String body) {
        return http.send(HttpMethod.POST, NoteResponse.class, Map.of("body", body),
                        new GitLabQuery("/projects/{id}/merge_requests/{iid}/notes").with("id", projectId).with("iid", iid),
                        false)
                .map(GitLabHttp::bodyOf)
                .map(note -> GitLabHttp.confirmed(note, note.belongsTo(projectId, iid)))
                .map(note -> new MergeRequestNote(null, iid, note.id(),
                        note.author() == null ? null : note.author().username(), note.createdAt()));
    }

    private static long size(HttpHeaders headers) {
        try {
            return Long.parseLong(GitLabHttp.required(headers.getFirst("X-Gitlab-Size")));
        } catch (NumberFormatException notANumber) {
            throw new BackendResponseException("size header is not a number");
        }
    }
}
