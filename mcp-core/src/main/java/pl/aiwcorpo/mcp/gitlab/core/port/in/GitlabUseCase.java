package pl.aiwcorpo.mcp.gitlab.core.port.in;

import pl.aiwcorpo.mcp.gitlab.core.domain.GitlabRecord;

/** Inbound port: what the server can do, in business terms. Implemented in mcp-application, called by mcp-tools. */
public interface GitlabUseCase {

    /**
     * @throws pl.aiwcorpo.mcp.gitlab.core.domain.RecordNotFoundException when the record does not exist or
     *         the caller's backend account may not see it
     */
    GitlabRecord getRecord(String recordId);
}
