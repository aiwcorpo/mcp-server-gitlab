package pl.aiwcorpo.mcp.gitlab.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroupResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProjectResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectList;
import pl.aiwcorpo.mcp.gitlab.core.domain.RepositoryFile;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.resource.ResourceContext;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;

/**
 * The MCP tools of the GitLab server. Every argument is untrusted input from a model: it is checked here,
 * and the group or project it names is the only one the call touches. There is deliberately no search
 * across GitLab; a listing is always scoped to one group named by the caller.
 *
 * <p>Group and project arguments are {@link ResourceRef}s: before a method runs the starter has asked GitLab for
 * the resource with the caller's own credential and evaluated the policy on it (its topics and those of every group
 * above it). A resource that is missing, hidden by GitLab or restricted by the policy ends the call with the same
 * {@code not_found_or_denied}, so the method only ever sees a resource the caller may use, and reads it from the
 * resolver's answer instead of asking GitLab again.
 */
@Service
public class GitLabTools {

    static final String GROUP = "GitLab::Group";
    static final String PROJECT = "GitLab::Project";

    private final GitLabUseCase gitlab;

    public GitLabTools(GitLabUseCase gitlab) {
        this.gitlab = gitlab;
    }

    @Tool(name = "list_projects", description = "Lists the projects directly in one GitLab group (not in its "
            + "subgroups, not shared into it), ordered by path, at most 100. Returns the group's full path, for each "
            + "project its path, name, description, default branch, archived flag, last activity and web URL, and "
            + "whether more projects exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Lista projektów grupy")
    public ProjectList listProjects(
            @ToolParam(description = "Full path of the group, e.g. team-a or team-a/sub")
            @ArgumentLimits(contractPattern = "GitLabGroupPath", minLength = 1)
            @ResourceRef(type = GROUP) String group) {
        return gitlab.listProjects(resolvedGroup(GitLabArguments.group(group)));
    }

    @Tool(name = "get_file", description = "Returns one UTF-8 text file from a GitLab repository at a branch, tag "
            + "or commit: project path, file path, ref, commit id, size in bytes and the content. Binary files and "
            + "files over the size limit are refused without content.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Pobierz plik z repozytorium")
    public RepositoryFile getFile(
            @ToolParam(description = "Full path of the project, e.g. team-a/app")
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = PROJECT) String project,
            @ToolParam(description = "Path of the file relative to the repository root, e.g. src/main.py")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.PATH_MAX_LENGTH) String path,
            @ToolParam(description = "Branch, tag or commit SHA; the project's default branch when omitted",
                    required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String ref) {
        return gitlab.getFile(resolvedProject(GitLabArguments.project(project)), GitLabArguments.filePath(path), GitLabArguments.ref(ref));
    }

    @Tool(name = "get_merge_request", description = "Returns one merge request of a GitLab project by its number "
            + "(iid): title, description, state, draft flag, source and target branch, author, assignees, "
            + "reviewers, labels, timestamps, merge status, conflict flag and web URL.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Pobierz merge request")
    public MergeRequest getMergeRequest(
            @ToolParam(description = "Full path of the project, e.g. team-a/app")
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = PROJECT) String project,
            @ToolParam(description = "Number of the merge request within the project (iid)")
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid) {
        return gitlab.getMergeRequest(resolvedProject(GitLabArguments.project(project)), GitLabArguments.iid(iid));
    }

    @Tool(name = "comment_merge_request", description = "Adds a comment to a merge request of a GitLab project as "
            + "the calling user. Lines starting with '/' (GitLab quick actions) are refused. Returns the project, "
            + "the merge request number, the new comment's id, its author and creation time.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Dodaj komentarz do merge requesta")
    public MergeRequestNote commentMergeRequest(
            @ToolParam(description = "Full path of the project, e.g. team-a/app")
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = PROJECT) String project,
            @ToolParam(description = "Number of the merge request within the project (iid)")
            @ArgumentLimits(minimum = 1, maximum = GitLabArguments.IID_MAX) Long iid,
            @ToolParam(description = "Text of the comment, Markdown")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.BODY_MAX_LENGTH, mentionFiltered = true) String body) {
        return gitlab.commentMergeRequest(resolvedProject(GitLabArguments.project(project)), GitLabArguments.iid(iid),
                GitLabArguments.commentBody(body));
    }

    /** The group the resolver fetched for this argument; the policy has accepted exactly this answer. */
    private static GitLabGroup resolvedGroup(String path) {
        return ResourceContext.required().fetched(GROUP, path, GitLabGroupResource.class).orElseThrow().group();
    }

    private static GitLabProject resolvedProject(String path) {
        return ResourceContext.required().fetched(PROJECT, path, GitLabProjectResource.class).orElseThrow().project();
    }
}
