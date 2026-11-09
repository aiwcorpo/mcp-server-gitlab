package pl.aiwcorpo.mcp.gitlab.core.port.in;

import pl.aiwcorpo.mcp.gitlab.core.domain.ArtifactFile;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.CurrentUser;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.RepositoryFile;

/**
 * What the server does in GitLab, always as the calling user. The group or project arrives already resolved by GitLab
 * (the starter's resolver did it, with the caller's credential, and the resource policy has let it through); after
 * that every call addresses it by numeric id. A thing GitLab does not show to the user ends in
 * {@link pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException}. Arguments arrive validated.
 */
public interface GitLabUseCase {

    /** One page (from 1) of the projects directly in the group. */
    BackendPage<ProjectSummary> listProjects(GitLabGroup group, int page);

    /** @param ref branch, tag or commit; {@code null} for the default branch */
    RepositoryFile getFile(GitLabProject project, String filePath, String ref);

    /** A UTF-8 text file from the artifacts of a job of the project; binary and oversized files are refused. */
    ArtifactFile getJobArtifactFile(GitLabProject project, long jobId, String artifactPath);

    /** The user the caller's credential belongs to; instance-wide, not tied to a group or project. */
    CurrentUser currentUser();

    MergeRequest getMergeRequest(GitLabProject project, long iid);

    MergeRequestNote commentMergeRequest(GitLabProject project, long iid, String body);
}
