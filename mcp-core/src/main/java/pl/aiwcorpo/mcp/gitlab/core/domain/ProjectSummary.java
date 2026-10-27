package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * One project of a group listing, with the fields a caller needs to pick a project. Name, description and
 * default branch are written by GitLab users and so are {@link UntrustedText}.
 */
public record ProjectSummary(String path, UntrustedText name, UntrustedText description,
        UntrustedText defaultBranch, boolean archived, String lastActivityAt, String webUrl) {
}
