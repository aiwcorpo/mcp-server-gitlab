package pl.aiwcorpo.mcp.gitlab.application.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import pl.aiwcorpo.mcp.gitlab.core.domain.ArtifactFile;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendRefusedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.CurrentUser;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileMetadata;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileNotReturnedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.RepositoryFile;
import pl.aiwcorpo.mcp.gitlab.core.domain.ResourceNotFoundException;
import pl.aiwcorpo.mcp.gitlab.core.domain.SystemText;
import pl.aiwcorpo.mcp.gitlab.core.port.in.GitLabUseCase;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;

/**
 * Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring.
 *
 * <p>The group or project comes resolved: the starter's resolver asked GitLab with the caller's credential and the
 * resource policy accepted the answer. The numeric id addresses every call here. Comparing path strings would get
 * case (GitLab paths are case-insensitive) and moved projects (GitLab follows the old path) wrong.
 */
public class GitLabService implements GitLabUseCase {

    private static final int REF_MAX_CHARS = 255;

    private final GitLabPort gitlab;
    private final long maxFileBytes;

    public GitLabService(GitLabPort gitlab, long maxFileBytes) {
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        this.gitlab = gitlab;
        this.maxFileBytes = maxFileBytes;
    }

    @Override
    public BackendPage<ProjectSummary> listProjects(GitLabGroup group, int pageNumber) {
        ProjectPage page = gitlab.listGroupProjects(group.id(), GitLabPort.PAGE_SIZE, pageNumber);
        List<ProjectSummary> inGroup = new ArrayList<>();
        for (int i = 0; i < page.projects().size(); i++) {
            // with_shared=false already keeps projects of other namespaces out; this still holds if GitLab
            // changes that default or something on the way drops the parameter.
            if (page.namespaceIds().get(i) == group.id()) {
                inGroup.add(page.projects().get(i));
            }
        }
        return new BackendPage<>(inGroup, page.nextPage());
    }

    @Override
    public RepositoryFile getFile(GitLabProject project, String filePath, String ref) {
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
        return new RepositoryFile(project.pathWithNamespace(), filePath, SystemText.plainOrEmpty(effectiveRef, REF_MAX_CHARS),
                metadata.commitId(),
                bytes.length, SystemText.hiddenCharacters(text),
                SystemText.plainOrEmpty(text, (int) Math.min(maxFileBytes, Integer.MAX_VALUE)));
    }

    @Override
    public ArtifactFile getJobArtifactFile(GitLabProject project, long jobId, String artifactPath) {
        byte[] bytes;
        try {
            bytes = gitlab.jobArtifact(project.id(), jobId, artifactPath).orElseThrow(ResourceNotFoundException::new);
        } catch (BackendRefusedException refused) {
            if (refused.reason() == BackendRefusedException.Reason.RESPONSE_TOO_LARGE) {
                throw new FileNotReturnedException(FileNotReturnedException.Reason.TOO_LARGE, maxFileBytes);
            }
            throw refused;
        }
        if (bytes.length > maxFileBytes) {
            throw new FileNotReturnedException(FileNotReturnedException.Reason.TOO_LARGE, maxFileBytes);
        }
        String text = utf8Text(bytes);
        return new ArtifactFile(project.pathWithNamespace(), jobId, artifactPath, bytes.length,
                SystemText.hiddenCharacters(text),
                SystemText.plainOrEmpty(text, (int) Math.min(maxFileBytes, Integer.MAX_VALUE)));
    }

    @Override
    public CurrentUser currentUser() {
        return gitlab.currentUser().orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public MergeRequest getMergeRequest(GitLabProject project, long iid) {
        FoundMergeRequest found = gitlab.findMergeRequest(project.id(), iid)
                .orElseThrow(ResourceNotFoundException::new);
        if (found.projectId() != project.id()) {
            throw new ResourceNotFoundException();
        }
        return found.mergeRequest().inProject(project.pathWithNamespace());
    }

    @Override
    public MergeRequestNote commentMergeRequest(GitLabProject project, long iid, String body) {
        return gitlab.addMergeRequestNote(project.id(), iid, body)
                .orElseThrow(ResourceNotFoundException::new)
                .inProject(project.pathWithNamespace());
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
