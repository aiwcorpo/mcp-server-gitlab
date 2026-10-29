package pl.aiwcorpo.mcp.gitlab.tools;

import java.util.Optional;
import org.springframework.stereotype.Component;
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
