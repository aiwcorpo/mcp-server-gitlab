package pl.aiwcorpo.mcp.gitlab.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabBackendProperties;

/** The file limit is checked at startup: the escaped content of the largest file must fit the 1 MiB result. */
class FileLimitWiringTest {

    private final ApplicationWiringConfiguration wiring = new ApplicationWiringConfiguration();
    private final GitLabBackendProperties backend = new GitLabBackendProperties("https://gitlab.internal", null,
            Duration.ofSeconds(2), Duration.ofSeconds(10), DataSize.ofMegabytes(1), DataSize.ofMegabytes(8), false);

    @Test
    void theDefaultAndTheLargestAllowedLimitStart() {
        assertThatCode(() -> wiring.gitLabUseCase(null, backend, 262_144)).doesNotThrowAnyException();
        assertThatCode(() -> wiring.gitLabUseCase(null, backend, ApplicationWiringConfiguration.MAX_CONFIGURABLE_FILE_BYTES))
                .doesNotThrowAnyException();
    }

    @Test
    void aLimitWhoseEscapedContentCouldExceedTheResultDoesNotStart() {
        assertThatThrownBy(() -> wiring.gitLabUseCase(null, backend, 1_000_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MCP_GITLAB_FILE_MAX_BYTES");
        assertThat(ApplicationWiringConfiguration.MAX_CONFIGURABLE_FILE_BYTES * 2).isLessThan(1024 * 1024);
    }
}
