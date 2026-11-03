package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;

/**
 * What the merge left behind. {@code state} is GitLab's: {@code merged}, or still {@code opened} when the project's
 * rules defer the merge. {@code alreadyMerged} is true when the call found the merge request merged at the commit the
 * caller named, so a repeated call reports the state it wanted instead of an error.
 */
public record MergeOutcome(String project, long iid, @Nullable String state, @Nullable String mergeCommitId,
        @Nullable String mergedAt, boolean alreadyMerged) {

    public MergeOutcome inProject(String canonicalPath) {
        return new MergeOutcome(canonicalPath, iid, state, mergeCommitId, mergedAt, alreadyMerged);
    }
}
