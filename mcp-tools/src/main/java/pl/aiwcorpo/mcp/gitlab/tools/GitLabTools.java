package pl.aiwcorpo.mcp.gitlab.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.RepositoryFile;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.paging.CursorCodec;
import pl.aiwcorpo.mcp.platform.paging.PagedResult;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;

/**
 * The tools that start from a group, and the one that reads a file. Every argument is untrusted input from a model: it
 * is checked here, and the group or project it names is the only one the call touches. There is deliberately no search
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

    static final String PROJECT_PARAM = "Full path of the project, e.g. team-a/app";
    static final String CURSOR_PARAM = "nextCursor of the previous result, to get the next page; omit for the first page";

    private final GitLabUseCase gitlab;
    private final PageCursors cursors;

    public GitLabTools(GitLabUseCase gitlab, PageCursors cursors) {
        this.gitlab = gitlab;
        this.cursors = cursors;
    }

    @Tool(name = "list_projects", description = "Lists the projects directly in one GitLab group (not in its "
            + "subgroups, not shared into it), ordered by path, 100 per page. Returns for each project its path, name, "
            + "description, default branch, archived flag, last activity and web URL, and nextCursor when more "
            + "projects exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Lista projektów grupy")
    public PagedResult<ProjectSummary> listProjects(
            @ToolParam(description = "Full path of the group, e.g. team-a or team-a/sub")
            @ArgumentLimits(contractPattern = "GitLabGroupPath", minLength = 1)
            @ResourceRef(type = ResolvedResources.GROUP) String group,
            @ToolParam(description = CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabGroup resolved = ResolvedResources.group(GitLabArguments.group(group));
        String fingerprint = CursorCodec.fingerprint("list_projects", resolved.fullPath());
        int page = cursors.pageOf(cursor, "list_projects", fingerprint);
        return cursors.result(gitlab.listProjects(resolved, page), "list_projects", fingerprint);
    }

    @Tool(name = "get_file", description = "Returns one UTF-8 text file from a GitLab repository at a branch, tag "
            + "or commit: project path, file path, ref, commit id, size in bytes and the content. Binary files and "
            + "files over the size limit are refused without content.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Pobierz plik z repozytorium")
    public RepositoryFile getFile(
            @ToolParam(description = PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Path of the file relative to the repository root, e.g. src/main.py")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.PATH_MAX_LENGTH) String path,
            @ToolParam(description = "Branch, tag or commit SHA; the project's default branch when omitted",
                    required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String ref) {
        return gitlab.getFile(ResolvedResources.project(GitLabArguments.project(project)),
                GitLabArguments.filePath(path), GitLabArguments.ref(ref));
    }
}
