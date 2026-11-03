package pl.aiwcorpo.mcp.gitlab.core.port.out;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.ApprovalResult;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeOutcome;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;

/**
 * Merge requests beyond the single one {@link GitLabPort} reads. Writes are done as the calling user; a refusal that
 * depends on the state of the project is a {@link pl.aiwcorpo.mcp.gitlab.core.domain.BackendRefusedException}, and
 * empty means GitLab did not show the thing to the caller.
 */
public interface MergeRequestPort {

    /** Filters of a listing; every field may be {@code null} for "any". */
    record Filter(@Nullable String state, @Nullable String author, @Nullable String reviewer,
            @Nullable List<String> labels, @Nullable String targetBranch, @Nullable Boolean draft) {
    }

    /** Draft filtering is done by the adapter, on the server or locally, whichever the GitLab version allows. */
    Optional<BackendPage<MergeRequestSummary>> list(long projectId, Filter filter, int page);

    Optional<BackendPage<FileDiff>> diffs(long projectId, long iid, int page);

    Optional<BackendPage<NoteView>> notes(long projectId, long iid, int page);

    Optional<MergeRequestChange> create(long projectId, String sourceBranch, String targetBranch, String title,
            @Nullable String description);

    /** Only the given fields change; {@code labels} replaces the whole set, an empty list clears it. */
    Optional<MergeRequestChange> update(long projectId, long iid, @Nullable String title, @Nullable String description,
            @Nullable List<String> labels, @Nullable String targetBranch);

    Optional<ApprovalResult> approve(long projectId, long iid, String expectedHeadSha);

    /** {@code squash} is sent only when given; GitLab then uses the setting of the merge request. The source branch is never removed. */
    Optional<MergeOutcome> merge(long projectId, long iid, String expectedHeadSha, @Nullable Boolean squash);
}
