package pl.aiwcorpo.mcp.gitlab.core.domain;

/** GitLab answered 429. Carries only the wait it asked for, never its text. */
public class BackendRateLimitedException extends RuntimeException {

    private final int retryAfterSeconds;

    public BackendRateLimitedException(int retryAfterSeconds) {
        super("rate limited", null, false, false);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
