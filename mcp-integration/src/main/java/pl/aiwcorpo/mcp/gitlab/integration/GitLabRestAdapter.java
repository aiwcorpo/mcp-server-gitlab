package pl.aiwcorpo.mcp.gitlab.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import pl.aiwcorpo.mcp.gitlab.core.domain.FileMetadata;
import pl.aiwcorpo.mcp.gitlab.core.domain.FoundMergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequest;
import pl.aiwcorpo.mcp.gitlab.core.domain.MergeRequestNote;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.ProjectSummary;
import pl.aiwcorpo.mcp.gitlab.core.domain.UntrustedText;
import pl.aiwcorpo.mcp.gitlab.core.port.out.GitLabPort;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.security.CredentialContext;
import pl.aiwcorpo.mcp.platform.security.CredentialRequiredException;

/**
 * GitLab REST API v4 as the user: with the credential the gateway resolved for this request (C3), never with
 * an account of the server.
 *
 * <p>Every path segment taken from an argument is percent-encoded as a whole ({@code team-a/app} becomes
 * {@code team-a%2Fapp}, as GitLab expects for {@code :id} and {@code :file_path}), so an argument can never
 * add a path segment or a query parameter.
 *
 * <p>Error mapping matters for security: 404 and 403 both become "not visible" (one client-facing code,
 * {@code not_found_or_denied}), and 401 means the stored credential no longer works, so the user is sent to
 * the portal to reconnect ({@code credential_required}). A failed call becomes an exception with the status
 * alone, an unusable answer (redirect, unparsable or oversized body) a {@link BackendResponseException}, so no
 * response body reaches a message or a log line.
 */
@Component
@EnableConfigurationProperties(GitLabBackendProperties.class)
public class GitLabRestAdapter implements GitLabPort {

    /** System id of this server: {@code server-id} is {@code mcp-<system>}, and the starter refuses any other. */
    static final String SYSTEM = "gitlab";

    private static final String DEV_PROFILE = "dev";
    private static final Logger LOG = LoggerFactory.getLogger(GitLabRestAdapter.class);

    static final int PROJECT_DESCRIPTION_MAX_CHARS = 2_000;
    static final int MERGE_REQUEST_DESCRIPTION_MAX_CHARS = 20_000;

    private static final ParameterizedTypeReference<List<ProjectResponse>> PROJECT_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient client;

