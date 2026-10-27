package pl.aiwcorpo.mcp.gitlab.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectList;
import pl.aiwcorpo.mcp.gitlab.core.domain.RepositoryFile;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;

/**
 * The MCP tools of the GitLab server. Every argument is untrusted input from a model: it is checked here,
 * and the group or project it names is the only one the call touches. There is deliberately no search
 * across GitLab; a listing is always scoped to one group named by the caller.
 */
@Service
public class GitLabTools {

    private final GitLabUseCase gitlab;

    public GitLabTools(GitLabUseCase gitlab) {
        this.gitlab = gitlab;
    }

    @Tool(name = "list_projects", description = "Lists the projects directly in one GitLab group (not in its "
            + "subgroups, not shared into it), ordered by path, at most 100. Returns the group's full path, for each "
            + "project its path, name, description, default branch, archived flag, last activity and web URL, and "
            + "whether more projects exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public ProjectList listProjects(
            @ToolParam(description = "Full path of the group, e.g. team-a or team-a/sub")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.PATH_MAX_LENGTH) String group) {
        return gitlab.listProjects(GitLabArguments.group(group));
    }

    @Tool(name = "get_file", description = "Returns one UTF-8 text file from a GitLab repository at a branch, tag "
            + "or commit: project path, file path, ref, commit id, size in bytes and the content. Binary files and "
            + "files over the size limit are refused without content.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public RepositoryFile getFile(
            @ToolParam(description = "Full path of the project, e.g. team-a/app")
            @ArgumentLimits(minLength = 3, maxLength = GitLabArguments.PATH_MAX_LENGTH) String project,
            @ToolParam(description = "Path of the file relative to the repository root, e.g. src/main.py")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.PATH_MAX_LENGTH) String path,
            @ToolParam(description = "Branch, tag or commit SHA; the project's default branch when omitted",
                    required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String ref) {
        return gitlab.getFile(GitLabArguments.project(project), GitLabArguments.filePath(path), GitLabArguments.ref(ref));
    }

    @Tool(name = "get_merge_request", description = "Returns one merge request of a GitLab project by its number "
            + "(iid): title, description, state, draft flag, source and target branch, author, assignees, "
            + "reviewers, labels, timestamps, merge status, conflict flag and web URL.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public MergeRequest getMergeRequest(
            @ToolParam(description = "Full path of the project, e.g. team-a/app")
            @ArgumentLimits(minLength = 3, maxLength = GitLabArguments.PATH_MAX_LENGTH) String project,
            @ToolParam(description = "Number of the merge request within the project (iid)")
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid) {
        return gitlab.getMergeRequest(GitLabArguments.project(project), GitLabArguments.iid(iid));
    }

    @Tool(name = "comment_merge_request", description = "Adds a comment to a merge request of a GitLab project as "
            + "the calling user. Lines starting with '/' (GitLab quick actions) are refused. Returns the project, "
            + "the merge request number, the new comment's id, its author and creation time.")
    @ToolRisk(RiskLevel.UPDATE)
    public MergeRequestNote commentMergeRequest(
            @ToolParam(description = "Full path of the project, e.g. team-a/app")
            @ArgumentLimits(minLength = 3, maxLength = GitLabArguments.PATH_MAX_LENGTH) String project,
            @ToolParam(description = "Number of the merge request within the project (iid)")
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = "Text of the comment, Markdown")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.BODY_MAX_LENGTH) String body) {
        return gitlab.commentMergeRequest(GitLabArguments.project(project), GitLabArguments.iid(iid),
                GitLabArguments.commentBody(body));
    }
}
