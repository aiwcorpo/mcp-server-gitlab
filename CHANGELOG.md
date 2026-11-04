# Changelog

## 1.0.0

First public release. MCP server for self-managed GitLab 17.0 to 19.x (REST API v4), run behind the platform gateway. Built on
`mcp-platform-starter` 3.0.0 and `mcp-contracts` 6.3.2.

- 30 tools: 20 reads (projects, files, repository trees, code search, branches, commits and diffs, merge requests with diffs
  and notes, issues and notes, pipelines, jobs and job logs) and 10 changes (`UPDATE`: merge request comment, creation, update,
  approval and merge; issue creation, update and comment; pipeline retry and cancel). No tool is `DESTRUCTIVE`.
- Reads are tolerant of renamed fields (`detailed_merge_status`, `merge_user`, `draft`); the draft filter follows the version. The
  server holds no credential of its own and learns the GitLab version from the first request with a user credential; outside
  the range it runs in the `degraded` state.
- `list_projects` returns `PagedResult` (`items`, `nextCursor`, `truncated`, `omitted`) and fills its page after the policy
  filter. The cursor is signed with `MCP_PAGING_CURSOR_KEY`; `MCP_GITLAB_MAX_JSON_SIZE` and `MCP_GITLAB_MAX_LOG_SIZE` bound responses.
- `merge_merge_request` never deletes the source branch. `approve_merge_request` reports an existing approval of the same
  user. `429` is `rate_limited`; a change whose answer names another project is `upstream_unavailable` with a fixed text. Job log
  masking is bounded in time.
- Tool titles are in English. Apache-2.0 `LICENSE` and `NOTICE`, `SECURITY.md`, `CONTRIBUTING.md`; the release workflow builds the
  image for ghcr.io and attaches the manifest and SBOM to the GitHub Release.
