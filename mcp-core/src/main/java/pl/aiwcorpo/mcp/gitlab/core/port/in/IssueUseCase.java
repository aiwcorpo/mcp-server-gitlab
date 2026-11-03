package pl.aiwcorpo.mcp.gitlab.core.port.in;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.Issue;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.port.out.IssuePort;

/** Issues of a resolved project. */
public interface IssueUseCase {

    BackendPage<IssueSummary> list(GitLabProject project, IssuePort.Filter filter, int page);

    Issue get(GitLabProject project, long iid);

    BackendPage<NoteView> notes(GitLabProject project, long iid, int page);

    IssueChange create(GitLabProject project, String title, @Nullable String description, @Nullable List<String> labels);

    IssueChange update(GitLabProject project, long iid, @Nullable String title, @Nullable String description,
            @Nullable List<String> labels, @Nullable String state);

    IssueNote comment(GitLabProject project, long iid, String body);
}
