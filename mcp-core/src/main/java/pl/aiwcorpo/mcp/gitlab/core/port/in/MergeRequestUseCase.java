package pl.aiwcorpo.mcp.gitlab.core.port.in;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.ApprovalResult;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeOutcome;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.port.out.MergeRequestPort;

/** Merge requests of a resolved project (one merge request is read by {@link GitLabUseCase}). */
public interface MergeRequestUseCase {

    BackendPage<MergeRequestSummary> list(GitLabProject project, MergeRequestPort.Filter filter, int page);

    BackendPage<FileDiff> diffs(GitLabProject project, long iid, int page);

    BackendPage<NoteView> notes(GitLabProject project, long iid, int page);

    MergeRequestChange create(GitLabProject project, String sourceBranch, String targetBranch, String title,
            @Nullable String description);

    MergeRequestChange update(GitLabProject project, long iid, @Nullable String title, @Nullable String description,
            @Nullable List<String> labels, @Nullable String targetBranch);

    ApprovalResult approve(GitLabProject project, long iid, String expectedHeadSha);

    MergeOutcome merge(GitLabProject project, long iid, String expectedHeadSha, @Nullable Boolean squash);
}
