package pl.aiwcorpo.mcp.gitlab.core.domain;

import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** An entry of a repository directory; {@code type} is GitLab's own: {@code blob}, {@code tree} or {@code commit}. */
public record TreeEntry(UntrustedText path, String type) {
}
