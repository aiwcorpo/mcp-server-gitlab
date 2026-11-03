package pl.aiwcorpo.mcp.gitlab.integration;

import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.platform.capability.Capabilities;
import pl.aiwcorpo.mcp.platform.capability.CapabilityProbe;

/**
 * Reports the platform what {@link GitLabCapabilities} learned from user requests. It makes no call and holds no secret
 * (the platform runs probes outside any request, where there is no user credential), so the state is {@code degraded}
 * until the first user request has taught the server the version.
 */
@Component
public class GitLabVersionProbe implements CapabilityProbe {

    private final GitLabCapabilities capabilities;

    public GitLabVersionProbe(GitLabCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public String name() {
        return GitLabCapabilities.PROBE;
    }

    @Override
    public Capabilities probe() {
        return capabilities.current();
    }
}
