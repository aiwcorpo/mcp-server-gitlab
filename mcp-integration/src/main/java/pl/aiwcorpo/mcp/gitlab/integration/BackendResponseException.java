package pl.aiwcorpo.mcp.gitlab.integration;

/**
 * The backend answered, but not with something the server can use: a redirect, a body that does not parse, a
 * record without its id, a body over the size limit. Carries a fixed message and no cause, because the parser's
 * message quotes the body and would reach the starter's {@code tool_failed} log line. The client sees
 * {@code internal_error}.
 */
public class BackendResponseException extends RuntimeException {

    private final boolean tooLarge;

    public BackendResponseException(String reason) {
        this(reason, false);
    }

    BackendResponseException(String reason, boolean tooLarge) {
        super("backend response not usable: " + reason, null, false, false);
        this.tooLarge = tooLarge;
    }

    /** The body went past the size limit of its client, as opposed to being unreadable. */
    boolean tooLarge() {
        return tooLarge;
    }
}
