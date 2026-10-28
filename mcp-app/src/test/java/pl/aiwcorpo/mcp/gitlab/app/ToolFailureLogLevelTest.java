package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Failures the caller can cause or expect are INFO in the starter's {@code tool_failed} line; ERROR is for bugs. */
class ToolFailureLogLevelTest extends ServerTestSupport {

    private static final String PAT = "Bearer glpat-jkowalski-7f3a";

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger("pl.aiwcorpo.mcp.platform.error.McpErrorMapper");

    @BeforeEach
    void capture() {
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void release() {
        logger.detachAppender(events);
        events.stop();
    }

    @Test
    void aDomainErrorIsLoggedAtInfo() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertNotFoundOrDenied(call(client, "get_merge_request", Map.of("project", "team-a/app", "iid", 3)));
            assertInvalidArgument(call(client, "get_file", Map.of("project", "team-a/app", "path", "logo.png")),
                    "binary content is not returned");
        }

        List<ILoggingEvent> failures = events.list.stream()
                .filter(event -> event.getFormattedMessage().contains("event=tool_failed")).toList();
        assertThat(failures).hasSize(2).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
    }
}
