package pl.aiwcorpo.mcp.gitlab.core.domain;

/** One project of a group listing, with the fields a caller needs to pick a project. */
public record ProjectSummary(String path, String name, UntrustedText description, String defaultBranch,
        boolean archived, String lastActivityAt, String webUrl) {
}
