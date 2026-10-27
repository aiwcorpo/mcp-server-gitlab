package pl.aiwcorpo.mcp.gitlab.core.port.out;

import java.util.List;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectSummary;

/**
 * The single place where a project listing is narrowed to what the caller's policy allows, after GitLab's
 * own permissions. With starter 3.0.0-alpha the gateway decides per call and no resource policy reaches the
 * server, so the implementation in mcp-app passes the list through; the starter beta's {@code ResultFilter}
 * (and the {@code QueryScope} of the link) plugs in here.
 */
public interface ProjectResultFilter {

    List<ProjectSummary> retainAllowed(List<ProjectSummary> projects);
}
