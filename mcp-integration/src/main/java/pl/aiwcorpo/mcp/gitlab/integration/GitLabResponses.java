package pl.aiwcorpo.mcp.gitlab.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.Branch;
import pl.aiwcorpo.mcp.gitlab.core.domain.CodeHit;
import pl.aiwcorpo.mcp.gitlab.core.domain.Commit;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.Issue;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.domain.Pipeline;
import pl.aiwcorpo.mcp.gitlab.core.domain.PipelineJob;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectDetails;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.SystemText;
import pl.aiwcorpo.mcp.gitlab.core.domain.TreeEntry;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * GitLab's representations, read tolerantly. GitLab only adds fields within API v4 and retires old ones slowly, so every
 * record drops unknown fields, boxes what an older or newer version may leave out, and reads a field that was renamed
 * from both names, the new one first: {@code detailed_merge_status} or {@code merge_status},
 * {@code merge_user} or {@code merged_by}, {@code draft} or {@code work_in_progress}. Nothing that is not mapped
 * here reaches the model.
 */
final class GitLabResponses {

    static final int PROJECT_DESCRIPTION_MAX_CHARS = 2_000;
    static final int MERGE_REQUEST_DESCRIPTION_MAX_CHARS = 20_000;
    static final int ISSUE_DESCRIPTION_MAX_CHARS = 20_000;
    static final int NOTE_MAX_CHARS = 4_000;
    static final int COMMIT_MESSAGE_MAX_CHARS = 8_000;
    static final int DIFF_MAX_CHARS = 16_000;
    static final int SNIPPET_MAX_CHARS = 2_000;
    static final int TITLE_MAX_CHARS = 1_000;
    static final int NAME_MAX_CHARS = 255;

    /** A title that marks a draft in the versions that have no {@code draft} field. */
    private static final Pattern DRAFT_TITLE = Pattern.compile("(?i)^\\s*(?:\\[draft]|\\(draft\\)|draft:)");

    private GitLabResponses() {
    }

    static @Nullable UntrustedText plainName(@Nullable String raw) {
        return SystemText.plain(raw, NAME_MAX_CHARS);
    }

    /** File paths can be longer than a name; GitLab allows 1024 characters. */
    static @Nullable UntrustedText plainPath(@Nullable String raw) {
        return SystemText.plain(raw, 1_100);
    }

    static List<UntrustedText> plainNames(@Nullable List<String> labels) {
        return labels == null ? List.of() : labels.stream().filter(Objects::nonNull)
                .map(GitLabResponses::plainName).filter(Objects::nonNull).toList();
    }

