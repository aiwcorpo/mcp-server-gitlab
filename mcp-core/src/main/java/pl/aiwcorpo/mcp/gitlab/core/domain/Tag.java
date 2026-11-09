package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** A tag with the commit it points to; {@code message} is the annotation of an annotated tag, written by whoever tagged. */
public record Tag(UntrustedText name, boolean protectedTag, @Nullable UntrustedText message, @Nullable String commitId,
        @Nullable UntrustedText commitTitle, @Nullable String committedAt) {
}
