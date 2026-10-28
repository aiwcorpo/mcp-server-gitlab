package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * One project of a group listing, with the fields a caller needs to pick a project. Name, description and
 * default branch are written by GitLab users and so are {@link UntrustedText}. The path is a resource reference:
 * the starter resolves every listed project and drops the ones the caller's policy refuses (restricted topics on the
 * project or on any group above it).
 */
public record ProjectSummary(@ResourceRef(type = "GitLab::Project") String path, @Nullable UntrustedText name,
        @Nullable UntrustedText description, @Nullable UntrustedText defaultBranch, boolean archived,
        @Nullable String lastActivityAt, @Nullable String webUrl) {
}
