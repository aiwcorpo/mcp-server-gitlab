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
 * @param hiddenCharactersRemoved number of control, invisible and direction-changing characters (bidi overrides,
 *                 zero-width characters, a BOM) that the text hygiene dropped from {@code content}, counted on the
 *                 whole file; a code review needs to know that the file held them, because they can make code read
 *                 differently from what it does
 * @param content  the file as UTF-8 text, marked as data
 */
public record RepositoryFile(String project, String path, String ref, String commitId, long size,
        int hiddenCharactersRemoved, UntrustedText content) {
}
