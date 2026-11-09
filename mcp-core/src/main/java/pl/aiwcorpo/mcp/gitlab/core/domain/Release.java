package pl.aiwcorpo.mcp.gitlab.core.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * A release of a project. The description is Markdown written by the releasing user. Only the names of the asset links
 * are returned, not their URLs: a URL in a release is an address the model could be talked into fetching.
 */
public record Release(UntrustedText tag, @Nullable UntrustedText name, @Nullable String releasedAt,
        @Nullable UntrustedText description, List<UntrustedText> assetLinkNames) {
}
