package pl.aiwcorpo.mcp.gitlab.integration;

import org.springframework.http.HttpHeaders;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * How the C3 credential is presented to GitLab. A personal or project access token ({@code user-secret}) goes
 * in {@code PRIVATE-TOKEN}; an OAuth access token ({@code user-oauth}, or one from token exchange) goes in
 * {@code Authorization: Bearer}. Any other combination is a platform misconfiguration (GitLab is connected per
 * user, and Basic is not offered for users in v1), so it fails the call instead of guessing a header.
 */
final class GitLabCredentials {

    static final String PRIVATE_TOKEN = "PRIVATE-TOKEN";

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

    private static IllegalStateException unsupported(BackendCredential credential) {
        return new IllegalStateException("GitLab does not take a credential of type " + credential.type().wireName()
                + " with scheme " + credential.scheme().wireName());
    }
}
