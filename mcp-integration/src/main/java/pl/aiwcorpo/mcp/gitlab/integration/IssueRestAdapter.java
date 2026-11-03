package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.port.out.IssuePort;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.IssueResponse;
import pl.aiwcorpo.mcp.gitlab.integration.GitLabResponses.NoteResponse;

/** Issues. In newer GitLab versions an issue is a work item; the REST endpoints below keep answering for them. */
@Component
public class IssueRestAdapter implements IssuePort {

    static final int LIST_PAGE = 20;
    static final int NOTE_PAGE = 10;

    private static final ParameterizedTypeReference<List<IssueResponse>> ISSUES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<NoteResponse>> NOTES = new ParameterizedTypeReference<>() {
    };

    private final GitLabHttp http;

    public IssueRestAdapter(GitLabHttp http) {
        this.http = http;
    }

    @Override
    public Optional<BackendPage<IssueSummary>> list(long projectId, Filter filter, int page) {
        return http.getPage(ISSUES, new GitLabQuery("/projects/{id}/issues").with("id", projectId)
                        .param("state", filter.state()).param("author_username", filter.author())
                        .param("assignee_username", filter.assignee())
                        .param("labels", filter.labels() == null || filter.labels().isEmpty() ? null : String.join(",", filter.labels()))
                        .param("search", filter.search())
                        .fixed("order_by", "updated_at").fixed("sort", "desc").page(page, LIST_PAGE))
                .map(result -> new BackendPage<>(result.items().stream().filter(issue -> issue.projectId() == projectId)
                        .map(IssueResponse::toSummary).toList(), result.nextPage()));
    }

    @Override
    public Optional<FoundIssue> find(long projectId, long iid) {
        return http.get(IssueResponse.class, new GitLabQuery("/projects/{id}/issues/{iid}").with("id", projectId).with("iid", iid))
                .map(issue -> new FoundIssue(issue.projectId(), issue.toDomain()));
    }

    @Override
    public Optional<BackendPage<NoteView>> notes(long projectId, long iid, int page) {
        return http.getPage(NOTES, new GitLabQuery("/projects/{id}/issues/{iid}/notes").with("id", projectId)
                        .with("iid", iid).fixed("order_by", "created_at").fixed("sort", "asc").page(page, NOTE_PAGE))
                .map(result -> new BackendPage<>(result.items().stream().filter(note -> note.belongsTo(projectId, iid))
                        .map(NoteResponse::toView).toList(), result.nextPage()));
    }

    @Override
    public Optional<IssueChange> create(long projectId, String title, @Nullable String description,
            @Nullable List<String> labels) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        if (description != null) {
            body.put("description", description);
        }
        if (labels != null) {
            body.put("labels", String.join(",", labels));
        }
        return http.send(HttpMethod.POST, IssueResponse.class, body,
                        new GitLabQuery("/projects/{id}/issues").with("id", projectId), false)
                .map(GitLabHttp::bodyOf).map(issue -> GitLabHttp.confirmed(issue, issue.projectId() == projectId))
                .map(IssueResponse::toChange);
    }

    @Override
    public Optional<IssueChange> update(long projectId, long iid, @Nullable String title, @Nullable String description,
            @Nullable List<String> labels, @Nullable String state) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (title != null) {
            body.put("title", title);
        }
        if (description != null) {
            body.put("description", description);
        }
        if (labels != null) {
            body.put("labels", String.join(",", labels));
        }
        if (state != null) {
            body.put("state_event", "closed".equals(state) ? "close" : "reopen");
        }
        return http.send(HttpMethod.PUT, IssueResponse.class, body,
                        new GitLabQuery("/projects/{id}/issues/{iid}").with("id", projectId).with("iid", iid), false)
                .map(GitLabHttp::bodyOf).map(issue -> GitLabHttp.confirmed(issue, issue.projectId() == projectId))
                .map(IssueResponse::toChange);
    }

    @Override
    public Optional<IssueNote> addNote(long projectId, long iid, String body) {
        return http.send(HttpMethod.POST, NoteResponse.class, Map.of("body", body),
                        new GitLabQuery("/projects/{id}/issues/{iid}/notes").with("id", projectId).with("iid", iid), false)
                .map(GitLabHttp::bodyOf)
                .map(note -> GitLabHttp.confirmed(note, note.belongsTo(projectId, iid)))
                .map(note -> new IssueNote(null, iid, note.id(),
                        note.author() == null ? null : note.author().username(), note.createdAt()));
    }
}
