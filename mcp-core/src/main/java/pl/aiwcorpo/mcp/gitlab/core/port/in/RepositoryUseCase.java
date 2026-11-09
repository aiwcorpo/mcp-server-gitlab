package pl.aiwcorpo.mcp.gitlab.core.port.in;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.Branch;
import pl.aiwcorpo.mcp.gitlab.core.domain.CodeHit;
import pl.aiwcorpo.mcp.gitlab.core.domain.Commit;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.Label;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectDetails;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectMember;
import pl.aiwcorpo.mcp.gitlab.core.domain.Release;
import pl.aiwcorpo.mcp.gitlab.core.domain.Tag;
import pl.aiwcorpo.mcp.gitlab.core.domain.TreeEntry;

/** Reading a repository of a resolved project. A thing GitLab does not show ends in {@code ResourceNotFoundException}. */
public interface RepositoryUseCase {

    ProjectDetails getProject(GitLabProject project);

    BackendPage<TreeEntry> tree(GitLabProject project, @Nullable String path, @Nullable String ref, int page);

    BackendPage<CodeHit> searchCode(GitLabProject project, String term, @Nullable String ref, int page);

    BackendPage<Branch> branches(GitLabProject project, @Nullable String search, int page);

    BackendPage<Tag> tags(GitLabProject project, @Nullable String search, int page);

    BackendPage<Release> releases(GitLabProject project, int page);

    /** Members of the project including those inherited from its groups. */
    BackendPage<ProjectMember> members(GitLabProject project, int page);

    BackendPage<Label> labels(GitLabProject project, @Nullable String search, int page);

    /** Creates {@code branch} at {@code ref} (a branch, tag or commit); a branch that exists is refused. */
    Branch createBranch(GitLabProject project, String branch, String ref);

    BackendPage<Commit> commits(GitLabProject project, @Nullable String ref, @Nullable String path,
            @Nullable String since, @Nullable String until, int page);

    Commit commit(GitLabProject project, String sha);

    BackendPage<FileDiff> commitDiffs(GitLabProject project, String sha, int page);
}
