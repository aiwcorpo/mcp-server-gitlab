# Changelog

## Unreleased

- Starter 3.0.0-beta-14 (contracts 6.3.2).
- GitLab self-managed 17.0 to 19.x: tolerant reads of renamed fields (`detailed_merge_status`, `merge_user`, `draft`), draft
  filter by version, version learned from the first request with a user credential (the server holds no credential of its
  own); outside the range the server runs in the `degraded` state.
- 30 tools instead of 4. New reads: `get_project`, `list_repository_tree`, `search_code`, `list_branches`, `list_commits`,
  `get_commit`, `get_commit_diff`, `list_merge_requests`, `list_merge_request_diffs`, `list_merge_request_notes`, `list_issues`,
  `get_issue`, `list_issue_notes`, `list_pipelines`, `get_pipeline`, `list_pipeline_jobs`, `get_job_log`. New changes (`UPDATE`):
  `create_merge_request`, `update_merge_request`, `approve_merge_request`, `merge_merge_request`, `create_issue`,
  `update_issue`, `comment_issue`, `retry_pipeline`, `cancel_pipeline`.
- `list_projects` returns `PagedResult` (`items`, `nextCursor`, `truncated`, `omitted`) with a cursor instead of
  `{group, projects, truncated}`; `get_merge_request` adds `mergedBy` and `headSha`. Breaking for clients.
- New settings `MCP_PAGING_CURSOR_KEY`, `MCP_GITLAB_MAX_JSON_SIZE` and `MCP_GITLAB_MAX_LOG_SIZE`.
- `merge_merge_request` has no `removeSourceBranch`: it never deletes the source branch. `approve_merge_request` reports an existing
  approval of the same user. `list_projects` fills its page (`PageFiller`). `429` is `rate_limited`; a change whose answer names
  another project is `upstream_unavailable` with a fixed text. Job log masking is bounded in time and covers more shapes.
- Apache-2.0 `LICENSE` and `NOTICE`, `SECURITY.md`, `CONTRIBUTING.md`.
