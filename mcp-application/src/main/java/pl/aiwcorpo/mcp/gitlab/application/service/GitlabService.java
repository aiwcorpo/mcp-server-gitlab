package pl.aiwcorpo.mcp.gitlab.application.service;

import pl.aiwcorpo.mcp.gitlab.core.domain.GitlabRecord;
import pl.aiwcorpo.mcp.gitlab.core.domain.RecordNotFoundException;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitlabUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitlabResourcePort;

/** Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring. */
public class GitlabService implements GitlabUseCase {

    private final GitlabResourcePort resource;

    public GitlabService(GitlabResourcePort resource) {
        this.resource = resource;
    }

    @Override
    public GitlabRecord getRecord(String recordId) {
        return resource.findById(recordId).orElseThrow(RecordNotFoundException::new);
    }
}
