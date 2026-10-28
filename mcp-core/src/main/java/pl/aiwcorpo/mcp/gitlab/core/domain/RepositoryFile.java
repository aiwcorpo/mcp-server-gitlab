package pl.aiwcorpo.mcp.gitlab.core.domain;

import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * A text file read from a repository.
 *
 * @param project  canonical project path
 * @param path     path of the file
 * @param ref      the ref the caller asked for (or the default branch)
 * @param commitId the commit the content was read at
 * @param size     size in bytes
 * @param content  the file as UTF-8 text, marked as data
 */
public record RepositoryFile(String project, String path, String ref, String commitId, long size,
        UntrustedText content) {
}
