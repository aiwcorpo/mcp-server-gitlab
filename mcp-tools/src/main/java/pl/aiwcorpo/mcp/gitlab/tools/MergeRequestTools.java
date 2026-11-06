package pl.aiwcorpo.mcp.gitlab.tools;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.gitlab.core.domain.ApprovalResult;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeOutcome;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.in.MergeRequestUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.MergeRequestPort;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.paging.CursorCodec;
import pl.aiwcorpo.mcp.platform.paging.PagedResult;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;

/**
 * Merge requests. Approving and merging are {@code UPDATE} (decision W-8): they change a merge request on behalf of the
 * caller, and what GitLab itself requires for them (a role, passing pipeline, approvals) still applies. Both take the
 * full head commit id the caller reviewed, so a branch that moved after the review is refused by GitLab instead of
 * being approved or merged unseen, and a repeated call cannot do the thing twice.
 */
@Service
public class MergeRequestTools {

    private static final String IID_PARAM = "Number of the merge request within the project (iid)";

    private final GitLabUseCase gitlab;
    private final MergeRequestUseCase mergeRequests;
    private final PageCursors cursors;

    public MergeRequestTools(GitLabUseCase gitlab, MergeRequestUseCase mergeRequests, PageCursors cursors) {
        this.gitlab = gitlab;
        this.mergeRequests = mergeRequests;
        this.cursors = cursors;
    }

    @Tool(name = "list_merge_requests", description = "Lists the merge requests of a project, most recently updated "
            + "first, 20 per page: number, title, state, draft flag, author, source and target branch, update time "
            + "and web URL. Optionally limited by state, author, reviewer, labels (all of them), target branch and "
            + "draft status. Returns nextCursor when more merge requests exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List merge requests")
    public PagedResult<MergeRequestSummary> listMergeRequests(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "opened, closed, merged or all; all states when omitted", required = false)
            @ArgumentLimits(maxLength = 10, pattern = GitLabArguments.MR_STATE_PATTERN) String state,
            @ToolParam(description = "GitLab username of the author", required = false)
            @ArgumentLimits(minLength = 1, maxLength = 255, pattern = GitLabArguments.USERNAME_PATTERN) String author,
            @ToolParam(description = "GitLab username of a reviewer", required = false)
            @ArgumentLimits(minLength = 1, maxLength = 255, pattern = GitLabArguments.USERNAME_PATTERN) String reviewer,
            @ToolParam(description = "Only merge requests that have all of these labels", required = false)
            @ArgumentLimits(maxItems = GitLabArguments.LABELS_MAX_ITEMS, minLength = 1,
                    maxLength = GitLabArguments.LABEL_MAX_LENGTH, pattern = GitLabArguments.LABEL_PATTERN) List<String> labels,
            @ToolParam(description = "Only merge requests into this target branch", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String targetBranch,
            @ToolParam(description = "true for drafts only, false for everything but drafts; both when omitted", required = false) Boolean draft,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        MergeRequestPort.Filter filter = new MergeRequestPort.Filter(GitLabArguments.mergeRequestState(state),
                GitLabArguments.username(author), GitLabArguments.username(reviewer), GitLabArguments.labels(labels),
                GitLabArguments.ref(targetBranch), draft);
        String fingerprint = CursorCodec.fingerprint("list_merge_requests", resolved.pathWithNamespace(), filter.state(),
                filter.author(), filter.reviewer(), filter.labels() == null ? null : String.join(",", filter.labels()),
                filter.targetBranch(), filter.draft() == null ? null : filter.draft().toString());
        int page = cursors.pageOf(cursor, "list_merge_requests", fingerprint);
        return cursors.result(mergeRequests.list(resolved, filter, page), "list_merge_requests", fingerprint);
    }

    @Tool(name = "get_merge_request", description = "Returns one merge request of a GitLab project by its number "
            + "(iid): title, description, state, draft flag, source and target branch, author, assignees, "
            + "reviewers, labels, timestamps, merge status, conflict flag, the user who merged it, the head commit "
            + "id of the source branch and web URL.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Get merge request")
    public MergeRequest getMergeRequest(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid) {
        return gitlab.getMergeRequest(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.iid(iid));
    }

    @Tool(name = "list_merge_request_diffs", description = "Returns the changes of a merge request, 10 files per page: "
            + "old and new path, whether the file was added, renamed or deleted, and the diff text, cut at 16000 "
            + "characters per file. Returns truncated when a diff was cut and nextCursor when more files exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List merge request diffs")
    public PagedResult<FileDiff> listMergeRequestDiffs(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        long number = GitLabArguments.iid(iid);
        String fingerprint = CursorCodec.fingerprint("list_merge_request_diffs", resolved.pathWithNamespace(), Long.toString(number));
        int page = cursors.pageOf(cursor, "list_merge_request_diffs", fingerprint);
        return cursors.result(mergeRequests.diffs(resolved, number, page), "list_merge_request_diffs", fingerprint,
                diff -> diff.diff().truncated());
    }

    @Tool(name = "list_merge_request_notes", description = "Lists the comments of a merge request in the order they were "
            + "written, 10 per page: id, author, text (cut at 4000 characters), whether it is a system note "
            + "(GitLab's own log line) and creation time. Returns truncated when a text was cut and nextCursor when "
            + "more notes exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List merge request notes")
    public PagedResult<NoteView> listMergeRequestNotes(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        long number = GitLabArguments.iid(iid);
        String fingerprint = CursorCodec.fingerprint("list_merge_request_notes", resolved.pathWithNamespace(), Long.toString(number));
        int page = cursors.pageOf(cursor, "list_merge_request_notes", fingerprint);
        return cursors.result(mergeRequests.notes(resolved, number, page), "list_merge_request_notes", fingerprint,
                note -> note.body() != null && note.body().truncated());
    }

