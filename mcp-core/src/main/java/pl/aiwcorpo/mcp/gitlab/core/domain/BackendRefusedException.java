package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * GitLab understood a change and refused it for the state the project is in. Carries no text of GitLab's: the reason
 * is one of a few fixed ones, so the answer cannot echo the credential or a backend message.
 */
public class BackendRefusedException extends RuntimeException {

    public enum Reason {
        /** 409: the head commit moved, or an open merge request for the same branches exists. */
        CONFLICT,
        /** 400, 405, 406, 422: not valid in the current state (draft, unresolved discussions, missing branch). */
        REJECTED,
        /** The job log is larger than the server reads. */
        LOG_TOO_LARGE,
        /** A change was sent but GitLab's answer does not name this project, so the outcome is not known. */
        UNCONFIRMED
    }

    private final Reason reason;

    public BackendRefusedException(Reason reason) {
        super(reason.name(), null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
