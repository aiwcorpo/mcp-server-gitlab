package pl.aiwcorpo.mcp.gitlab.core.port.out;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.Issue;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;

/** Issues of a project as the calling user; empty means GitLab did not show the thing to the caller. */
public interface IssuePort {

    record Filter(@Nullable String state, @Nullable String author, @Nullable String assignee,
            @Nullable List<String> labels, @Nullable String search) {
    }

    Optional<BackendPage<IssueSummary>> list(long projectId, Filter filter, int page);

    /** The issue and the id of the project GitLab says it belongs to, which the service compares with the resolved one. */
    Optional<FoundIssue> find(long projectId, long iid);

    record FoundIssue(long projectId, Issue issue) {
    }

    Optional<BackendPage<NoteView>> notes(long projectId, long iid, int page);

    Optional<IssueChange> create(long projectId, String title, @Nullable String description,
            @Nullable List<String> labels);

    /** {@code state} is {@code opened} or {@code closed}; {@code labels} replaces the whole set. */
    Optional<IssueChange> update(long projectId, long iid, @Nullable String title, @Nullable String description,
            @Nullable List<String> labels, @Nullable String state);

    Optional<IssueNote> addNote(long projectId, long iid, String body);
}
