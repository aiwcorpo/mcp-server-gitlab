package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * What GitLab says about a file before its content is read.
 *
 * @param filePath path of the file in the repository
 * @param commitId commit the ref pointed to; the content is then read at this commit, so a branch moving
 *                 between the two calls cannot swap in a larger file
 * @param size     size in bytes
 */
public record FileMetadata(String filePath, String commitId, long size) {
}
