package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * The group, project, file or merge request does not exist or the caller may not see it. One exception for
 * both cases: the client gets the same {@code not_found_or_denied}, so it cannot probe what exists.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("resource not found or not visible", null, false, false);
    }
}
