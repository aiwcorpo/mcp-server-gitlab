package pl.aiwcorpo.mcp.gitlab.application.service;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.ApprovalResult;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendRefusedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeOutcome;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException;
import pl.aiwcorpo.mcp.gitlab.core.port.in.MergeRequestUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.gitlab.core.port.out.MergeRequestPort;

/** Merge request reads and changes of a resolved project. */
public class MergeRequestService implements MergeRequestUseCase {

    private final MergeRequestPort mergeRequests;
    private final GitLabPort gitlab;

    public MergeRequestService(MergeRequestPort mergeRequests, GitLabPort gitlab) {
        this.mergeRequests = mergeRequests;
        this.gitlab = gitlab;
    }

    @Override
    public BackendPage<MergeRequestSummary> list(GitLabProject project, MergeRequestPort.Filter filter, int page) {
        return mergeRequests.list(project.id(), filter, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<FileDiff> diffs(GitLabProject project, long iid, int page) {
        return mergeRequests.diffs(project.id(), iid, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<NoteView> notes(GitLabProject project, long iid, int page) {
        return mergeRequests.notes(project.id(), iid, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public MergeRequestChange create(GitLabProject project, String sourceBranch, String targetBranch, String title,
            @Nullable String description) {
        return mergeRequests.create(project.id(), sourceBranch, targetBranch, title, description)
                .orElseThrow(ResourceNotFoundException::new)
                .inProject(project.pathWithNamespace());
    }

    @Override
    public MergeRequestChange update(GitLabProject project, long iid, @Nullable String title,
            @Nullable String description, @Nullable List<String> labels, @Nullable String targetBranch) {
        return mergeRequests.update(project.id(), iid, title, description, labels, targetBranch)
                .orElseThrow(ResourceNotFoundException::new)
                .inProject(project.pathWithNamespace());
    }

    @Override
    public ApprovalResult approve(GitLabProject project, long iid, String expectedHeadSha) {
        return mergeRequests.approve(project.id(), iid, expectedHeadSha)
                .orElseThrow(ResourceNotFoundException::new)
                .inProject(project.pathWithNamespace());
    }

    /**
     * A merge that GitLab refuses is looked at once more: a merge request that is already merged at the commit the
     * caller named is the state the caller asked for (the call is delivered at least once, so a repeat must not look
     * like a failure). Any other refusal stays one.
     */
    @Override
    public MergeOutcome merge(GitLabProject project, long iid, String expectedHeadSha, @Nullable Boolean squash,
            @Nullable Boolean removeSourceBranch) {
        try {
            return mergeRequests.merge(project.id(), iid, expectedHeadSha, squash, removeSourceBranch)
                    .orElseThrow(ResourceNotFoundException::new)
                    .inProject(project.pathWithNamespace());
        } catch (BackendRefusedException refused) {
            FoundMergeRequest found = gitlab.findMergeRequest(project.id(), iid).orElseThrow(() -> refused);
            MergeRequest current = found.mergeRequest();
            if (found.projectId() == project.id() && "merged".equals(current.state())
                    && expectedHeadSha.equals(current.headSha())) {
                return new MergeOutcome(project.pathWithNamespace(), iid, current.state(), null, current.mergedAt(), true);
            }
            throw refused;
        }
    }
}
