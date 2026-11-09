package pl.aiwcorpo.mcp.gitlab.core.domain;

import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * A text file from the artifacts of a job. A CI job writes it, so its content is as untrusted as a job log.
 *
 * @param project canonical project path
 * @param path    path of the file inside the artifacts
 * @param size    size in bytes
 * @param hiddenCharactersRemoved as in {@link RepositoryFile}
 */
public record ArtifactFile(String project, long jobId, String path, long size, int hiddenCharactersRemoved,
        UntrustedText content) {
}
