package pl.aiwcorpo.mcp.gitlab.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GitLabArgumentsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void pathLimitIsTheContractLimit() throws IOException {
        JsonNode defs = read("mcp-contracts/schemas/common.schema.json").path("$defs");

        assertThat(defs.path("GitLabGroupPath").path("maxLength").asInt()).isEqualTo(GitLabArguments.PATH_MAX_LENGTH);
        assertThat(defs.path("GitLabProjectPath").path("maxLength").asInt()).isEqualTo(GitLabArguments.PATH_MAX_LENGTH);
    }

    @Test
    void groupFollowsTheContractVectors() throws IOException {
        checkVectors("mcp-contracts/test-vectors/resource-ids/gitlab-group.json", GitLabArguments::group);
        assertThatThrownBy(() -> GitLabArguments.group("a/".repeat(20) + "b")).isInstanceOf(InvalidToolArgumentException.class);
        assertThatThrownBy(() -> GitLabArguments.group("a".repeat(1025))).isInstanceOf(InvalidToolArgumentException.class);
        assertThatThrownBy(() -> GitLabArguments.group(null)).isInstanceOf(InvalidToolArgumentException.class);
    }

    @Test
    void projectFollowsTheContractVectors() throws IOException {
        checkVectors("mcp-contracts/test-vectors/resource-ids/gitlab-project.json", GitLabArguments::project);
        assertThatThrownBy(() -> GitLabArguments.project("42")).as("numeric ids are not accepted, only paths")
                .isInstanceOf(InvalidToolArgumentException.class);
        assertThatThrownBy(() -> GitLabArguments.project("team-a/app?private_token=x"))
                .isInstanceOf(InvalidToolArgumentException.class);
    }

    @Test
    void filePathStaysInsideTheRepository() {
        for (String ok : new String[] {"README.md", "src/main/App.java", "docs/a b.md", ".gitlab-ci.yml", "a..b/c"}) {
            assertThatCode(() -> GitLabArguments.filePath(ok)).as(ok).doesNotThrowAnyException();
        }
        for (String bad : new String[] {"", "/etc/passwd", "../secret", "a/../../b", "a/./b", "a//b", "a/", "a\\b",
                "a%2F..%2Fb", "a\nb", "a\u0000b", "x".repeat(1025)}) {
            assertThatThrownBy(() -> GitLabArguments.filePath(bad)).as(bad).isInstanceOf(InvalidToolArgumentException.class);
        }
    }

    @Test
    void refIsABranchTagOrCommit() {
        assertThat(GitLabArguments.ref(null)).isNull();
        for (String ok : new String[] {"main", "feature/retry", "v1.0", "release-2026.10", "6104942438c14ec7bd21c6cd5bd995272b3faff6"}) {
            assertThatCode(() -> GitLabArguments.ref(ok)).as(ok).doesNotThrowAnyException();
        }
        for (String bad : new String[] {"", "-main", ".hidden", "/main", "main/", "a..b", "a//b", "main.lock", "main.",
                "HEAD~1", "main^", "a:b", "a b", "@{1}", "main&private_token=x", "x".repeat(256)}) {
            assertThatThrownBy(() -> GitLabArguments.ref(bad)).as(bad).isInstanceOf(InvalidToolArgumentException.class);
        }
    }

    @Test
    void iidIsAPositiveInt() {
        assertThat(GitLabArguments.iid(1L)).isEqualTo(1);
        assertThat(GitLabArguments.iid((long) Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        for (Long bad : new Long[] {null, 0L, -1L, Integer.MAX_VALUE + 1L}) {
            assertThatThrownBy(() -> GitLabArguments.iid(bad)).isInstanceOf(InvalidToolArgumentException.class);
        }
    }

    @Test
    void commentBodyCannotCarryQuickActions() {
        assertThatCode(() -> GitLabArguments.commentBody("Looks good.\nSee src/a/b.java and a/b paths.")).doesNotThrowAnyException();
        for (String bad : new String[] {"/merge", "LGTM\n/approve", "ok\r\n  /assign @me", "\t/close", "", "   ",
                "bell\u0007", "x".repeat(10_001)}) {
            assertThatThrownBy(() -> GitLabArguments.commentBody(bad)).as(bad).isInstanceOf(InvalidToolArgumentException.class);
        }
    }

    @Test
    void commentBodyCannotMentionEveryone() {
        for (String ok : new String[] {"thanks @anowak", "mail me: a@all.internal", "see @alligator", "@all-hands doc"}) {
            assertThatCode(() -> GitLabArguments.commentBody(ok)).as(ok).doesNotThrowAnyException();
        }
        for (String bad : new String[] {"@all please look", "cc @all", "(@all)", "x\n@all."}) {
            assertThatThrownBy(() -> GitLabArguments.commentBody(bad)).as(bad).isInstanceOf(InvalidToolArgumentException.class);
        }
    }

    private static void checkVectors(String resource, UnaryOperator<String> check) throws IOException {
        JsonNode vectors = read(resource);
        assertThat(vectors.path("valid").size()).isPositive();
        for (JsonNode valid : vectors.path("valid")) {
            assertThatCode(() -> check.apply(valid.asString())).as(valid.asString()).doesNotThrowAnyException();
        }
        for (JsonNode invalid : vectors.path("invalid")) {
            assertThatThrownBy(() -> check.apply(invalid.asString())).as(invalid.asString())
                    .isInstanceOf(InvalidToolArgumentException.class);
        }
    }

    private static JsonNode read(String resource) throws IOException {
        try (InputStream in = GitLabArgumentsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return JSON.readTree(in);
        }
    }
}
