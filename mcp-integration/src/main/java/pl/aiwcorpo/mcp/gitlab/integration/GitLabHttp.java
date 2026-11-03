package pl.aiwcorpo.mcp.gitlab.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendPage;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendRateLimitedException;
import pl.aiwcorpo.mcp.gitlab.core.domain.BackendRefusedException;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.security.CredentialContext;
import pl.aiwcorpo.mcp.platform.security.CredentialRequiredException;

/**
 * The one HTTP client of the GitLab REST API v4, shared by the adapters: the user's own credential from the gateway
 * (C3) on every request, redirects never followed, bodies bounded, and one mapping of statuses.
 *
 * <p>Error mapping matters for security. 404 and 403 both become "not visible" (an empty result, one client-facing code,
 * {@code not_found_or_denied}); 401 means the stored credential no longer works, so the user is sent to the portal to
 * reconnect ({@code credential_required}). A failed call becomes an exception with the status alone and an unusable
 * answer (redirect, unparsable or oversized body) a {@link BackendResponseException}, so no response body reaches a
 * message or a log line.
 */
@Component
@EnableConfigurationProperties(GitLabBackendProperties.class)
public class GitLabHttp {

    /** System id of this server: {@code server-id} is {@code mcp-<system>}, and the starter refuses any other. */
    static final String SYSTEM = "gitlab";

    private static final String DEV_PROFILE = "dev";
    private static final Logger LOG = LoggerFactory.getLogger(GitLabHttp.class);

    private final RestClient client;
    private final RestClient jsonClient;
    private final RestClient logClient;
    private final GitLabBackendProperties properties;

    public GitLabHttp(GitLabBackendProperties properties, ObjectProvider<SslBundles> sslBundles, Environment environment) {
        this.properties = properties;
        String apiRoot = apiRoot(properties.baseUrl(), environment.matchesProfiles(DEV_PROFILE) || properties.allowPlainHttp());
        JdkClientHttpRequestFactory factory = requestFactory(properties, sslBundles);
        this.client = newClient(apiRoot, factory, properties.maxResponseSize().toBytes());
        this.jsonClient = newClient(apiRoot, factory, properties.maxJsonSize().toBytes());
        this.logClient = newClient(apiRoot, factory, properties.maxLogSize().toBytes());
    }

