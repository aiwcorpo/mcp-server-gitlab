package pl.aiwcorpo.mcp.gitlab.core.port.out;

import java.util.Optional;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileMetadata;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;

/**
 * GitLab REST API v4 as the calling user. Empty means GitLab did not show the thing to the caller (404 or
 * 403), whether it exists or not. Everything after the path lookups is addressed by numeric id.
 */
public interface GitLabPort {

    /** GitLab's own maximum for {@code per_page}; the listing of a group's projects always asks for this many. */
    int PAGE_SIZE = 100;

    Optional<GitLabGroup> findGroup(String fullPath);

    Optional<GitLabProject> findProject(String pathWithNamespace);

    /**
     * One page (from 1) of projects directly in the group, ordered by path; shared projects are not included. Answers
     * for the same group, size and page within one tool call are the same page.
     */
    ProjectPage listGroupProjects(long groupId, int pageSize, int page);

    /**
     * The page of the group's projects that the current tool call listed last, or the first page when it listed none.
     * The starter resolves every listed project after the tool returned, and this is where those projects are, however
     * far into the listing the caller is.
     */
    ProjectPage listedGroupProjects(long groupId, int pageSize);

    Optional<FileMetadata> fileMetadata(long projectId, String filePath, String ref);

    /** Raw bytes of the file at a commit. */
    Optional<byte[]> fileContent(long projectId, String filePath, String commitId);

    Optional<FoundMergeRequest> findMergeRequest(long projectId, long iid);

    Optional<MergeRequestNote> addMergeRequestNote(long projectId, long iid, String body);
}
