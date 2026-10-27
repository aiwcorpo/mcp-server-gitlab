package pl.aiwcorpo.mcp.gitlab.core.port.out;

import java.util.Optional;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitlabRecord;

/** Outbound port to the backend system, implemented in mcp-integration. */
public interface GitlabResourcePort {

    /** Empty when the backend does not show the record to the caller, whether it exists or not. */
    Optional<GitlabRecord> findById(String recordId);
}
