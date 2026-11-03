package pl.aiwcorpo.mcp.gitlab.tools;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendRateLimitedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendRefusedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileNotReturnedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException;
import pl.aiwcorpo.mcp.platform.error.McpErrorMapper.SafeError;
import pl.aiwcorpo.mcp.platform.error.ToolErrorCode;
import pl.aiwcorpo.mcp.platform.error.ToolErrorMapping;

/** Domain exceptions of this server as C10 errors; everything else falls through to the starter's defaults. */
@Component
public class GitLabToolErrors implements ToolErrorMapping {

    @Override
    public Optional<SafeError> map(Throwable ex) {
        if (ex instanceof ResourceNotFoundException) {
            return Optional.of(new SafeError(ToolErrorCode.NOT_FOUND_OR_DENIED.code(),
                    ToolErrorCode.NOT_FOUND_OR_DENIED_MESSAGE));
        }
        if (ex instanceof BackendRefusedException refused) {
            String message = switch (refused.reason()) {
                case CONFLICT -> "GitLab refused the change because it conflicts with the current state of the "
                        + "project, for example the head commit moved or an open merge request for these branches exists.";
                case REJECTED -> "GitLab refused the change: it is not valid in the current state of the project, for "
                        + "example a draft, a missing branch or an unresolved blocker.";
                case LOG_TOO_LARGE -> "The job log is larger than the server reads; its end is not returned.";
                case UNCONFIRMED -> "The change may have been saved, but GitLab's answer could not be confirmed for this "
                        + "project. Check the current state before repeating it.";
            };
            String code = refused.reason() == BackendRefusedException.Reason.UNCONFIRMED
                    ? ToolErrorCode.UPSTREAM_UNAVAILABLE.code() : ToolErrorCode.INVALID_ARGUMENT.code();
            return Optional.of(new SafeError(code, message));
        }
        if (ex instanceof BackendRateLimitedException limited) {
            return Optional.of(new SafeError(ToolErrorCode.RATE_LIMITED.code(),
                    "GitLab is limiting requests; try again later.",
                    java.util.Map.of("retryAfterSeconds", limited.retryAfterSeconds())));
        }
        if (ex instanceof FileNotReturnedException notReturned) {
            String message = switch (notReturned.reason()) {
                case BINARY -> "The file is not UTF-8 text; binary content is not returned.";
                case TOO_LARGE -> "The file is larger than " + notReturned.limitBytes()
                        + " bytes; its content is not returned.";
            };
            return Optional.of(new SafeError(ToolErrorCode.INVALID_ARGUMENT.code(), message));
        }
        return Optional.empty();
    }
}
