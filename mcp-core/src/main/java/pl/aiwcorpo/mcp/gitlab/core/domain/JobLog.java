package pl.aiwcorpo.mcp.gitlab.core.domain;

import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * The end of a job log. {@code log} holds the last lines that fit the limits, cut at a line break; it is
 * {@link UntrustedText#truncated()} when earlier lines were left out. Terminal escape codes and runner section
 * markers are removed, and strings shaped like credentials are replaced by {@code [redacted]}: GitLab masks only the
 * variables a project marked as masked, and job output often prints more than that.
 *
 * @param redactions how many strings were replaced, so a reader knows the log was edited
 */
public record JobLog(String project, long jobId, int redactions, UntrustedText log) {

    public JobLog inProject(String canonicalPath) {
        return new JobLog(canonicalPath, jobId, redactions, log);
    }
}
