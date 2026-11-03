package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

public record PipelineJob(long id, @Nullable UntrustedText name, @Nullable UntrustedText stage,
        @Nullable String status, boolean allowFailure, @Nullable String createdAt, @Nullable String finishedAt,
        @Nullable Double durationSeconds, @Nullable String webUrl) {
}
