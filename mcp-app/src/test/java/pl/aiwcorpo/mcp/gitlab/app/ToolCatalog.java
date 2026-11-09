package pl.aiwcorpo.mcp.gitlab.app;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tools the server is meant to publish, with the risk and the resource argument of each: the reviewed surface. A tool
 * with no resource argument ({@code get_current_user}) has {@code null} for the argument and the type.
 */
final class ToolCatalog {

    record Entry(String risk, String argument, String type) {
    }

    static final Map<String, Entry> TOOLS = new LinkedHashMap<>();

    static {
        group("list_projects", "READ_ONLY");
        for (String tool : new String[] {"get_project", "get_file", "list_repository_tree", "search_code", "list_branches",
                "list_commits", "get_commit", "get_commit_diff", "list_merge_requests", "get_merge_request",
                "list_merge_request_diffs", "list_merge_request_notes", "list_issues", "get_issue", "list_issue_notes",
                "list_pipelines", "get_pipeline", "list_pipeline_jobs", "get_job_log", "list_tags", "list_releases",
                "get_job_artifact_file", "list_project_members", "list_labels"}) {
            project(tool, "READ_ONLY");
        }
        for (String tool : new String[] {"comment_merge_request", "create_merge_request", "update_merge_request",
                "approve_merge_request", "merge_merge_request", "create_issue", "update_issue", "comment_issue",
                "retry_pipeline", "cancel_pipeline", "create_branch"}) {
            project(tool, "UPDATE");
        }
        TOOLS.put("get_current_user", new Entry("READ_ONLY", null, null));
    }

    private ToolCatalog() {
    }

    private static void group(String tool, String risk) {
        TOOLS.put(tool, new Entry(risk, "group", "GitLab::Group"));
    }

    private static void project(String tool, String risk) {
        TOOLS.put(tool, new Entry(risk, "project", "GitLab::Project"));
    }
}
