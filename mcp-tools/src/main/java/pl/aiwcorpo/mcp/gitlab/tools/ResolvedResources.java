package pl.aiwcorpo.mcp.gitlab.tools;

import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroup;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabGroupResource;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProject;
import pl.aiwcorpo.mcp.gitlab.core.domain.GitLabProjectResource;
import pl.aiwcorpo.mcp.platform.resource.ResourceContext;

/**
 * The group or project the starter's resolver fetched for an argument, with the caller's credential, before the tool
 * ran. The policy has accepted exactly this answer, so a tool reads it from here instead of asking GitLab again.
 */
final class ResolvedResources {

    static final String GROUP = "GitLab::Group";
    static final String PROJECT = "GitLab::Project";

    private ResolvedResources() {
    }

    static GitLabGroup group(String path) {
        return ResourceContext.required().fetched(GROUP, path, GitLabGroupResource.class).orElseThrow().group();
    }

    static GitLabProject project(String path) {
        return ResourceContext.required().fetched(PROJECT, path, GitLabProjectResource.class).orElseThrow().project();
    }
}
