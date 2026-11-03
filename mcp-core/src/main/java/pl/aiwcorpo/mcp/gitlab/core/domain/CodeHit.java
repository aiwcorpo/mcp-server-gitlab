package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** A file that matched a code search in one project, with the matching lines as GitLab cut them. */
public record CodeHit(UntrustedText path, @Nullable Long startLine, UntrustedText snippet) {
}
