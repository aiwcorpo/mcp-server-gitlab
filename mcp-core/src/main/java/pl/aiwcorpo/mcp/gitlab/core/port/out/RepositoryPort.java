package pl.aiwcorpo.mcp.gitlab.core.port.out;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.Branch;
import pl.aiwcorpo.mcp.gitlab.core.domain.CodeHit;
import pl.aiwcorpo.mcp.gitlab.core.domain.Commit;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectDetails;
import pl.aiwcorpo.mcp.gitlab.core.domain.TreeEntry;

/**
 * Reading a repository as the calling user. Projects are addressed by numeric id; empty means GitLab did not show the
 * thing to the caller. Pages are numbered from 1, a page that does not exist is empty.
 */
public interface RepositoryPort {

    Optional<ProjectDetails> projectDetails(long projectId);

    /** One level of a directory; {@code path} and {@code ref} may be {@code null} for the root and the default branch. */
    Optional<BackendPage<TreeEntry>> tree(long projectId, @Nullable String path, @Nullable String ref, int page);

    Optional<BackendPage<CodeHit>> searchCode(long projectId, String term, @Nullable String ref, int page);

    Optional<BackendPage<Branch>> branches(long projectId, @Nullable String search, int page);

    Optional<BackendPage<Commit>> commits(long projectId, @Nullable String ref, @Nullable String path,
            @Nullable String since, @Nullable String until, int page);

    Optional<Commit> commit(long projectId, String sha);

    Optional<BackendPage<FileDiff>> commitDiffs(long projectId, String sha, int page);
}
