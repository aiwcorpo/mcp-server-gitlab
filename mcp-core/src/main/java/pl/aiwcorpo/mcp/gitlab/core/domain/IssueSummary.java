package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

public record IssueSummary(long iid, @Nullable UntrustedText title, @Nullable String state,
        @Nullable UntrustedText author, @Nullable String updatedAt, @Nullable String webUrl) {
}