    public GitLabRestAdapter(GitLabBackendProperties properties, ObjectProvider<SslBundles> sslBundles,
            Environment environment) {
        DefaultUriBuilderFactory uris = new DefaultUriBuilderFactory(apiRoot(properties.baseUrl(),
                environment.matchesProfiles(DEV_PROFILE) || properties.allowPlainHttp()));
        uris.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        this.client = RestClient.builder()
                .uriBuilderFactory(uris)
                .requestFactory(requestFactory(properties, sslBundles))
                .requestInterceptor(new BoundedBodyInterceptor(properties.maxResponseSize().toBytes()))
                .defaultStatusHandler(status -> status.isError() || status.is3xxRedirection(), (request, response) -> {
                    // Status only: the body and headers of a failed call may echo the credential or carry backend
                    // detail, and the exception message ends up in the starter's tool_failed log line.
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is3xxRedirection()) {
                        // Redirects are not followed (the credential must not go elsewhere) and an empty body
                        // must not read as "not found": an SSO login page would turn into not_found_or_denied.
                        throw new BackendResponseException("redirect " + status.value());
                    }
                    throw status.is4xxClientError()
                            ? HttpClientErrorException.create(status, "", HttpHeaders.EMPTY, new byte[0], null)
                            : HttpServerErrorException.create(status, "", HttpHeaders.EMPTY, new byte[0], null);
                })
                .build();
    }

    @Override
    public Optional<GitLabGroup> findGroup(String fullPath) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        return visible(() -> client.get()
                .uri("/groups/{id}?with_projects=false", fullPath)
                .headers(headers -> GitLabCredentials.apply(credential, headers))
                .retrieve()
                .body(GroupResponse.class))
                .map(group -> new GitLabGroup(group.id(), required(group.fullPath())));
    }

    @Override
    public Optional<GitLabProject> findProject(String pathWithNamespace) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        return visible(() -> client.get()
                .uri("/projects/{id}", pathWithNamespace)
                .headers(headers -> GitLabCredentials.apply(credential, headers))
                .retrieve()
                .body(ProjectResponse.class))
                .map(project -> new GitLabProject(project.id(), required(project.pathWithNamespace()),
                        project.defaultBranch()));
    }

    @Override
    public ProjectPage listGroupProjects(long groupId, int pageSize) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        // A group that resolved a moment ago and now answers 404/403 is treated like an empty one: the caller
        // learns nothing beyond what the lookup already told it.
        Optional<ResponseEntity<List<ProjectResponse>>> page = visible(() -> client.get()
                .uri("/groups/{id}/projects?per_page={size}&page=1&order_by=path&sort=asc"
                        + "&include_subgroups=false&with_shared=false", groupId, pageSize)
                .headers(headers -> GitLabCredentials.apply(credential, headers))
                .retrieve()
                .toEntity(PROJECT_LIST));
        List<ProjectResponse> projects = page.map(ResponseEntity::getBody).orElse(List.of());
        String nextPage = page.map(response -> response.getHeaders().getFirst("X-Next-Page")).orElse(null);
        return new ProjectPage(
                projects.stream().map(ProjectResponse::toSummary).toList(),
                projects.stream().map(ProjectResponse::namespaceId).toList(),
                nextPage != null && !nextPage.isBlank());
    }

    @Override
    public Optional<FileMetadata> fileMetadata(long projectId, String filePath, String ref) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        // HEAD answers with the X-Gitlab-* headers and no content, so a large file is refused before it is read.
        return visible(() -> client.head()
                .uri("/projects/{id}/repository/files/{path}?ref={ref}", projectId, filePath, ref)
                .headers(headers -> GitLabCredentials.apply(credential, headers))
                .retrieve()
                .toBodilessEntity())
                .map(response -> {
                    HttpHeaders headers = response.getHeaders();
                    return new FileMetadata(
                            required(headers.getFirst("X-Gitlab-File-Path")),
                            required(headers.getFirst("X-Gitlab-Commit-Id")),
                            size(headers.getFirst("X-Gitlab-Size")));
                });
    }

    @Override
    public Optional<byte[]> fileContent(long projectId, String filePath, String commitId) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        return visible(() -> {
            byte[] content = client.get()
                    .uri("/projects/{id}/repository/files/{path}/raw?ref={ref}", projectId, filePath, commitId)
                    .headers(headers -> GitLabCredentials.apply(credential, headers))
                    .retrieve()
                    .body(byte[].class);
            // An empty file has no body, which is still a file and not "not found".
            return content == null ? new byte[0] : content;
        });
    }

    @Override
    public Optional<FoundMergeRequest> findMergeRequest(long projectId, long iid) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        return visible(() -> client.get()
                .uri("/projects/{id}/merge_requests/{iid}", projectId, iid)
                .headers(headers -> GitLabCredentials.apply(credential, headers))
                .retrieve()
                .body(MergeRequestResponse.class))
                .map(MergeRequestResponse::toDomain);
    }

    @Override
    public Optional<MergeRequestNote> addMergeRequestNote(long projectId, long iid, String body) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        return visible(() -> client.post()
                .uri("/projects/{id}/merge_requests/{iid}/notes", projectId, iid)
                .headers(headers -> GitLabCredentials.apply(credential, headers))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", body))
                .retrieve()
                .body(NoteResponse.class))
                .map(note -> new MergeRequestNote(null, iid, note.id(), note.author() == null ? null : note.author().username(),
                        note.createdAt()));
    }

    private static <T> Optional<T> visible(Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized expired) {
            throw new CredentialRequiredException(SYSTEM);
        } catch (RestClientResponseException | ResourceAccessException statusOrIo) {
            // Status-only exceptions (see the status handler) and I/O failures: the starter maps 502-504 and
            // unreachable backends to upstream_unavailable.
            throw statusOrIo;
        } catch (RestClientException unreadable) {
            // A 2xx body that does not parse: the converter's message quotes the body.
            throw new BackendResponseException("body not readable");
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new BackendResponseException("field the server relies on is missing");
        }
        return value;
    }

    private static long size(String header) {
        try {
            return Long.parseLong(required(header));
        } catch (NumberFormatException notANumber) {
            throw new BackendResponseException("size header is not a number");
        }
    }

    private static String apiRoot(String baseUrl, boolean plainHttpAllowed) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("gitlab.backend.base-url (MCP_GITLAB_URL) must point at the GitLab instance");
        }
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("gitlab.backend.base-url must be an absolute http(s) URL without query");
        }
        if (!"https".equals(uri.getScheme())) {
            if (!plainHttpAllowed) {
                // The user's credential travels in a header; over http it would cross the network in clear text.
                throw new IllegalStateException("gitlab.backend.base-url must use https outside the 'dev' profile "
                        + "(gitlab.backend.allow-plain-http exists for the mocks of mcp-platform-dev only)");
            }
            LOG.warn("event=backend_plain_http detail=\"credentials go to the backend unencrypted\"");
        }
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return root + "/api/v4";
    }

    private static JdkClientHttpRequestFactory requestFactory(GitLabBackendProperties properties,
            ObjectProvider<SslBundles> sslBundles) {
        // Redirects are not followed: the credential header must not travel to wherever a backend points.
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (properties.sslBundle() != null && !properties.sslBundle().isBlank()) {
            http.sslContext(sslBundles.getObject().getBundle(properties.sslBundle()).createSslContext());
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    // GitLab's representations; unknown fields are dropped here and never reach the model. Flags are boxed
    // because GitLab leaves out fields a representation does not have (e.g. archived on a single project).

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroupResponse(long id, @JsonProperty("full_path") String fullPath) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NamespaceResponse(long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProjectResponse(long id, String name, @JsonProperty("path_with_namespace") String pathWithNamespace,
            String description, @JsonProperty("default_branch") String defaultBranch, Boolean archived,
            @JsonProperty("last_activity_at") String lastActivityAt, @JsonProperty("web_url") String webUrl,
            NamespaceResponse namespace) {

        ProjectSummary toSummary() {
            return new ProjectSummary(pathWithNamespace, name,
                    UntrustedText.of("gitlab-project-description", emptyToNull(description), PROJECT_DESCRIPTION_MAX_CHARS),
                    defaultBranch, Boolean.TRUE.equals(archived), lastActivityAt, webUrl);
        }

        /** -1 when GitLab sent no namespace, which no group id matches. */
        long namespaceId() {
            return namespace == null ? -1 : namespace.id();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserResponse(String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MergeRequestResponse(long iid, @JsonProperty("project_id") long projectId, String title,
            String description, String state, Boolean draft, @JsonProperty("source_branch") String sourceBranch,
            @JsonProperty("target_branch") String targetBranch, UserResponse author, List<UserResponse> assignees,
            List<UserResponse> reviewers, List<String> labels, @JsonProperty("created_at") String createdAt,
            @JsonProperty("updated_at") String updatedAt, @JsonProperty("merged_at") String mergedAt,
            @JsonProperty("detailed_merge_status") String detailedMergeStatus,
            @JsonProperty("has_conflicts") Boolean hasConflicts, @JsonProperty("web_url") String webUrl) {

        FoundMergeRequest toDomain() {
            return new FoundMergeRequest(projectId, new MergeRequest(null, iid, title,
                    UntrustedText.of("gitlab-merge-request-description", emptyToNull(description),
                            MERGE_REQUEST_DESCRIPTION_MAX_CHARS),
                    state, Boolean.TRUE.equals(draft), sourceBranch, targetBranch, author == null ? null : author.username(),
                    usernames(assignees), usernames(reviewers), labels == null ? List.of() : labels, createdAt,
                    updatedAt, mergedAt, detailedMergeStatus, Boolean.TRUE.equals(hasConflicts), webUrl));
        }

        private static List<String> usernames(List<UserResponse> users) {
            return users == null ? List.of()
                    : users.stream().map(UserResponse::username).filter(Objects::nonNull).toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NoteResponse(long id, UserResponse author, @JsonProperty("created_at") String createdAt) {
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }
}
