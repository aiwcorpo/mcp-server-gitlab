package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.Branch;
import pl.aiwcorpo.mcp.gitlab.core.domain.CodeHit;
import pl.aiwcorpo.mcp.gitlab.core.domain.Commit;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectDetails;
import pl.aiwcorpo.mcp.gitlab.core.domain.TreeEntry;
import pl.aiwcorpo.mcp.gitlab.core.port.out.RepositoryPort;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.BlobHitResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.BranchResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.CommitResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.DiffResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.ProjectResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.TreeEntryResponse;

/**
 * Repository reads. Page sizes are chosen so that a full page of the largest rows still fits a result of 1 MiB: a diff
 * is cut at 16000 characters, so 10 of them are about 160 KB, a code snippet at 2000, so 20 of them about 40 KB.
 */
@Component
public class RepositoryRestAdapter implements RepositoryPort {

    static final int TREE_PAGE = 100;
    static final int SEARCH_PAGE = 20;
    static final int BRANCH_PAGE = 50;
    static final int COMMIT_PAGE = 50;
    static final int DIFF_PAGE = 10;

    private static final ParameterizedTypeReference<List<TreeEntryResponse>> TREE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<BlobHitResponse>> BLOBS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<BranchResponse>> BRANCHES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<CommitResponse>> COMMITS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<DiffResponse>> DIFFS = new ParameterizedTypeReference<>() {
    };

    private final GitLabHttp http;

    public RepositoryRestAdapter(GitLabHttp http) {
        this.http = http;
    }

    @Override
    public Optional<ProjectDetails> projectDetails(long projectId) {
        return http.get(ProjectResponse.class, new GitLabQuery("/projects/{id}").with("id", projectId))
                .map(ProjectResponse::toDetails);
    }

    @Override
    public Optional<BackendPage<TreeEntry>> tree(long projectId, @Nullable String path, @Nullable String ref, int page) {
        return http.getPage(TREE, new GitLabQuery("/projects/{id}/repository/tree").with("id", projectId)
                        .param("path", path).param("ref", ref).page(page, TREE_PAGE))
                .map(result -> result.map(TreeEntryResponse::toDomain));
    }

    @Override
    public Optional<BackendPage<CodeHit>> searchCode(long projectId, String term, @Nullable String ref, int page) {
        return http.getPage(BLOBS, new GitLabQuery("/projects/{id}/search").with("id", projectId)
                        .fixed("scope", "blobs").param("search", term).param("ref", ref).page(page, SEARCH_PAGE))
                .map(result -> new BackendPage<>(result.items().stream().filter(hit -> hit.belongsTo(projectId))
                        .map(BlobHitResponse::toDomain).toList(), result.nextPage()));
    }

    @Override
    public Optional<BackendPage<Branch>> branches(long projectId, @Nullable String search, int page) {
        return http.getPage(BRANCHES, new GitLabQuery("/projects/{id}/repository/branches").with("id", projectId)
                        .param("search", search).page(page, BRANCH_PAGE))
                .map(result -> result.map(BranchResponse::toDomain));
    }

    @Override
    public Optional<BackendPage<Commit>> commits(long projectId, @Nullable String ref, @Nullable String path,
            @Nullable String since, @Nullable String until, int page) {
        return http.getPage(COMMITS, new GitLabQuery("/projects/{id}/repository/commits").with("id", projectId)
                        .param("ref_name", ref).param("path", path).param("since", since).param("until", until)
                        .page(page, COMMIT_PAGE))
                .map(result -> result.map(CommitResponse::toDomain));
    }

    @Override
    public Optional<Commit> commit(long projectId, String sha) {
        return http.get(CommitResponse.class, new GitLabQuery("/projects/{id}/repository/commits/{sha}")
                        .with("id", projectId).with("sha", sha).fixed("stats", "true"))
                .map(CommitResponse::toDomain);
    }

    @Override
    public Optional<BackendPage<FileDiff>> commitDiffs(long projectId, String sha, int page) {
        return http.getPage(DIFFS, new GitLabQuery("/projects/{id}/repository/commits/{sha}/diff")
                        .with("id", projectId).with("sha", sha).page(page, DIFF_PAGE))
                .map(result -> result.map(DiffResponse::toDomain));
    }
}
