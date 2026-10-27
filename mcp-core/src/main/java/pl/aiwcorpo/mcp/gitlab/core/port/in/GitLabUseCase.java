package pl.aiwcorpo.mcp.gitlab.core.port.in;

import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectList;
import pl.aiwcorpo.mcp.gitlab.core.domain.RepositoryFile;

/**
 * What the server does in GitLab, always as the calling user. Group and project paths are resolved to ids
 * by GitLab first; a path GitLab does not show to the user ends in
 * {@link pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException}. Arguments arrive validated.
 */
public interface GitLabUseCase {

    ProjectList listProjects(String groupPath);

    /** @param ref branch, tag or commit; {@code null} for the default branch */
    RepositoryFile getFile(String projectPath, String filePath, String ref);

    MergeRequest getMergeRequest(String projectPath, long iid);

    MergeRequestNote commentMergeRequest(String projectPath, long iid, String body);
}
