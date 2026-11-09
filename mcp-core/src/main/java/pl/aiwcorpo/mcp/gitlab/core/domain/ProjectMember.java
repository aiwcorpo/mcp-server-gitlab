package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * A member of a project, direct or inherited from a group. {@code accessLevel} is GitLab's number and {@code role} its
 * name, absent for a level this server does not know. There is no e-mail address on purpose.
 */
public record ProjectMember(UntrustedText username, @Nullable UntrustedText name, int accessLevel, @Nullable String role,
        @Nullable String state) {
}
