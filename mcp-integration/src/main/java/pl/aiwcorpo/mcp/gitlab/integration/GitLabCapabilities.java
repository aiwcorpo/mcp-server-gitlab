package pl.aiwcorpo.mcp.gitlab.integration;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.platform.capability.Capabilities;
import pl.aiwcorpo.mcp.platform.capability.ProductVersion;

/**
 * What the GitLab version can do, for the adapters that differ between versions. GitLab answers {@code /api/v4/version}
 * only to an authenticated caller and the server holds no credential of its own, so the version is learned from the first
 * request that carries a user's credential and refreshed once an hour. Until then, and when the call fails or the version
 * is outside the tested range, every question is "no" and the adapter takes its conservative path.
 *
 * <p>The tested range is 17.0 to 19.x, the versions with fixtures in this repository. The only behaviour that depends
 * on the version is the draft filter of {@code list_merge_requests}.
 */
@Component
public class GitLabCapabilities {

    /** Name of the probe: the label of the metric and the key in the platform's capabilities. */
    static final String PROBE = "gitlab";

    /** Merge request listing takes {@code draft=yes|no}: GitLab 19.0 and newer. */
    static final String MR_DRAFT_FILTER = "mr_draft_filter";

    /** Merge request listing takes {@code wip=yes|no}, deprecated in favour of {@code draft}: 17.x and 18.x. */
    static final String MR_WIP_FILTER = "mr_wip_filter";

    static final int FIRST_MAJOR = 17;
    static final int LAST_TESTED_MAJOR = 19;
    private static final Duration REFRESH = Duration.ofHours(1);
    /** A version endpoint that refused (token without the scope) is not asked again on every request. */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(5);
    private static final Logger LOG = LoggerFactory.getLogger(GitLabCapabilities.class);

    private record Learned(String version, long learnedAtMillis) {
    }

    private final GitLabHttp http;
    private final AtomicReference<Learned> learned = new AtomicReference<>();
    private volatile long failedAtMillis;

    public GitLabCapabilities(GitLabHttp http) {
        this.http = http;
    }

    /** Asks GitLab for its version with the current user's credential when it is not known or stale; never fails the caller. */
    void learn() {
        Learned known = learned.get();
        long now = System.currentTimeMillis();
        if (known != null && now - known.learnedAtMillis() < REFRESH.toMillis()
                || now - failedAtMillis < RETRY_AFTER_FAILURE.toMillis()) {
            return;
        }
        try {
            java.util.Optional<String> version = http.version().map(GitLabHttp.VersionResponse::version);
            if (version.isEmpty()) {
                failedAtMillis = now;
                return;
            }
            if (known == null || !known.version().equals(version.get())) {
                LOG.info("event=backend_version version={}", sanitized(version.get()));
            }
            learned.set(new Learned(version.get(), now));
        } catch (RuntimeException unknown) {
            failedAtMillis = now;
            // The request that follows reports a credential problem itself; here a failure only means "not known".
            LOG.debug("event=backend_version_unavailable reason={}", unknown.getClass().getSimpleName());
        }
    }

    /** Drops the learned version, so that the next request learns it again (a version that changed, a test). */
    public void forget() {
        learned.set(null);
        failedAtMillis = 0;
    }

    /** The state for the platform's health and metric; reads what was learned and calls nothing. */
    public Capabilities current() {
        Learned known = learned.get();
        if (known == null) {
            return Capabilities.degraded(null, Set.of(), "version not learned yet: no request with a user credential so far");
        }
        ProductVersion version = ProductVersion.parse(known.version()).orElse(null);
        if (version == null) {
            return Capabilities.degraded(null, Set.of(), "the version endpoint did not return a version");
        }
        if (version.major() < FIRST_MAJOR || version.major() > LAST_TESTED_MAJOR) {
            return Capabilities.degraded(known.version(), Set.of(), "version outside the tested range 17.0 to 19.x");
        }
        return Capabilities.supported(known.version(), version.atLeast(19, 0)
                ? Set.of(MR_DRAFT_FILTER) : Set.of(MR_WIP_FILTER));
    }

    boolean draftFilter() {
        return current().supports(MR_DRAFT_FILTER);
    }

    boolean wipFilter() {
        return current().supports(MR_WIP_FILTER);
    }

    private static String sanitized(String version) {
        return version.replaceAll("[^A-Za-z0-9._+ -]", "?");
    }
}