    @Tool(name = "comment_merge_request", description = "Adds a comment to a merge request of a GitLab project as "
            + "the calling user. Lines starting with '/' (GitLab quick actions) are refused. A repeated call adds the "
            + "comment again. Returns the project, the merge request number, the new comment's id, its author and "
            + "creation time.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Comment on merge request")
    public MergeRequestNote commentMergeRequest(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = "Text of the comment, Markdown")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.BODY_MAX_LENGTH, mentionFiltered = true) String body) {
        return gitlab.commentMergeRequest(ResolvedResources.project(GitLabArguments.project(project)),
                GitLabArguments.iid(iid), GitLabArguments.commentBody(body));
    }

    @Tool(name = "create_merge_request", description = "Opens a merge request in a GitLab project as the calling user, "
            + "from an existing source branch into an existing target branch. GitLab refuses a second open merge "
            + "request for the same branches, so a repeated call changes nothing. Lines of the description starting "
            + "with '/' (quick actions) are refused. Returns the project, number, title, state, draft flag and web URL.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Create merge request")
    public MergeRequestChange createMergeRequest(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Branch with the changes")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String sourceBranch,
            @ToolParam(description = "Branch the changes go into")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String targetBranch,
            @ToolParam(description = "Title, one line; start it with 'Draft: ' to open a draft")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.TITLE_MAX_LENGTH, mentionFiltered = true) String title,
            @ToolParam(description = "Description, Markdown", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.DESCRIPTION_MAX_LENGTH, mentionFiltered = true) String description) {
        return mergeRequests.create(ResolvedResources.project(GitLabArguments.project(project)),
                GitLabArguments.ref(sourceBranch), GitLabArguments.ref(targetBranch), GitLabArguments.title(title),
                GitLabArguments.description(description));
    }

    @Tool(name = "update_merge_request", description = "Changes a merge request of a GitLab project as the calling user: "
            + "title, description, labels (the given list replaces the labels; an empty list removes them all) and "
            + "target branch. Only the given fields change and each is set to the given value, so a repeated call "
            + "changes nothing more. Lines of the description starting with '/' (quick actions) are refused. "
            + "Returns the project, number, title, state, draft flag and web URL.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Update merge request")
    public MergeRequestChange updateMergeRequest(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = "New title, one line", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.TITLE_MAX_LENGTH, mentionFiltered = true) String title,
            @ToolParam(description = "New description, Markdown", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.DESCRIPTION_MAX_LENGTH, mentionFiltered = true) String description,
            @ToolParam(description = "The complete new set of labels", required = false)
            @ArgumentLimits(maxItems = GitLabArguments.LABELS_MAX_ITEMS, minLength = 1,
                    maxLength = GitLabArguments.LABEL_MAX_LENGTH, pattern = GitLabArguments.LABEL_PATTERN) List<String> labels,
            @ToolParam(description = "New target branch", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String targetBranch) {
        if (title == null && description == null && labels == null && targetBranch == null) {
            throw new pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException(
                    "at least one of title, description, labels and targetBranch must be given");
        }
        return mergeRequests.update(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.iid(iid),
                title == null ? null : GitLabArguments.title(title), GitLabArguments.description(description),
                GitLabArguments.labels(labels), GitLabArguments.ref(targetBranch));
    }

    @Tool(name = "approve_merge_request", description = "Approves a merge request of a GitLab project as the calling "
            + "user, for the head commit the caller names: GitLab refuses the approval when the source branch has "
            + "moved on. A repeated call by the same user reports the existing approval of that commit. Returns the project, number, the "
            + "commit approved and the number of approvals GitLab reports.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Approve merge request")
    public ApprovalResult approveMergeRequest(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = "Full 40 character id of the head commit of the source branch that was reviewed "
                    + "(headSha of get_merge_request)")
            @ArgumentLimits(minLength = 40, maxLength = 40, pattern = GitLabArguments.HEAD_SHA_PATTERN) String expectedHeadSha) {
        return mergeRequests.approve(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.iid(iid),
                GitLabArguments.headSha(expectedHeadSha));
    }

    @Tool(name = "merge_merge_request", description = "Merges a merge request of a GitLab project as the calling user, "
            + "for the head commit the caller names: GitLab refuses the merge when the source branch has moved on, "
            + "when the merge request is a draft or has unresolved blockers, or when the caller may not merge. "
            + "The source branch is never deleted by this call. A merge request that is already merged at that "
            + "commit is reported as alreadyMerged, so a repeated call changes nothing. Returns the project, number, state, merge commit id and merge time.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Merge a merge request")
    public MergeOutcome mergeMergeRequest(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(resourceType = ResolvedResources.PROJECT, minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = "Full 40 character id of the head commit of the source branch that was reviewed "
                    + "(headSha of get_merge_request)")
            @ArgumentLimits(minLength = 40, maxLength = 40, pattern = GitLabArguments.HEAD_SHA_PATTERN) String expectedHeadSha,
            @ToolParam(description = "Squash the commits into one; the merge request's setting when omitted", required = false) Boolean squash) {
        return mergeRequests.merge(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.iid(iid),
                GitLabArguments.headSha(expectedHeadSha), squash);
    }
}
