package pl.aiwcorpo.mcp.gitlab.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.cedar.entities.Scopes;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabPaths;
import pl.aiwcorpo.mcp.platform.test.PolicyDecisions;
import pl.aiwcorpo.mcp.platform.test.PolicyDecisions.Decision;

/**
 * The decisions of the policies in {@code mcp/policies/} on groups and projects, without Spring and without the gateway:
 * who may read, change and delete what, given the scope mapping an administrator would set. The rules for restricted
 * data and for the service account come from the platform, generated from the declarations; only the restriction
 * inherited from a group above is a rule of this server.
 */
class GitLabPolicyDecisionsTest {

    private static final String READ = "mcp-gitlab-read";
    private static final String WRITE = "mcp-gitlab-write";
    private static final String DESTRUCTIVE = "mcp-gitlab-destructive";
    private static final String TEAM_A = "mcp-gitlab-grp-team-a";
    private static final String SUB = "mcp-gitlab-grp-team-a-sub";

    private final PolicyDecisions policies = PolicyDecisions.fromResources().scopeMapping(Map.of(
            TEAM_A, List.of("GitLab::Group/team-a"),
            SUB, List.of("GitLab::Group/team-a/sub")));

    private static List<String> scopesOfGroup(String groupPath) {
        List<String> scopes = new ArrayList<>();
        Scopes.pathPrefixes(groupPath).forEach(prefix -> scopes.add(Scopes.id("GitLab::Group", prefix)));
        return scopes;
    }

    private static String parentOf(String path) {
        return path.substring(0, path.lastIndexOf('/'));
    }

