package pl.aiwcorpo.mcp.gitlab.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileMetadata;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;

class GitLabResolversTest {

    private final FakeGitLab gitlab = new FakeGitLab();
    private final GitLabGroupResolver groups = new GitLabGroupResolver(gitlab);
    private final GitLabProjectResolver projects = new GitLabProjectResolver(gitlab);

    private static String levels(int count) {
        return String.join("/", java.util.Collections.nCopies(count, "g"));
    }

    @Test
    void aGroupCarriesTheScopeOfEveryPathPrefixAndNothingForASiblingThatSharesTheStart() {
        ResolvedResource sub = groups.describe(groups.fetch("team-a/sub").orElseThrow());
        ResolvedResource sibling = groups.describe(groups.fetch("team-ab").orElseThrow());

        assertThat(sub.id()).isEqualTo("team-a/sub");
        assertThat(sub.scopes()).containsExactlyInAnyOrder("GitLab::Group/team-a", "GitLab::Group/team-a/sub");
        assertThat(sibling.scopes()).containsExactly("GitLab::Group/team-ab");
    }

    @Test
    void aProjectAndItsGroupCarryTheScopesOfTheGroupsOnThePath() {
        ResolvedResource project = projects.describe(projects.fetch("team-a/sub/svc").orElseThrow());

        assertThat(project.id()).isEqualTo("team-a/sub/svc");
        assertThat(project.scopes()).containsExactlyInAnyOrder("GitLab::Group/team-a", "GitLab::Group/team-a/sub");
        ResolvedResource group = project.parent().orElseThrow();
        assertThat(group.id()).isEqualTo("team-a/sub");
        assertThat(group.scopes()).isEqualTo(project.scopes());
    }

    @Test
    void theIdAndTheParentComeFromGitLabNotFromTheArgument() {
        ResolvedResource project = projects.describe(projects.fetch("TEAM-A/App").orElseThrow());

        assertThat(project.id()).isEqualTo("team-a/app");
        assertThat(project.parent().orElseThrow().id()).isEqualTo("team-a");
        assertThat(project.scopes()).containsExactly("GitLab::Group/team-a");
    }

    @Test
    void theTopicsOfAProjectAreItsClassificationAndNothingIsInherited() {
        ResolvedResource project = projects.describe(projects.fetch("team-a/secret-plans").orElseThrow());

        assertThat(project.attributes()).containsEntry("classification", List.of("Restricted"));
        assertThat(project.attributes()).containsEntry("inheritedClassification", List.of());
        assertThat(project.parent().orElseThrow().attributes()).containsEntry("inheritedClassification", List.of());
    }

    @Test
    void theProbeOfAProjectNamesTheGroupOfThePathAndAsksGitLabNothing() {
        ResolvedResource probe = projects.probe("team-a/sub/svc").orElseThrow();

        assertThat(probe.id()).isEqualTo("team-a/sub/svc");
        assertThat(probe.scopes()).containsExactlyInAnyOrder("GitLab::Group/team-a", "GitLab::Group/team-a/sub");
        assertThat(probe.parent().orElseThrow().id()).isEqualTo("team-a/sub");
        assertThat(probe.attributes()).doesNotContainKey("classification").containsEntry("inheritedClassification", List.of());
        ResolvedResource group = groups.probe("team-ab").orElseThrow();
        assertThat(group.scopes()).containsExactly("GitLab::Group/team-ab");
        assertThat(gitlab.lookups).isEmpty();
    }

    @Test
    void aPathGitLabCannotHaveHasNoProbeAndIsRefusedAsAnInvalidArgumentBeforeGitLabIsAsked() {
        for (String tooDeep : List.of(levels(21))) {
            assertThat(groups.probe(tooDeep)).isEmpty();
            assertThatThrownBy(() -> groups.fetch(tooDeep)).isInstanceOf(InvalidToolArgumentException.class);
        }
        for (String project : List.of(levels(22), "team-a/app.git", "team-a/x.atom/app", "team.git/app")) {
            assertThat(projects.probe(project)).as(project).isEmpty();
            assertThatThrownBy(() -> projects.fetch(project)).as(project).isInstanceOf(InvalidToolArgumentException.class);
        }
        assertThatThrownBy(() -> groups.fetch("team-a/sub.git")).isInstanceOf(InvalidToolArgumentException.class);
        assertThat(gitlab.lookups).isEmpty();
    }

    @Test
    void theLimitsAreInclusive() {
        assertThat(groups.probe(levels(20))).isPresent();
        assertThat(projects.probe(levels(21))).isPresent();
        assertThat(projects.fetch(levels(21))).isPresent();
        assertThat(gitlab.lookups).containsExactly("project:" + levels(21));
    }

    @Test
    void aListingSkipsAnElementWithAPathGitLabCannotHave() {
        Map<String, ?> fetched = projects.fetchAll(List.of("team-a/app", "team-a/app.git", "team-a/lib"));

        assertThat(fetched).containsOnlyKeys("team-a/app", "team-a/lib");
    }

    private static final class FakeGitLab implements GitLabPort {

        final List<String> lookups = new ArrayList<>();

        @Override
        public Optional<GitLabGroup> findGroup(String fullPath) {
            lookups.add("group:" + fullPath);
            return Optional.of(new GitLabGroup(1, fullPath.toLowerCase(java.util.Locale.ROOT), List.of()));
        }

        @Override
        public Optional<GitLabProject> findProject(String pathWithNamespace) {
            lookups.add("project:" + pathWithNamespace);
            List<String> topics = pathWithNamespace.endsWith("/secret-plans") ? List.of("Restricted") : List.of();
            return Optional.of(new GitLabProject(7, pathWithNamespace.toLowerCase(java.util.Locale.ROOT), "main", topics));
        }

        @Override
        public ProjectPage listGroupProjects(long groupId, int pageSize, int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProjectPage listedGroupProjects(long groupId, int pageSize) {
            return new ProjectPage(List.of(), List.of(), List.of(), null);
        }

        @Override
        public Optional<FileMetadata> fileMetadata(long projectId, String filePath, String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<byte[]> fileContent(long projectId, String filePath, String commitId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<FoundMergeRequest> findMergeRequest(long projectId, long iid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<MergeRequestNote> addMergeRequestNote(long projectId, long iid, String body) {
            throw new UnsupportedOperationException();
        }
    }
}
