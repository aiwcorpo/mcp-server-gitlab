package pl.aiwcorpo.mcp.gitlab.core.port.in;

import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectList;
import pl.aiwcorpo.mcp.gitlab.core.domain.RepositoryFile;

/**
 * What the server does in GitLab, always as the calling user. The group or project arrives already resolved by GitLab
 * (the starter's resolver did it, with the caller's credential, and the resource policy has let it through); after
 * that every call addresses it by numeric id. A thing GitLab does not show to the user ends in
 * {@link pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException}. Arguments arrive validated.
 */
public interface GitLabUseCase {

    ProjectList listProjects(GitLabGroup group);

    /** @param ref branch, tag or commit; {@code null} for the default branch */
    RepositoryFile getFile(GitLabProject project, String filePath, String ref);

    MergeRequest getMergeRequest(GitLabProject project, long iid);

    MergeRequestNote commentMergeRequest(GitLabProject project, long iid, String body);
}
