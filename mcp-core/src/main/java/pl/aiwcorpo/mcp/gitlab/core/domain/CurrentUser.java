package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** The GitLab user behind the caller's credential. No e-mail address, no administrator flag, no token data. */
public record CurrentUser(long id, UntrustedText username, @Nullable UntrustedText name, @Nullable String state) {
}