    private static RestClient newClient(String apiRoot, JdkClientHttpRequestFactory factory, long maxBytes) {
        DefaultUriBuilderFactory uris = new DefaultUriBuilderFactory(apiRoot);
        uris.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        return RestClient.builder()
                .uriBuilderFactory(uris)
                .requestFactory(factory)
                .requestInterceptor(new BoundedBodyInterceptor(maxBytes))
                .defaultStatusHandler(status -> status.isError() || status.is3xxRedirection(), (request, response) -> {
                    // Status only: the body and headers of a failed call may echo the credential or carry backend
                    // detail, and the exception message ends up in the starter's tool_failed log line.
                    HttpStatusCode status = response.getStatusCode();
                    if (status.value() == 429) {
                        throw new BackendRateLimitedException(retryAfter(response.getHeaders().getFirst("Retry-After")));
                    }
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

    /** The credential of the current request; a missing one is {@code credential_required}. */
    BackendCredential credential() {
        return CredentialContext.require(SYSTEM);
    }

    /** GET as the user; empty when GitLab does not show the thing. */
    <T> Optional<T> get(Class<T> type, GitLabQuery query) {
        BackendCredential credential = credential();
        return visible(() -> jsonClient.get().uri(query.template(), query.variables())
                .headers(headers -> GitLabCredentials.apply(credential, headers))
                .retrieve().body(type));
    }

    /** GET of a list as the user, with the number of the next page from {@code X-Next-Page}. */
    <T> Optional<BackendPage<T>> getPage(ParameterizedTypeReference<List<T>> type, GitLabQuery query) {
        BackendCredential credential = credential();
        return visible(() -> {
            ResponseEntity<List<T>> response = jsonClient.get().uri(query.template(), query.variables())
                    .headers(headers -> GitLabCredentials.apply(credential, headers))
                    .retrieve().toEntity(type);
            List<T> items = response.getBody() == null ? List.of() : response.getBody();
            return new BackendPage<>(items, nextPage(response.getHeaders().getFirst("X-Next-Page")));
        });
    }

    /** The raw body of a GET, for the log of a job: its own, larger size limit. */
    Optional<byte[]> getLog(GitLabQuery query) {
        BackendCredential credential = credential();
        try {
            return visible(() -> logClient.get().uri(query.template(), query.variables())
                    .headers(headers -> GitLabCredentials.apply(credential, headers))
                    .retrieve().body(byte[].class));
        } catch (BackendResponseException unusable) {
            if (unusable.tooLarge()) {
                throw new BackendRefusedException(BackendRefusedException.Reason.LOG_TOO_LARGE);
            }
            throw unusable;
        } catch (ResourceAccessException failed) {
            if (ResponseTooLargeException.causedBy(failed)) {
                throw new BackendRefusedException(BackendRefusedException.Reason.LOG_TOO_LARGE);
            }
            throw failed;
        }
    }

    /** HEAD as the user, for the headers only. */
    Optional<HttpHeaders> head(GitLabQuery query) {
        BackendCredential credential = credential();
        return visible(() -> client.head().uri(query.template(), query.variables())
                .headers(headers -> GitLabCredentials.apply(credential, headers))
                .retrieve().toBodilessEntity())
                .map(ResponseEntity::getHeaders);
    }

    /** GET of a representation that is not JSON (file content). */
    Optional<byte[]> getBytes(GitLabQuery query) {
        BackendCredential credential = credential();
        return visible(() -> {
            byte[] content = client.get().uri(query.template(), query.variables())
                    .headers(headers -> GitLabCredentials.apply(credential, headers))
                    .retrieve().body(byte[].class);
            // An empty file has no body, which is still a file and not "not found".
            return content == null ? new byte[0] : content;
        });
    }

    /**
     * A change as the user. GitLab's refusals that depend on the state of the project become a
     * {@link BackendRefusedException} with a fixed reason (never GitLab's text); not visible is empty.
     *
     * @param unauthorizedIsDenial approve and merge answer 401 when the user may not do it (own merge request, no
     *                             permission), which is not an expired credential; the credential is then checked once
     *                             on its own, and only a credential that no longer works is {@code credential_required}
     */
    <T> Optional<ResponseEntity<T>> send(HttpMethod method, Class<T> type, Object body, GitLabQuery query,
            boolean unauthorizedIsDenial) {
        BackendCredential credential = credential();
        try {
            return visible(() -> {
                RestClient.RequestBodySpec request = jsonClient.method(method).uri(query.template(), query.variables())
                        .headers(headers -> GitLabCredentials.apply(credential, headers));
                if (body != null) {
                    request = request.contentType(MediaType.APPLICATION_JSON).body(body);
                }
                return request.retrieve().toEntity(type);
            });
        } catch (CredentialRequiredException unauthorized) {
            if (unauthorizedIsDenial && credentialStillWorks(credential)) {
                return Optional.empty();
            }
            throw unauthorized;
        } catch (HttpClientErrorException.Conflict conflict) {
            throw new BackendRefusedException(BackendRefusedException.Reason.CONFLICT);
        } catch (HttpClientErrorException refused) {
            int status = refused.getStatusCode().value();
            if (status == 400 || status == 405 || status == 406 || status == 422) {
                throw new BackendRefusedException(BackendRefusedException.Reason.REJECTED);
            }
            throw refused;
        }
    }

    /** The body of a successful change; a 2xx without one is not what the server relies on. */
    static <T> T bodyOf(ResponseEntity<T> response) {
        if (response.getBody() == null) {
            throw new BackendResponseException("empty body");
        }
        return response.getBody();
    }

    /**
     * The answer to a change, if it names this project. A change is already made when the answer arrives, so an answer from
     * another project is not a refusal: the outcome is unknown and the caller is told so with a fixed text.
     */
    static <T> T confirmed(T body, boolean belongsToProject) {
        if (!belongsToProject) {
            throw new BackendRefusedException(BackendRefusedException.Reason.UNCONFIRMED);
        }
        return body;
    }

    private static int retryAfter(String header) {
        try {
            return Math.min(3600, Math.max(1, Integer.parseInt(header.strip())));
        } catch (RuntimeException unusable) {
            return 30;
        }
    }

    private boolean credentialStillWorks(BackendCredential credential) {
        try {
            client.get().uri("/user")
                    .headers(headers -> GitLabCredentials.apply(credential, headers))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.Unauthorized expired) {
            return false;
        } catch (RestClientException unknown) {
            // The check says nothing: stay with the safer reading, that the user has to reconnect.
            return false;
        }
    }

    /** {@code GET /version} as the user: GitLab does not answer it anonymously, and the server holds no credential of its own. */
    Optional<VersionResponse> version() {
        return get(VersionResponse.class, new GitLabQuery("/version"));
    }

    GitLabBackendProperties properties() {
        return properties;
    }

    static <T> Optional<T> visible(Supplier<T> call) {
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
            boolean tooLarge = ResponseTooLargeException.causedBy(unreadable);
            throw new BackendResponseException(tooLarge ? "body larger than the limit" : "body not readable", tooLarge);
        }
    }

    static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new BackendResponseException("field the server relies on is missing");
        }
        return value;
    }

    private static Integer nextPage(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            int page = Integer.parseInt(header.strip());
            return page > 0 ? page : null;
        } catch (NumberFormatException notANumber) {
            return null;
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

    /** What {@code GET /version} says; unknown fields ({@code revision}, {@code enterprise}) are not read. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record VersionResponse(String version) {
    }
}
