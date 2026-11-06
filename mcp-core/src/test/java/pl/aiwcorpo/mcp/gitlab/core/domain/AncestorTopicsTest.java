package pl.aiwcorpo.mcp.gitlab.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AncestorTopicsTest {

    @Test
    void theInheritedClassificationIsTheNormalizedUnionOfEveryAncestor() {
        AncestorTopics topics = new AncestorTopics(Map.of(
                "hr", List.of(" Restricted "),
                "hr/payroll", List.of("internal", "RESTRICTED"),
                "hr/payroll/team", List.of()));

        assertThat(topics.inheritedClassification()).containsExactly("internal", "restricted");
    }

    @Test
    void noAncestorTopicsInheritNothing() {
        assertThat(new AncestorTopics(Map.of("hr", List.of(), "hr/payroll", List.of())).inheritedClassification()).isEmpty();
        assertThat(new AncestorTopics(Map.of()).inheritedClassification()).isEmpty();
    }

    @Test
    void theParentGroupOfAProjectDoesNotInheritItsOwnTopics() {
        AncestorTopics topics = new AncestorTopics(Map.of("hr", List.of("restricted"), "hr/payroll", List.of("internal")));

        assertThat(topics.without("hr/payroll").inheritedClassification()).containsExactly("restricted");
    }
}