    static List<UntrustedText> usernames(@Nullable List<UserResponse> users) {
        return users == null ? List.of() : users.stream().filter(Objects::nonNull).map(UserResponse::username)
                .map(GitLabResponses::plainName).filter(Objects::nonNull).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroupResponse(long id, @JsonProperty("full_path") String fullPath) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NamespaceResponse(long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProjectResponse(long id, String name, @JsonProperty("path_with_namespace") String pathWithNamespace,
            String description, @JsonProperty("default_branch") String defaultBranch, Boolean archived,
            @JsonProperty("last_activity_at") String lastActivityAt, @JsonProperty("web_url") String webUrl,
            String visibility, NamespaceResponse namespace, List<String> topics) {

        GitLabProject toDomain() {
            List<String> reported = topics == null ? List.of() : topics.stream().filter(Objects::nonNull).toList();
            return new GitLabProject(id, GitLabHttp.required(pathWithNamespace), defaultBranch, reported);
        }

        ProjectSummary toSummary() {
            return new ProjectSummary(pathWithNamespace, plainName(this.name),
                    SystemText.markdown(description, PROJECT_DESCRIPTION_MAX_CHARS), plainName(defaultBranch),
                    Boolean.TRUE.equals(archived), lastActivityAt, webUrl);
        }

        ProjectDetails toDetails() {
            return new ProjectDetails(GitLabHttp.required(pathWithNamespace), plainName(this.name),
                    SystemText.markdown(description, PROJECT_DESCRIPTION_MAX_CHARS), plainName(defaultBranch),
                    Boolean.TRUE.equals(archived), visibility, lastActivityAt, webUrl);
        }

        /** -1 when GitLab sent no namespace, which no group id matches. */
        long namespaceId() {
            return namespace == null ? -1 : namespace.id();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserResponse(String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MergeRequestResponse(long iid, @JsonProperty("project_id") long projectId, String title,
            String description, String state, Boolean draft, @JsonProperty("work_in_progress") Boolean workInProgress,
            @JsonProperty("source_branch") String sourceBranch, @JsonProperty("target_branch") String targetBranch,
            UserResponse author, List<UserResponse> assignees, List<UserResponse> reviewers, List<String> labels,
            @JsonProperty("created_at") String createdAt, @JsonProperty("updated_at") String updatedAt,
            @JsonProperty("merged_at") String mergedAt,
            @JsonProperty("detailed_merge_status") String detailedMergeStatus,
            @JsonProperty("merge_status") String mergeStatus,
            @JsonProperty("has_conflicts") Boolean hasConflicts,
            @JsonProperty("merge_user") UserResponse mergeUser, @JsonProperty("merged_by") UserResponse mergedBy,
            String sha, @JsonProperty("merge_commit_sha") String mergeCommitSha,
            @JsonProperty("web_url") String webUrl) {

        boolean isDraft() {
            if (draft != null) {
                return draft;
            }
            if (workInProgress != null) {
                return workInProgress;
            }
            return title != null && DRAFT_TITLE.matcher(title).find();
        }

        FoundMergeRequest toDomain() {
            UserResponse merger = mergeUser != null ? mergeUser : mergedBy;
            return new FoundMergeRequest(projectId, new MergeRequest(null, iid,
                    SystemText.plain(title, TITLE_MAX_CHARS),
                    SystemText.markdown(description, MERGE_REQUEST_DESCRIPTION_MAX_CHARS),
                    state, isDraft(), plainName(sourceBranch), plainName(targetBranch),
                    author == null ? null : plainName(author.username()),
                    usernames(assignees), usernames(reviewers), plainNames(labels), createdAt, updatedAt, mergedAt,
                    detailedMergeStatus != null ? detailedMergeStatus : mergeStatus, Boolean.TRUE.equals(hasConflicts),
                    merger == null ? null : plainName(merger.username()), sha, webUrl));
        }

        MergeRequestSummary toSummary() {
            return new MergeRequestSummary(iid, SystemText.plain(title, TITLE_MAX_CHARS), state, isDraft(),
                    author == null ? null : plainName(author.username()), plainName(sourceBranch), plainName(targetBranch),
                    updatedAt, webUrl);
        }

        MergeRequestChange toChange() {
            return new MergeRequestChange(null, iid, SystemText.plain(title, TITLE_MAX_CHARS), state, isDraft(), webUrl);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NoteResponse(long id, UserResponse author, String body, Boolean system,
            @JsonProperty("created_at") String createdAt, @JsonProperty("noteable_iid") Long noteableIid,
            @JsonProperty("project_id") Long projectId) {

        /** A note that says it belongs to another project or another merge request or issue is not the caller's to read. */
        boolean belongsTo(long project, long iid) {
            return (projectId == null || projectId == project) && (noteableIid == null || noteableIid == iid);
        }

        NoteView toView() {
            return new NoteView(id, author == null ? null : plainName(author.username()),
                    SystemText.markdown(body, NOTE_MAX_CHARS), Boolean.TRUE.equals(system), createdAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IssueResponse(long iid, @JsonProperty("project_id") long projectId, String title, String description,
            String state, @JsonProperty("issue_type") String issueType, UserResponse author,
            List<UserResponse> assignees, List<String> labels, @JsonProperty("created_at") String createdAt,
            @JsonProperty("updated_at") String updatedAt, @JsonProperty("closed_at") String closedAt,
            @JsonProperty("web_url") String webUrl) {

        Issue toDomain() {
            return new Issue(null, iid, SystemText.plain(title, TITLE_MAX_CHARS),
                    SystemText.markdown(description, ISSUE_DESCRIPTION_MAX_CHARS), state, issueType,
                    author == null ? null : plainName(author.username()), usernames(assignees), plainNames(labels), createdAt,
                    updatedAt, closedAt, webUrl);
        }

        IssueSummary toSummary() {
            return new IssueSummary(iid, SystemText.plain(title, TITLE_MAX_CHARS), state,
                    author == null ? null : plainName(author.username()), updatedAt, webUrl);
        }

        IssueChange toChange() {
            return new IssueChange(null, iid, SystemText.plain(title, TITLE_MAX_CHARS), state, webUrl);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CommitResponse(String id, String title, String message, @JsonProperty("author_name") String authorName,
            @JsonProperty("authored_date") String authoredDate, StatsResponse stats,
            @JsonProperty("web_url") String webUrl) {

        Commit toDomain() {
            return new Commit(GitLabHttp.required(id), SystemText.plain(title, TITLE_MAX_CHARS),
                    SystemText.markdown(message, COMMIT_MESSAGE_MAX_CHARS), plainName(authorName), authoredDate,
                    stats == null ? null : stats.additions(), stats == null ? null : stats.deletions(), webUrl);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatsResponse(Long additions, Long deletions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BranchResponse(String name, Boolean merged, @JsonProperty("protected") Boolean protectedBranch,
            @JsonProperty("default") Boolean defaultBranch, BranchCommit commit) {

        Branch toDomain() {
            UntrustedText branchName = plainName(name);
            if (branchName == null) {
                throw new BackendResponseException("field the server relies on is missing");
            }
            return new Branch(branchName, Boolean.TRUE.equals(defaultBranch), Boolean.TRUE.equals(protectedBranch),
                    Boolean.TRUE.equals(merged), commit == null ? null : commit.id(),
                    commit == null ? null : SystemText.plain(commit.title(), TITLE_MAX_CHARS),
                    commit == null ? null : commit.committedDate());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BranchCommit(String id, String title, @JsonProperty("committed_date") String committedDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreeEntryResponse(String path, String type) {

        TreeEntry toDomain() {
            UntrustedText entryPath = plainPath(path);
            if (entryPath == null || type == null) {
                throw new BackendResponseException("field the server relies on is missing");
            }
            return new TreeEntry(entryPath, type.toLowerCase(Locale.ROOT));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BlobHitResponse(String path, String filename, Long startline, String data) {

        CodeHit toDomain() {
            UntrustedText hitPath = plainPath(path != null ? path : filename);
            if (hitPath == null) {
                throw new BackendResponseException("field the server relies on is missing");
            }
            return new CodeHit(hitPath, startline, SystemText.plainOrEmpty(data == null ? "" : data, SNIPPET_MAX_CHARS));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DiffResponse(@JsonProperty("old_path") String oldPath, @JsonProperty("new_path") String newPath,
            @JsonProperty("new_file") Boolean newFile, @JsonProperty("renamed_file") Boolean renamedFile,
            @JsonProperty("deleted_file") Boolean deletedFile, String diff) {

        FileDiff toDomain() {
            UntrustedText current = plainPath(newPath != null ? newPath : oldPath);
            if (current == null) {
                throw new BackendResponseException("field the server relies on is missing");
            }
            String text = diff == null ? "" : diff;
            return new FileDiff(Boolean.TRUE.equals(renamedFile) ? plainPath(oldPath) : null, current,
                    Boolean.TRUE.equals(newFile), Boolean.TRUE.equals(renamedFile), Boolean.TRUE.equals(deletedFile),
                    SystemText.hiddenCharacters(text), SystemText.plainOrEmpty(text, DIFF_MAX_CHARS));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PipelineResponse(long id, @JsonProperty("project_id") Long projectId, String status, String source, String ref, String sha, UserResponse user,
            @JsonProperty("created_at") String createdAt, @JsonProperty("started_at") String startedAt,
            @JsonProperty("finished_at") String finishedAt, Double duration, @JsonProperty("web_url") String webUrl) {

        boolean belongsTo(long project) {
            return projectId != null && projectId == project;
        }

        Pipeline toDomain() {
            return new Pipeline(id, status, source, plainName(ref), sha, user == null ? null : plainName(user.username()),
                    createdAt, startedAt, finishedAt, duration, webUrl);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JobResponse(long id, JobPipeline pipeline, String name, String stage, String status,
            @JsonProperty("allow_failure") Boolean allowFailure, @JsonProperty("created_at") String createdAt,
            @JsonProperty("finished_at") String finishedAt, Double duration, @JsonProperty("web_url") String webUrl) {

        /** The job names its pipeline and the pipeline its project; a job without that statement is not accepted. */
        boolean belongsTo(long project) {
            return pipeline != null && pipeline.projectId() != null && pipeline.projectId() == project;
        }

        PipelineJob toDomain() {
            return new PipelineJob(id, GitLabResponses.plainName(name), GitLabResponses.plainName(stage), status,
                    Boolean.TRUE.equals(allowFailure), createdAt, finishedAt, duration, webUrl);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JobPipeline(long id, @JsonProperty("project_id") Long projectId) {
    }

    /** {@code POST approve} answers with the approval state; the list of approvers is only counted. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApprovalResponse(@JsonProperty("approved_by") List<Object> approvedBy) {

        Integer approvals() {
            return approvedBy == null ? null : approvedBy.size();
        }
    }
}
