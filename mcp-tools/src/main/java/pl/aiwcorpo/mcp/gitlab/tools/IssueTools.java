package pl.aiwcorpo.mcp.gitlab.tools;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.Issue;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.port.in.IssueUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.IssuePort;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.paging.CursorCodec;
import pl.aiwcorpo.mcp.platform.paging.PagedResult;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;

/** Issues of a project. */
@Service
public class IssueTools {

    private static final String IID_PARAM = "Number of the issue within the project (iid)";

    private final IssueUseCase issues;
    private final PageCursors cursors;

    public IssueTools(IssueUseCase issues, PageCursors cursors) {
        this.issues = issues;
        this.cursors = cursors;
    }

    @Tool(name = "list_issues", description = "Lists the issues of a project, most recently updated first, 20 per page: "
            + "number, title, state, author, update time and web URL. Optionally limited by state, author, "
            + "assignee, labels (all of them) and a word in the title or description. Returns nextCursor when more "
            + "issues exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List issues")
    public PagedResult<IssueSummary> listIssues(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "opened, closed or all; all states when omitted", required = false)
            @ArgumentLimits(maxLength = 10, pattern = GitLabArguments.ISSUE_STATE_PATTERN) String state,
            @ToolParam(description = "GitLab username of the author", required = false)
            @ArgumentLimits(minLength = 1, maxLength = 255, pattern = GitLabArguments.USERNAME_PATTERN) String author,
            @ToolParam(description = "GitLab username of an assignee", required = false)
            @ArgumentLimits(minLength = 1, maxLength = 255, pattern = GitLabArguments.USERNAME_PATTERN) String assignee,
            @ToolParam(description = "Only issues that have all of these labels", required = false)
            @ArgumentLimits(maxItems = GitLabArguments.LABELS_MAX_ITEMS, minLength = 1,
                    maxLength = GitLabArguments.LABEL_MAX_LENGTH, pattern = GitLabArguments.LABEL_PATTERN) List<String> labels,
            @ToolParam(description = "Word or phrase to look for in the title and description, plain text", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.SEARCH_MAX_LENGTH) String search,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        IssuePort.Filter filter = new IssuePort.Filter(GitLabArguments.issueState(state), GitLabArguments.username(author),
                GitLabArguments.username(assignee), GitLabArguments.labels(labels),
                search == null ? null : GitLabArguments.searchTerm(search));
        String fingerprint = CursorCodec.fingerprint("list_issues", resolved.pathWithNamespace(), filter.state(),
                filter.author(), filter.assignee(), filter.labels() == null ? null : String.join(",", filter.labels()),
                filter.search());
        int page = cursors.pageOf(cursor, "list_issues", fingerprint);
        return cursors.result(issues.list(resolved, filter, page), "list_issues", fingerprint);
    }

    @Tool(name = "get_issue", description = "Returns one issue of a GitLab project by its number (iid): title, "
            + "description, state, type, author, assignees, labels, timestamps and web URL.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Get issue")
    public Issue getIssue(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid) {
        return issues.get(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.iid(iid));
    }

    @Tool(name = "list_issue_notes", description = "Lists the comments of an issue in the order they were written, 10 per "
            + "page: id, author, text (cut at 4000 characters), whether it is a system note (GitLab's own log line) "
            + "and creation time. Returns truncated when a text was cut and nextCursor when more notes exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List issue notes")
    public PagedResult<NoteView> listIssueNotes(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        long number = GitLabArguments.iid(iid);
        String fingerprint = CursorCodec.fingerprint("list_issue_notes", resolved.pathWithNamespace(), Long.toString(number));
        int page = cursors.pageOf(cursor, "list_issue_notes", fingerprint);
        return cursors.result(issues.notes(resolved, number, page), "list_issue_notes", fingerprint,
                note -> note.body() != null && note.body().truncated());
    }

    @Tool(name = "create_issue", description = "Creates an issue in a GitLab project as the calling user. A repeated "
            + "call creates another issue. Lines of the description starting with '/' (quick actions) are refused. "
            + "Returns the project, number, title, state and web URL.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Create issue")
    public IssueChange createIssue(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Title, one line")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.TITLE_MAX_LENGTH, mentionFiltered = true) String title,
            @ToolParam(description = "Description, Markdown", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.DESCRIPTION_MAX_LENGTH, mentionFiltered = true) String description,
            @ToolParam(description = "Labels of the new issue", required = false)
            @ArgumentLimits(maxItems = GitLabArguments.LABELS_MAX_ITEMS, minLength = 1,
                    maxLength = GitLabArguments.LABEL_MAX_LENGTH, pattern = GitLabArguments.LABEL_PATTERN) List<String> labels) {
        return issues.create(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.title(title),
                GitLabArguments.description(description), GitLabArguments.labels(labels));
    }

    @Tool(name = "update_issue", description = "Changes an issue of a GitLab project as the calling user: title, "
            + "description, labels (the given list replaces the labels; an empty list removes them all) and state "
            + "(opened or closed). Only the given fields change and each is set to the given value, so a repeated "
            + "call changes nothing more. Lines of the description starting with '/' (quick actions) are refused. "
            + "Returns the project, number, title, state and web URL.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Update issue")
    public IssueChange updateIssue(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
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
            @ToolParam(description = "opened to reopen the issue, closed to close it", required = false)
            @ArgumentLimits(maxLength = 10, pattern = GitLabArguments.ISSUE_NEW_STATE_PATTERN) String state) {
        if (title == null && description == null && labels == null && state == null) {
            throw new InvalidToolArgumentException("at least one of title, description, labels and state must be given");
        }
        return issues.update(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.iid(iid),
                title == null ? null : GitLabArguments.title(title), GitLabArguments.description(description),
                GitLabArguments.labels(labels), GitLabArguments.newIssueState(state));
    }

    @Tool(name = "comment_issue", description = "Adds a comment to an issue of a GitLab project as the calling user. "
            + "Lines starting with '/' (GitLab quick actions) are refused. A repeated call adds the comment again. "
            + "Returns the project, the issue number, the new comment's id, its author and creation time.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Comment on issue")
    public IssueNote commentIssue(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = IID_PARAM)
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = "Text of the comment, Markdown")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.BODY_MAX_LENGTH, mentionFiltered = true) String body) {
        return issues.comment(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.iid(iid),
                GitLabArguments.commentBody(body));
    }
}
