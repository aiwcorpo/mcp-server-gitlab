package pl.aiwcorpo.mcp.gitlab.application.service;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.Issue;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueChange;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.IssueSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.NoteView;
import pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException;
import pl.aiwcorpo.mcp.gitlab.core.port.in.IssueUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.IssuePort;

/** Issue reads and changes of a resolved project. */
public class IssueService implements IssueUseCase {

    private final IssuePort issues;

    public IssueService(IssuePort issues) {
        this.issues = issues;
    }

    @Override
    public BackendPage<IssueSummary> list(GitLabProject project, IssuePort.Filter filter, int page) {
        return issues.list(project.id(), filter, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public Issue get(GitLabProject project, long iid) {
        IssuePort.FoundIssue found = issues.find(project.id(), iid).orElseThrow(ResourceNotFoundException::new);
        if (found.projectId() != project.id()) {
            throw new ResourceNotFoundException();
        }
        return found.issue().inProject(project.pathWithNamespace());
    }

    @Override
    public BackendPage<NoteView> notes(GitLabProject project, long iid, int page) {
        return issues.notes(project.id(), iid, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public IssueChange create(GitLabProject project, String title, @Nullable String description,
            @Nullable List<String> labels) {
        return issues.create(project.id(), title, description, labels).orElseThrow(ResourceNotFoundException::new)
                .inProject(project.pathWithNamespace());
    }

    @Override
    public IssueChange update(GitLabProject project, long iid, @Nullable String title, @Nullable String description,
            @Nullable List<String> labels, @Nullable String state) {
        return issues.update(project.id(), iid, title, description, labels, state)
                .orElseThrow(ResourceNotFoundException::new)
                .inProject(project.pathWithNamespace());
    }

    @Override
    public IssueNote comment(GitLabProject project, long iid, String body) {
        return issues.addNote(project.id(), iid, body).orElseThrow(ResourceNotFoundException::new)
                .inProject(project.pathWithNamespace());
    }
}
