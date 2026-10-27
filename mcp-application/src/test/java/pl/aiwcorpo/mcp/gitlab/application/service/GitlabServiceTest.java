package pl.aiwcorpo.mcp.gitlab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitlabRecord;
import pl.aiwcorpo.mcp.gitlab.core.domain.RecordNotFoundException;

class GitlabServiceTest {

    private final Map<String, GitlabRecord> backend = Map.of("ABC-1", new GitlabRecord("ABC-1", "ABC", "First", "open"));
    private final GitlabService service = new GitlabService(id -> Optional.ofNullable(backend.get(id)));

    @Test
    void returnsTheRecordTheBackendShows() {
        assertThat(service.getRecord("ABC-1").title()).isEqualTo("First");
    }

    @Test
    void aRecordTheBackendDoesNotShowIsNotFoundOrDenied() {
        assertThatThrownBy(() -> service.getRecord("ABC-2")).isInstanceOf(RecordNotFoundException.class);
    }
}
