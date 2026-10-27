package pl.aiwcorpo.mcp.gitlab.core.domain;

/** The file exists but its content is not handed out (binary or over the size limit). Carries no content. */
public class FileNotReturnedException extends RuntimeException {

    public enum Reason {
        BINARY,
        TOO_LARGE
    }

    private final Reason reason;
    private final long limitBytes;

    public FileNotReturnedException(Reason reason, long limitBytes) {
        super("file content not returned: " + reason, null, false, false);
        this.reason = reason;
        this.limitBytes = limitBytes;
    }

    public Reason reason() {
        return reason;
    }

    public long limitBytes() {
        return limitBytes;
    }
}
