package pl.aiwcorpo.mcp.gitlab.application.service;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.Branch;
import pl.aiwcorpo.mcp.gitlab.core.domain.CodeHit;
import pl.aiwcorpo.mcp.gitlab.core.domain.Commit;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileDiff;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.Label;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectDetails;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectMember;
import pl.aiwcorpo.mcp.gitlab.core.domain.Release;
import pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException;
import pl.aiwcorpo.mcp.gitlab.core.domain.Tag;
import pl.aiwcorpo.mcp.gitlab.core.domain.TreeEntry;
import pl.aiwcorpo.mcp.gitlab.core.port.in.RepositoryUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.RepositoryPort;

/** Reads of a repository. Every call is addressed by the numeric id of the project the starter already resolved. */
public class RepositoryService implements RepositoryUseCase {

    private final RepositoryPort repository;

    public RepositoryService(RepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public ProjectDetails getProject(GitLabProject project) {
        return repository.projectDetails(project.id()).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<TreeEntry> tree(GitLabProject project, @Nullable String path, @Nullable String ref, int page) {
        return repository.tree(project.id(), path, ref, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<CodeHit> searchCode(GitLabProject project, String term, @Nullable String ref, int page) {
        return repository.searchCode(project.id(), term, ref, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<Branch> branches(GitLabProject project, @Nullable String search, int page) {
        return repository.branches(project.id(), search, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<Tag> tags(GitLabProject project, @Nullable String search, int page) {
        return repository.tags(project.id(), search, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<Release> releases(GitLabProject project, int page) {
        return repository.releases(project.id(), page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<ProjectMember> members(GitLabProject project, int page) {
        return repository.members(project.id(), page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<Label> labels(GitLabProject project, @Nullable String search, int page) {
        return repository.labels(project.id(), search, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public Branch createBranch(GitLabProject project, String branch, String ref) {
        return repository.createBranch(project.id(), branch, ref).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<Commit> commits(GitLabProject project, @Nullable String ref, @Nullable String path,
            @Nullable String since, @Nullable String until, int page) {
        return repository.commits(project.id(), ref, path, since, until, page).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public Commit commit(GitLabProject project, String sha) {
        return repository.commit(project.id(), sha).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public BackendPage<FileDiff> commitDiffs(GitLabProject project, String sha, int page) {
        return repository.commitDiffs(project.id(), sha, page).orElseThrow(ResourceNotFoundException::new);
    }
}
