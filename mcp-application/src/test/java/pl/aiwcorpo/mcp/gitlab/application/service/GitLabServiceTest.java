package pl.aiwcorpo.mcp.gitlab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileMetadata;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileNotReturnedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;

class GitLabServiceTest {

    private static final long GROUP_ID = 7;
    private static final long PROJECT_ID = 42;

    private final FakeGitLab gitlab = new FakeGitLab();
    private final GitLabService service = new GitLabService(gitlab, projects -> projects, 16);

    @Test
    void listsOnlyProjectsWhoseNamespaceIsTheResolvedGroup() {
        var list = service.listProjects("TEAM-A");

        assertThat(list.group()).as("canonical path from GitLab, not the argument").isEqualTo("team-a");
        assertThat(list.projects()).extracting(ProjectSummary::path).containsExactly("team-a/app");
        assertThat(list.truncated()).isTrue();
    }

    @Test
    void theResultFilterSeesEveryListing() {
        GitLabService filtering = new GitLabService(gitlab, projects -> List.of(), 16);

        assertThat(filtering.listProjects("team-a").projects()).isEmpty();
    }

    @Test
    void readsTheFileAtTheCommitTheRefPointedTo() {
        var file = service.getFile("Team-A/App", "README.md", null);

        assertThat(file.project()).isEqualTo("team-a/app");
        assertThat(file.ref()).as("default branch when no ref is given").isEqualTo("main");
        assertThat(gitlab.contentReadAt).containsExactly("c0ffee");
        assertThat(file.content().text()).isEqualTo("hello");
        assertThat(file.content().kind()).isEqualTo("untrusted-data");
    }

    @Test
    void aFileOverTheLimitIsNotReadAtAll() {
        assertThatThrownBy(() -> service.getFile("team-a/app", "big.txt", "main"))
                .isInstanceOf(FileNotReturnedException.class)
                .extracting(ex -> ((FileNotReturnedException) ex).reason())
                .isEqualTo(FileNotReturnedException.Reason.TOO_LARGE);
        assertThat(gitlab.contentReadAt).isEmpty();
    }

    @Test
    void contentLargerThanAnnouncedIsNotReturned() {
        assertThatThrownBy(() -> service.getFile("team-a/app", "grew.txt", "main"))
                .isInstanceOf(FileNotReturnedException.class);
    }

    @Test
    void binaryContentIsNotReturned() {
        for (String path : new String[] {"nul.bin", "latin1.txt"}) {
            assertThatThrownBy(() -> service.getFile("team-a/app", path, "main"))
                    .isInstanceOf(FileNotReturnedException.class)
                    .extracting(ex -> ((FileNotReturnedException) ex).reason())
                    .isEqualTo(FileNotReturnedException.Reason.BINARY);
        }
    }

    @Test
    void anEmptyRepositoryHasNoDefaultFile() {
        assertThatThrownBy(() -> service.getFile("team-a/empty", "README.md", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aMergeRequestOfAnotherProjectIsNotFound() {
        assertThat(service.getMergeRequest("team-a/app", 1).project()).isEqualTo("team-a/app");
        assertThatThrownBy(() -> service.getMergeRequest("team-a/app", 2)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unknownPathsAreNotFound() {
        assertThatThrownBy(() -> service.listProjects("nope")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.commentMergeRequest("nope/app", 1, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** GitLab in memory: paths are looked up case-insensitively, as GitLab does. */
    private static final class FakeGitLab implements GitLabPort {

        final List<String> contentReadAt = new ArrayList<>();

        @Override
        public Optional<GitLabGroup> findGroup(String fullPath) {
            return "team-a".equalsIgnoreCase(fullPath) ? Optional.of(new GitLabGroup(GROUP_ID, "team-a")) : Optional.empty();
        }

        @Override
        public Optional<GitLabProject> findProject(String path) {
            if ("team-a/app".equalsIgnoreCase(path)) {
                return Optional.of(new GitLabProject(PROJECT_ID, "team-a/app", "main"));
            }
            if ("team-a/empty".equalsIgnoreCase(path)) {
                return Optional.of(new GitLabProject(43, "team-a/empty", null));
            }
            return Optional.empty();
        }

        @Override
        public ProjectPage listGroupProjects(long groupId, int pageSize) {
            return new ProjectPage(List.of(summary("team-a/app"), summary("other/shared")), List.of(GROUP_ID, 99L), true);
        }

        @Override
        public Optional<FileMetadata> fileMetadata(long projectId, String filePath, String ref) {
            long size = switch (filePath) {
                case "big.txt" -> 17;
                default -> 5;
            };
            return Optional.of(new FileMetadata(filePath, "c0ffee", size));
        }

        @Override
        public Optional<byte[]> fileContent(long projectId, String filePath, String commitId) {
            contentReadAt.add(commitId);
            return Optional.of(switch (filePath) {
                case "grew.txt" -> "x".repeat(17).getBytes(StandardCharsets.UTF_8);
                case "nul.bin" -> new byte[] {'a', 0, 'b'};
                case "latin1.txt" -> new byte[] {'z', (byte) 0xF3, 'b'};
                default -> "hello".getBytes(StandardCharsets.UTF_8);
            });
        }

        @Override
        public Optional<FoundMergeRequest> findMergeRequest(long projectId, long iid) {
            long owner = iid == 1 ? PROJECT_ID : 1000;
            return Optional.of(new FoundMergeRequest(owner, new MergeRequest(null, iid, "t", null, "opened", false, "f", "main", "u",
                    List.of(), List.of(), List.of(), null, null, null, null, false, null)));
        }

        @Override
        public Optional<MergeRequestNote> addMergeRequestNote(long projectId, long iid, String body) {
            return Optional.of(new MergeRequestNote(null, iid, 1, "u", null));
        }

        private static ProjectSummary summary(String path) {
            return new ProjectSummary(path, path, null, "main", false, null, null);
        }
    }
}