    private static ResolvedResource group(String path, List<String> topics, List<String> inherited) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("path", path);
        attributes.put("classification", topics);
        attributes.put("inheritedClassification", inherited);
        return ResolvedResource.of("GitLab::Group", path, attributes).withScopes(scopesOfGroup(path));
    }

    private static ResolvedResource group(String path) {
        return group(path, List.of(), List.of());
    }

    private static ResolvedResource project(String path, List<String> topics, List<String> inherited, ResolvedResource group) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("path", path);
        attributes.put("classification", topics);
        attributes.put("inheritedClassification", inherited);
        return ResolvedResource.of("GitLab::Project", path, attributes).withParent(group).withScopes(group.scopes());
    }

    private static ResolvedResource project(String path) {
        return project(path, List.of(), List.of(), group(parentOf(path)));
    }

    @Test
    void aReaderReadsGroupsAndProjectsOutsideAnyScope() {
        policies.assertAllowed(PolicyDecisions.caller(READ), "read", project("team-a/app"));
        policies.assertAllowed(PolicyDecisions.caller(READ), "read", project("team-b/app"));
        policies.assertAllowed(PolicyDecisions.caller(READ), "read", project("team-z/app"));
        policies.assertAllowed(PolicyDecisions.caller(READ), "read", group("team-b"));
        Decision decision = policies.decide(PolicyDecisions.caller(READ), "read", project("team-a/sub/svc"));
        assertThat(decision.determiningPolicies()).containsExactly("gitlab-read");
    }

    @Test
    void aCallerWithoutTheReadGroupReadsNothing() {
        policies.assertDenied(PolicyDecisions.caller(), "read", project("team-a/app"));
        policies.assertDenied(PolicyDecisions.caller(WRITE, TEAM_A), "read", project("team-a/app"));
        policies.assertDenied(PolicyDecisions.caller(TEAM_A), "read", group("team-a"));
    }

    @Test
    void aProjectIsUpdatedInTheScopeOfAnyGroupAboveIt() {
        Decision decision = policies.decide(PolicyDecisions.caller(WRITE, TEAM_A), "update", project("team-a/app"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.determiningPolicies()).containsExactly("gitlab-update-project-in-scope");
        policies.assertAllowed(PolicyDecisions.caller(WRITE, TEAM_A), "update", project("team-a/sub/svc"));
        policies.assertAllowed(PolicyDecisions.caller(WRITE, TEAM_A), "update", project("team-a/sub/deep/app"));
    }

    @Test
    void aGroupWhoseNameOnlyStartsWithTheGrantedOneIsOutsideTheScope() {
        policies.assertDenied(PolicyDecisions.caller(WRITE, TEAM_A), "update", project("team-ab/app"));
        policies.assertDenied(PolicyDecisions.caller(WRITE, TEAM_A), "update", project("team-b/app"));
        policies.assertDenied(PolicyDecisions.caller(WRITE, TEAM_A), "update", group("team-ab"));
    }

    @Test
    void theScopeWithoutTheWriteGroupChangesNothing() {
        policies.assertDenied(PolicyDecisions.caller(READ, TEAM_A), "update", project("team-a/app"));
        policies.assertDenied(PolicyDecisions.caller(WRITE), "update", project("team-a/app"));
    }

    @Test
    void aGrantOnASubgroupDoesNotCoverTheGroupAboveIt() {
        policies.assertAllowed(PolicyDecisions.caller(WRITE, SUB), "update", project("team-a/sub/svc"));
        policies.assertAllowed(PolicyDecisions.caller(WRITE, SUB), "update", group("team-a/sub/deep"));
        policies.assertDenied(PolicyDecisions.caller(WRITE, SUB), "update", project("team-a/app"));
        policies.assertDenied(PolicyDecisions.caller(WRITE, SUB), "update", group("team-a"));
    }

    @Test
    void creatingInAGroupIsAnUpdateOfThatGroup() {
        Decision decision = policies.decide(PolicyDecisions.caller(WRITE, TEAM_A), "update", group("team-a/sub"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.determiningPolicies()).containsExactly("gitlab-update-group-in-scope");
        policies.assertAllowed(PolicyDecisions.caller(WRITE, TEAM_A), "update", group("team-a/sub/deep"));
        policies.assertDenied(PolicyDecisions.caller(WRITE, TEAM_A), "update", group("team-b"));
    }

    @Test
    void aProjectIsDeletedInTheScopeWithTheDestructiveGroup() {
        Decision decision = policies.decide(PolicyDecisions.caller(DESTRUCTIVE, TEAM_A), "delete", project("team-a/app"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.determiningPolicies()).containsExactly("gitlab-delete-project-in-scope");
    }

    @Test
    void aDeleteNeedsTheDestructiveGroupAndTheScope() {
        policies.assertDenied(PolicyDecisions.caller(READ, WRITE, TEAM_A), "delete", project("team-a/app"));
        policies.assertDenied(PolicyDecisions.caller(DESTRUCTIVE, TEAM_A), "delete", project("team-b/app"));
        policies.assertDenied(PolicyDecisions.caller(DESTRUCTIVE), "delete", project("team-a/app"));
    }

    @Test
    void aGroupIsNeverDeleted() {
        policies.assertDenied(PolicyDecisions.caller(READ, WRITE, DESTRUCTIVE, TEAM_A), "delete", group("team-a/sub"));
    }

    @Test
    void aGroupOrProjectClassifiedRestrictedIsDeniedByThePlatformRuleWhatEverTheGroups() {
        ResolvedResource restrictedGroup = group("team-a", List.of("restricted"), List.of());
        ResolvedResource restrictedProject = project("team-a/app", List.of("restricted"), List.of(), group("team-a"));
        ResolvedResource underRestrictedParent = project("team-a/app", List.of(), List.of(), restrictedGroup);

        for (ResolvedResource resource : List.of(restrictedGroup, restrictedProject, underRestrictedParent)) {
            for (String action : List.of("read", "update", "delete")) {
                Decision decision = policies.decide(PolicyDecisions.caller(READ, WRITE, DESTRUCTIVE, TEAM_A), action, resource);

                assertThat(decision.allowed()).as(resource.type() + " " + action).isFalse();
                assertThat(decision.determiningPolicies()).as(resource.type() + " " + action)
                        .isNotEmpty().allMatch(id -> id.startsWith("platform-"));
            }
        }
    }

    @Test
    void theTopicsAreNormalizedBeforeThePlatformRuleLooksAtThem() {
        policies.assertDenied(PolicyDecisions.caller(READ), "read",
                project("team-a/app", List.of(" Restricted"), List.of(), group("team-a")));
        policies.assertDenied(PolicyDecisions.caller(READ), "read", group("team-a", List.of("RESTRICTED "), List.of()));
    }

    @Test
    void aProjectBelowARestrictedGroupAtAnyDepthIsClosedByTheInheritedClassification() {
        ResolvedResource payroll = group("hr/payroll", List.of(), List.of("restricted"));
        ResolvedResource app = project("hr/payroll/app", List.of(), List.of("restricted"), payroll);

        Decision project = policies.decide(PolicyDecisions.caller(READ), "read", app);
        Decision subgroup = policies.decide(PolicyDecisions.caller(READ), "read", payroll);

        assertThat(project.allowed()).isFalse();
        assertThat(project.determiningPolicies()).containsExactly("gitlab-forbid-restricted-project");
        assertThat(subgroup.allowed()).isFalse();
        assertThat(subgroup.determiningPolicies()).containsExactly("gitlab-forbid-restricted-group");
        policies.assertDenied(PolicyDecisions.caller(READ, WRITE, DESTRUCTIVE, SUB), "update", project("team-a/sub/x/app",
                List.of(), List.of("restricted"), group("team-a/sub/x", List.of(), List.of("restricted"))));
    }

    @Test
    void aRestrictedGroupDoesNotCloseALookAlikeOrAnUnclassifiedProject() {
        policies.assertAllowed(PolicyDecisions.caller(READ), "read", project("hr-eu/app"));
        policies.assertAllowed(PolicyDecisions.caller(READ), "read", project("team-a/app", List.of("internal"), List.of(), group("team-a")));
    }

    @Test
    void aProjectWithoutAClassificationAttributeIsNotRestricted() {
        ResolvedResource owner = ResolvedResource.of("GitLab::Group", "team-a",
                Map.of("path", "team-a", "inheritedClassification", List.of())).withScopes(scopesOfGroup("team-a"));
        ResolvedResource unclassified = ResolvedResource.of("GitLab::Project", "team-a/app",
                        Map.of("path", "team-a/app", "inheritedClassification", List.of()))
                .withParent(owner).withScopes(owner.scopes());

        policies.assertAllowed(PolicyDecisions.caller(READ), "read", unclassified);
    }

    @Test
    void aResourceWithoutTheInheritedClassificationIsDeniedNotOpenedByTheFailingForbid() {
        ResolvedResource owner = group("hr/payroll");
        ResolvedResource project = ResolvedResource.of("GitLab::Project", "hr/payroll/app", Map.of("path", "hr/payroll/app"))
                .withParent(owner).withScopes(owner.scopes());
        ResolvedResource bareGroup = ResolvedResource.of("GitLab::Group", "hr", Map.of("path", "hr"))
                .withScopes(scopesOfGroup("hr"));

        for (ResolvedResource resource : List.of(project, bareGroup)) {
            Decision decision = policies.decide(PolicyDecisions.caller(READ), "read", resource);

            assertThat(decision.allowed()).as(resource.type()).isFalse();
            assertThat(decision.errors()).as(resource.type()).isNotEmpty();
        }
    }

    @Test
    void anIdOutsideTheDeclaredPatternIsRefused() {
        for (String path : List.of("team-a/", "team--a/app", "-team/app", "team a/app")) {
            ResolvedResource owner = group("team-a");
            ResolvedResource invalid = ResolvedResource.of("GitLab::Project", path,
                            Map.of("path", path, "inheritedClassification", List.of()))
                    .withParent(owner).withScopes(owner.scopes());

            assertThatThrownBy(() -> policies.decide(PolicyDecisions.caller(READ), "read", invalid)).as(path)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void theServiceAccountReadsOnlyInTheScopesOfTheUser() {
        PolicyDecisions serviceAccount = PolicyDecisions.fromResources().scopeMapping(Map.of(TEAM_A, List.of("GitLab::Group/team-a")))
                .credentialType("service-account");

        Decision outside = serviceAccount.decide(PolicyDecisions.caller(READ, TEAM_A), "read", project("team-b/app"));
        assertThat(outside.allowed()).isFalse();
        assertThat(outside.determiningPolicies()).isNotEmpty().allMatch(id -> id.startsWith("platform-"));
        serviceAccount.assertAllowed(PolicyDecisions.caller(READ, TEAM_A), "read", project("team-a/app"));
        policies.assertAllowed(PolicyDecisions.caller(READ, TEAM_A), "read", project("team-b/app"));
    }

    @Test
    void anOverrideOfTheAdministratorClosesUpdatesEverywhere() {
        PolicyDecisions frozen = policies.override("override-no-update",
                "forbid (principal, action == GitLab::Action::\"update\", resource);");

        frozen.assertDenied(PolicyDecisions.caller(READ, WRITE, TEAM_A), "update", project("team-a/app"));
        frozen.assertAllowed(PolicyDecisions.caller(READ), "read", project("team-a/app"));
    }

    @Test
    void thePathPatternsOfTheCodeAreTheDeclaredIdPatterns() throws java.io.IOException {
        Map<String, String> declared = new LinkedHashMap<>();
        try (java.io.InputStream in = getClass().getResourceAsStream("/mcp/policies/resource-types.json")) {
            tools.jackson.databind.json.JsonMapper.builder().build().readTree(in)
                    .forEach(type -> declared.put(type.path("type").asString(), type.path("idPattern").asString()));
        }

        assertThat(GitLabPaths.GROUP_ID_PATTERN).isEqualTo(declared.get("GitLab::Group"));
        assertThat(GitLabPaths.PROJECT_ID_PATTERN).isEqualTo(declared.get("GitLab::Project"));
    }
}
