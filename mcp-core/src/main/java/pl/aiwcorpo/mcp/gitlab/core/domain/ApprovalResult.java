package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;

/**
 * The merge request after the caller approved it. {@code approvals} is the number of users who approved so far, absent
 * when GitLab does not say; {@code headSha} is the commit the approval was given for.
 */
public record ApprovalResult(String project, long iid, String headSha, @Nullable Integer approvals) {

    public ApprovalResult inProject(String canonicalPath) {
        return new ApprovalResult(canonicalPath, iid, headSha, approvals);
    }
}
