package pl.aiwcorpo.mcp.gitlab.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.gitlab.core.domain.Branch;
import pl.aiwcorpo.mcp.gitlab.core.domain.CodeHit;
import pl.aiwcorpo.mcp.gitlab.core.domain.Commit;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectDetails;
import pl.aiwcorpo.mcp.gitlab.core.domain.TreeEntry;
import pl.aiwcorpo.mcp.gitlab.core.port.in.RepositoryUseCase;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.paging.CursorCodec;
import pl.aiwcorpo.mcp.platform.paging.PagedResult;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;

/**
 * Reads of one project's repository. All tools take the project as a {@link ResourceRef}; none searches outside it.
 * The code search sends the caller's words to GitLab as a search term in the project's own scope, not as a query
 * language of the model's making.
 */
@Service
public class RepositoryTools {

    private final RepositoryUseCase repository;
    private final PageCursors cursors;

    public RepositoryTools(RepositoryUseCase repository, PageCursors cursors) {
        this.repository = repository;
        this.cursors = cursors;
    }

    @Tool(name = "get_project", description = "Returns one GitLab project: full path, name, description, default "
            + "branch, visibility, archived flag, last activity and web URL.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Get project")
    public ProjectDetails getProject(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project) {
        return repository.getProject(ResolvedResources.project(GitLabArguments.project(project)));
    }

    @Tool(name = "list_repository_tree", description = "Lists the entries of one directory of a repository (not its "
            + "subdirectories), 100 per page: path and type (blob for a file, tree for a directory, commit for a "
            + "submodule). Returns nextCursor when more entries exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List repository tree")
    public PagedResult<TreeEntry> listRepositoryTree(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Directory relative to the repository root, e.g. src/main; the root when omitted",
                    required = false)
            @ArgumentLimits(maxLength = GitLabArguments.PATH_MAX_LENGTH) String path,
            @ToolParam(description = "Branch, tag or commit SHA; the project's default branch when omitted",
                    required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String ref,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        String directory = GitLabArguments.treePath(path);
        String checkedRef = GitLabArguments.ref(ref);
        String fingerprint = CursorCodec.fingerprint("list_repository_tree", resolved.pathWithNamespace(), directory, checkedRef);
        int page = cursors.pageOf(cursor, "list_repository_tree", fingerprint);
        return cursors.result(repository.tree(resolved, directory, checkedRef, page), "list_repository_tree", fingerprint);
    }

    @Tool(name = "search_code", description = "Searches the files of one project for a word or phrase, 20 hits per page: "
            + "file path, first line number of the match and the matching lines. The search is limited to this "
            + "project, on its default branch or on the given ref. Returns nextCursor when more hits exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Search code")
    public PagedResult<CodeHit> searchCode(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Word or phrase to look for, plain text")
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.SEARCH_MAX_LENGTH) String query,
            @ToolParam(description = "Branch or tag to search; the project's default branch when omitted", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String ref,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        String term = GitLabArguments.searchTerm(query);
        String checkedRef = GitLabArguments.ref(ref);
        String fingerprint = CursorCodec.fingerprint("search_code", resolved.pathWithNamespace(), term, checkedRef);
        int page = cursors.pageOf(cursor, "search_code", fingerprint);
        return cursors.result(repository.searchCode(resolved, term, checkedRef, page), "search_code", fingerprint);
    }

    @Tool(name = "list_branches", description = "Lists the branches of a project, 50 per page: name, whether it is the "
            + "default or a protected branch, whether it is merged into the default branch, and its last commit. "
            + "Returns nextCursor when more branches exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List branches")
    public PagedResult<Branch> listBranches(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Only branches whose name contains this text", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String search,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        String text = GitLabArguments.ref(search);
        String fingerprint = CursorCodec.fingerprint("list_branches", resolved.pathWithNamespace(), text);
        int page = cursors.pageOf(cursor, "list_branches", fingerprint);
        return cursors.result(repository.branches(resolved, text, page), "list_branches", fingerprint);
    }

    @Tool(name = "list_commits", description = "Lists commits of a project, newest first, 50 per page: id, title, "
            + "author name, authoring time and web URL. Optionally limited to a branch or tag, to the commits that "
            + "touched one path, and to a period. Returns nextCursor when more commits exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List commits")
    public PagedResult<Commit> listCommits(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Branch, tag or commit SHA to list from; the default branch when omitted", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.REF_MAX_LENGTH) String ref,
            @ToolParam(description = "Only commits that changed this file or directory", required = false)
            @ArgumentLimits(minLength = 1, maxLength = GitLabArguments.PATH_MAX_LENGTH) String path,
            @ToolParam(description = "Only commits from this date on, YYYY-MM-DD or YYYY-MM-DDThh:mm:ssZ", required = false)
            @ArgumentLimits(minLength = 10, maxLength = 20, pattern = GitLabArguments.DATE_PATTERN) String since,
            @ToolParam(description = "Only commits up to this date, YYYY-MM-DD or YYYY-MM-DDThh:mm:ssZ", required = false)
            @ArgumentLimits(minLength = 10, maxLength = 20, pattern = GitLabArguments.DATE_PATTERN) String until,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        String checkedRef = GitLabArguments.ref(ref);
        String checkedPath = path == null ? null : GitLabArguments.filePath(path);
        String from = GitLabArguments.date(since);
        String to = GitLabArguments.date(until);
        String fingerprint = CursorCodec.fingerprint("list_commits", resolved.pathWithNamespace(), checkedRef, checkedPath, from, to);
        int page = cursors.pageOf(cursor, "list_commits", fingerprint);
        return cursors.result(repository.commits(resolved, checkedRef, checkedPath, from, to, page), "list_commits", fingerprint);
    }

    @Tool(name = "get_commit", description = "Returns one commit of a project: id, title, full message, author name, "
            + "authoring time, number of added and deleted lines and web URL.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Get commit")
    public Commit getCommit(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Commit id in lower case hexadecimal, 7 to 40 characters")
            @ArgumentLimits(minLength = 7, maxLength = 40, pattern = GitLabArguments.COMMIT_PATTERN) String sha) {
        return repository.commit(ResolvedResources.project(GitLabArguments.project(project)), GitLabArguments.commit(sha));
    }

    @Tool(name = "get_commit_diff", description = "Returns the changes of one commit, 10 files per page: old and new "
            + "path, whether the file was added, renamed or deleted, and the diff text, cut at 16000 characters per "
            + "file. Returns truncated when a diff was cut and nextCursor when more files exist.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Get commit diff")
    public PagedResult<FileDiff> getCommitDiff(
            @ToolParam(description = GitLabTools.PROJECT_PARAM)
            @ArgumentLimits(contractPattern = "GitLabProjectPath", minLength = 3)
            @ResourceRef(type = ResolvedResources.PROJECT) String project,
            @ToolParam(description = "Commit id in lower case hexadecimal, 7 to 40 characters")
            @ArgumentLimits(minLength = 7, maxLength = 40, pattern = GitLabArguments.COMMIT_PATTERN) String sha,
            @ToolParam(description = GitLabTools.CURSOR_PARAM, required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN) String cursor) {
        GitLabProject resolved = ResolvedResources.project(GitLabArguments.project(project));
        String commit = GitLabArguments.commit(sha);
        String fingerprint = CursorCodec.fingerprint("get_commit_diff", resolved.pathWithNamespace(), commit);
        int page = cursors.pageOf(cursor, "get_commit_diff", fingerprint);
        return cursors.result(repository.commitDiffs(resolved, commit, page), "get_commit_diff", fingerprint,
                diff -> diff.diff().truncated());
    }
}
