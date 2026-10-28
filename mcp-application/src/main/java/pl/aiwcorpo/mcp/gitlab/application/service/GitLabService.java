package pl.aiwcorpo.mcp.gitlab.application.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileMetadata;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileNotReturnedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectList;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.RepositoryFile;
import pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException;
import pl.aiwcorpo.mcp.gitlab.core.domain.SystemText;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.gitlab.core.port.out.ProjectResultFilter;

/**
 * Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring.
 *
 * <p>Paths from the caller are only ever used for the first lookup; GitLab answers with the numeric id and
 * the canonical path, and the id addresses every later call. Comparing path strings here would get case
 * (GitLab paths are case-insensitive) and moved projects (GitLab follows the old path) wrong.
 */
public class GitLabService implements GitLabUseCase {

    /** GitLab's own maximum for {@code per_page}. */
    static final int PAGE_SIZE = 100;

    private final GitLabPort gitlab;
    private final ProjectResultFilter resultFilter;
    private final long maxFileBytes;

    public GitLabService(GitLabPort gitlab, ProjectResultFilter resultFilter, long maxFileBytes) {
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        this.gitlab = gitlab;
        this.resultFilter = resultFilter;
        this.maxFileBytes = maxFileBytes;
    }

    @Override
    public ProjectList listProjects(String groupPath) {
        GitLabGroup group = gitlab.findGroup(groupPath).orElseThrow(ResourceNotFoundException::new);
        ProjectPage page = gitlab.listGroupProjects(group.id(), PAGE_SIZE);
        List<ProjectSummary> inGroup = new ArrayList<>();
        for (int i = 0; i < page.projects().size(); i++) {
            // with_shared=false already keeps projects of other namespaces out; this still holds if GitLab
            // changes that default or something on the way drops the parameter.
            if (page.namespaceIds().get(i) == group.id()) {
                inGroup.add(page.projects().get(i));
            }
        }
        return new ProjectList(group.fullPath(), resultFilter.retainAllowed(inGroup), page.hasMore());
    }

    @Override
    public RepositoryFile getFile(String projectPath, String filePath, String ref) {
        GitLabProject project = project(projectPath);
        String effectiveRef = ref != null ? ref : project.defaultBranch();
        if (effectiveRef == null) {
            throw new ResourceNotFoundException();
        }
        FileMetadata metadata = gitlab.fileMetadata(project.id(), filePath, effectiveRef)
                .orElseThrow(ResourceNotFoundException::new);
        if (metadata.size() > maxFileBytes) {
            throw new FileNotReturnedException(FileNotReturnedException.Reason.TOO_LARGE, maxFileBytes);
        }
        byte[] bytes = gitlab.fileContent(project.id(), filePath, metadata.commitId())
                .orElseThrow(ResourceNotFoundException::new);
        if (bytes.length > maxFileBytes) {
            throw new FileNotReturnedException(FileNotReturnedException.Reason.TOO_LARGE, maxFileBytes);
        }
        String text = utf8Text(bytes);
        return new RepositoryFile(project.pathWithNamespace(), metadata.filePath(), effectiveRef, metadata.commitId(),
                bytes.length, SystemText.hiddenCharacters(text),
                SystemText.plainOrEmpty(text, (int) Math.min(maxFileBytes, Integer.MAX_VALUE)));
    }

    @Override
    public MergeRequest getMergeRequest(String projectPath, long iid) {
        GitLabProject project = project(projectPath);
        FoundMergeRequest found = gitlab.findMergeRequest(project.id(), iid)
                .orElseThrow(ResourceNotFoundException::new);
        if (found.projectId() != project.id()) {
            throw new ResourceNotFoundException();
        }
        return found.mergeRequest().inProject(project.pathWithNamespace());
    }

    @Override
    public MergeRequestNote commentMergeRequest(String projectPath, long iid, String body) {
        GitLabProject project = project(projectPath);
        return gitlab.addMergeRequestNote(project.id(), iid, body)
                .orElseThrow(ResourceNotFoundException::new)
                .inProject(project.pathWithNamespace());
    }

    private GitLabProject project(String projectPath) {
        return gitlab.findProject(projectPath).orElseThrow(ResourceNotFoundException::new);
    }

    /** UTF-8 text without NUL bytes; anything else counts as binary and is not returned. */
    private String utf8Text(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                throw new FileNotReturnedException(FileNotReturnedException.Reason.BINARY, maxFileBytes);
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notText) {
            throw new FileNotReturnedException(FileNotReturnedException.Reason.BINARY, maxFileBytes);
        }
    }
}
