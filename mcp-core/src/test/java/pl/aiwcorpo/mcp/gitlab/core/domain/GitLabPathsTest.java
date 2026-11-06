package pl.aiwcorpo.mcp.gitlab.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The limits of GitLab that the {@code idPattern} declarations cannot express (CC-237): the depth of subgroups and the
 * reserved endings. The pattern part is covered by the contract vectors in the tools module.
 */
class GitLabPathsTest {

    private static String levels(int count) {
        return String.join("/", java.util.Collections.nCopies(count, "g"));
    }

    @Test
    void aGroupNestsUpToTwentyLevels() {
        assertThat(GitLabPaths.isGroup(levels(1))).isTrue();
        assertThat(GitLabPaths.isGroup(levels(20))).isTrue();
        assertThat(GitLabPaths.isGroup(levels(21))).isFalse();
        assertThat(GitLabPaths.isGroup(levels(200))).isFalse();
    }

    @Test
    void aProjectIsOneSegmentBelowTwentyLevelsOfGroups() {
        assertThat(GitLabPaths.isProject(levels(2))).isTrue();
        assertThat(GitLabPaths.isProject(levels(21))).isTrue();
        assertThat(GitLabPaths.isProject(levels(22))).isFalse();
    }

    @Test
    void aSegmentDoesNotEndInGitOrAtom() {
        for (String reserved : new String[] {"a.git", "a.atom", "team-a/app.git", "team-a/app.atom", "team.git/app",
                "team-a/sub.atom/app", "team-a/x.git/y"}) {
            assertThat(GitLabPaths.isProject(reserved)).as(reserved).isFalse();
        }
        for (String reserved : new String[] {"team.git", "team-a/sub.atom", "x.git/y", "a.atom/b/c"}) {
            assertThat(GitLabPaths.isGroup(reserved)).as(reserved).isFalse();
        }
    }

    @Test
    void aSegmentThatOnlyContainsGitOrAtomIsFine() {
        for (String ok : new String[] {"team/git", "team/atom", "team/app.github", "team/my.gitops", "git/atom", "team/a.git.b"}) {
            assertThat(GitLabPaths.isProject(ok)).as(ok).isTrue();
        }
        assertThat(GitLabPaths.isGroup("git")).isTrue();
        assertThat(GitLabPaths.isGroup("my.atoms")).isTrue();
    }

    @Test
    void theShapeOfThePatternStillApplies() {
        assertThat(GitLabPaths.isGroup(null)).isFalse();
        assertThat(GitLabPaths.isGroup("team-a/")).isFalse();
        assertThat(GitLabPaths.isGroup("team--a")).isFalse();
        assertThat(GitLabPaths.isProject("app")).as("a project has a group").isFalse();
        assertThat(GitLabPaths.isProject("team-a/../x")).isFalse();
        assertThat(GitLabPaths.isGroup("a".repeat(GitLabPaths.MAX_LENGTH + 1))).isFalse();
        assertThat(GitLabPaths.isGroup("a".repeat(GitLabPaths.MAX_LENGTH))).isTrue();
    }
}
