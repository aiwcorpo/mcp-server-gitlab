package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

public record Branch(UntrustedText name, boolean defaultBranch, boolean protectedBranch, boolean merged,
        @Nullable String commitId, @Nullable UntrustedText commitTitle, @Nullable String committedAt) {
}
