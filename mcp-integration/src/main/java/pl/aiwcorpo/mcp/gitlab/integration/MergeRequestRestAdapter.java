package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.gitlab.core.domain.ApprovalResult;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeOutcome;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.port.out.MergeRequestPort;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.ApprovalResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.ApprovalsResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.DiffResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.MergeRequestResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.NoteResponse;

/**
 * Merge requests. The only place the GitLab version changes a request is the draft filter of a listing (see
 * {@link GitLabCapabilities}); everything else is read tolerantly in {@link GitLabResponses}.
 */
@Component
public class MergeRequestRestAdapter implements MergeRequestPort {

    static final int LIST_PAGE = 20;
    static final int NOTE_PAGE = 10;
    static final int DIFF_PAGE = 10;

    private static final ParameterizedTypeReference<List<MergeRequestResponse>> MERGE_REQUESTS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<NoteResponse>> NOTES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<DiffResponse>> DIFFS = new ParameterizedTypeReference<>() {
    };

    private final GitLabHttp http;
    private final GitLabCapabilities capabilities;

    public MergeRequestRestAdapter(GitLabHttp http, GitLabCapabilities capabilities) {
        this.http = http;
        this.capabilities = capabilities;
    }

    @Override
    public Optional<BackendPage<MergeRequestSummary>> list(long projectId, Filter filter, int page) {
        GitLabQuery query = new GitLabQuery("/projects/{id}/merge_requests").with("id", projectId)
                .param("state", filter.state()).param("author_username", filter.author())
                .param("reviewer_username", filter.reviewer())
                .param("labels", filter.labels() == null || filter.labels().isEmpty() ? null : String.join(",", filter.labels()))
                .param("target_branch", filter.targetBranch())
                .fixed("order_by", "updated_at").fixed("sort", "desc").page(page, LIST_PAGE);
        boolean filterLocally = false;
        if (filter.draft() != null) {
            String value = filter.draft() ? "yes" : "no";
            if (capabilities.draftFilter()) {
                query.fixed("draft", value);
            } else if (capabilities.wipFilter()) {
                query.fixed("wip", value);
            } else {
                // A version nobody tested: sending a parameter it may have dropped would return unfiltered rows.
                filterLocally = true;
            }
        }
        boolean wantDrafts = Boolean.TRUE.equals(filter.draft());
        boolean local = filterLocally;
        return http.getPage(MERGE_REQUESTS, query).map(result -> new BackendPage<>(result.items().stream()
                .filter(mr -> mr.projectId() == projectId).filter(mr -> !local || mr.isDraft() == wantDrafts)
                .map(MergeRequestResponse::toSummary).toList(), result.nextPage()));
    }

    @Override
    public Optional<BackendPage<FileDiff>> diffs(long projectId, long iid, int page) {
        return http.getPage(DIFFS, new GitLabQuery("/projects/{id}/merge_requests/{iid}/diffs").with("id", projectId)
                        .with("iid", iid).page(page, DIFF_PAGE))
                .map(result -> result.map(DiffResponse::toDomain));
    }

    @Override
    public Optional<BackendPage<NoteView>> notes(long projectId, long iid, int page) {
        return http.getPage(NOTES, new GitLabQuery("/projects/{id}/merge_requests/{iid}/notes").with("id", projectId)
                        .with("iid", iid).fixed("order_by", "created_at").fixed("sort", "asc").page(page, NOTE_PAGE))
                .map(result -> new BackendPage<>(result.items().stream().filter(note -> note.belongsTo(projectId, iid))
                        .map(NoteResponse::toView).toList(), result.nextPage()));
    }

    @Override
    public Optional<MergeRequestChange> create(long projectId, String sourceBranch, String targetBranch, String title,
            @Nullable String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source_branch", sourceBranch);
        body.put("target_branch", targetBranch);
        body.put("title", title);
        if (description != null) {
            body.put("description", description);
        }
        return http.send(HttpMethod.POST, MergeRequestResponse.class, body,
                        new GitLabQuery("/projects/{id}/merge_requests").with("id", projectId), false)
                .map(GitLabHttp::bodyOf).map(mr -> GitLabHttp.confirmed(mr, mr.projectId() == projectId))
                .map(MergeRequestResponse::toChange);
    }

