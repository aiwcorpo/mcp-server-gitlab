package pl.aiwcorpo.mcp.gitlab.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.security.CredentialRequiredException;

/**
 * How the C3 credential is presented to GitLab. A personal or project access token ({@code user-secret}) goes
 * in {@code PRIVATE-TOKEN}; an OAuth access token ({@code user-oauth}, or one from token exchange) goes in
 * {@code Authorization: Bearer}. Any other combination is a platform misconfiguration (GitLab is connected per
 * user, and Basic is not offered for users in v1), so the call is refused instead of guessing a header and the user is sent to the portal to connect the account
 * again ({@code credential_required}).
 */
final class GitLabCredentials {

    static final String PRIVATE_TOKEN = "PRIVATE-TOKEN";
    private static final Logger LOG = LoggerFactory.getLogger(GitLabCredentials.class);

    private GitLabCredentials() {
    }

    static void apply(BackendCredential credential, HttpHeaders headers) {
        BackendCredential.Scheme scheme = credential.scheme();
        switch (credential.type()) {
            case USER_SECRET -> {
                if (scheme != BackendCredential.Scheme.BEARER && scheme != BackendCredential.Scheme.TOKEN) {
                    throw unsupported(credential);
                }
                headers.set(PRIVATE_TOKEN, credential.value());
            }
            case USER_OAUTH, TOKEN_EXCHANGE -> {
                if (scheme != BackendCredential.Scheme.BEARER) {
                    throw unsupported(credential);
                }
                credential.applyTo(headers);
            }
            default -> throw unsupported(credential);
        }
    }

    private static CredentialRequiredException unsupported(BackendCredential credential) {
        LOG.warn("event=credential_unsupported system=gitlab type={} scheme={}", credential.type().wireName(),
                credential.scheme().wireName());
        return new CredentialRequiredException("gitlab");
    }
}
