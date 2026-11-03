package pl.aiwcorpo.mcp.gitlab.integration;

import java.io.IOException;

/** A response body went past the limit of its client. Separate from other I/O errors so a caller can tell "too big" from "unreachable". */
final class ResponseTooLargeException extends IOException {

    ResponseTooLargeException() {
        super("backend response larger than the configured limit");
    }

    static boolean causedBy(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResponseTooLargeException) {
                return true;
            }
        }
        return false;
    }
}