    @Override
    public Optional<MergeRequestChange> update(long projectId, long iid, @Nullable String title,
            @Nullable String description, @Nullable List<String> labels, @Nullable String targetBranch) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (title != null) {
            body.put("title", title);
        }
        if (description != null) {
            body.put("description", description);
        }
        if (labels != null) {
            body.put("labels", String.join(",", labels));
        }
        if (targetBranch != null) {
            body.put("target_branch", targetBranch);
        }
        return http.send(HttpMethod.PUT, MergeRequestResponse.class, body,
                        new GitLabQuery("/projects/{id}/merge_requests/{iid}").with("id", projectId).with("iid", iid), false)
                .map(GitLabHttp::bodyOf).map(mr -> GitLabHttp.confirmed(mr, mr.projectId() == projectId))
                .map(MergeRequestResponse::toChange);
    }

    @Override
    public Optional<ApprovalResult> approve(long projectId, long iid, String expectedHeadSha) {
        Optional<ApprovalResult> approved = http.send(HttpMethod.POST, ApprovalResponse.class, Map.of("sha", expectedHeadSha),
                        new GitLabQuery("/projects/{id}/merge_requests/{iid}/approve").with("id", projectId).with("iid", iid),
                        true)
                .map(response -> new ApprovalResult(null, iid, expectedHeadSha,
                        response.getBody() == null ? null : response.getBody().approvals()));
        return approved.isPresent() ? approved : alreadyApprovedByTheCaller(projectId, iid, expectedHeadSha);
    }

    /**
     * GitLab answers a second approval by the same user with the 401 it uses for "may not approve", which reads as a
     * denial. When the approval did not go through, the merge request is looked at once: if the caller is among the
     * approvers of this very head commit, the state the caller asked for exists and is reported.
     */
    private Optional<ApprovalResult> alreadyApprovedByTheCaller(long projectId, long iid, String expectedHeadSha) {
        Optional<MergeRequestResponse> mergeRequest = http.get(MergeRequestResponse.class,
                new GitLabQuery("/projects/{id}/merge_requests/{iid}").with("id", projectId).with("iid", iid));
        if (mergeRequest.isEmpty() || mergeRequest.get().projectId() != projectId
                || !expectedHeadSha.equals(mergeRequest.get().sha())) {
            return Optional.empty();
        }
        Optional<String> caller = http.get(GitLabResponses.UserResponse.class, new GitLabQuery("/user"))
                .map(GitLabResponses.UserResponse::username);
        Optional<ApprovalsResponse> approvals = http.get(ApprovalsResponse.class,
                new GitLabQuery("/projects/{id}/merge_requests/{iid}/approvals").with("id", projectId).with("iid", iid));
        if (caller.isPresent() && approvals.isPresent() && approvals.get().includes(caller.get())) {
            return Optional.of(new ApprovalResult(null, iid, expectedHeadSha, approvals.get().approvedBy().size()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<MergeOutcome> merge(long projectId, long iid, String expectedHeadSha, @Nullable Boolean squash) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sha", expectedHeadSha);
        if (squash != null) {
            body.put("squash", squash);
        }
        // Explicit false: a merge through this server never deletes a branch. Deleting the source branch of a merge
        // request from a fork would be a write in a project the policy did not judge. (A project that deletes source
        // branches by its own setting is outside the server's control.)
        body.put("should_remove_source_branch", false);
        return http.send(HttpMethod.PUT, MergeRequestResponse.class, body,
                        new GitLabQuery("/projects/{id}/merge_requests/{iid}/merge").with("id", projectId).with("iid", iid),
                        true)
                .map(GitLabHttp::bodyOf)
                .map(merged -> GitLabHttp.confirmed(merged, merged.projectId() == projectId))
                .map(merged -> new MergeOutcome(null, iid, merged.state(), merged.mergeCommitSha(), merged.mergedAt(), false));
    }
}
