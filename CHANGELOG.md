# Changelog

## 2.0.0

Major release: the policies of the server live in this repository. Built on `mcp-platform-starter` 4.0.0 and `mcp-contracts` 7.0.0.

- CC-249, the gateway connects to the server by its address: the server needs no certificate from the internal CA, no mTLS and no policy data
  address. The server serves `GET /.well-known/mcp-manifest` and takes `POST /.well-known/mcp-policy-data`; the gateway pulls the manifest and
  pushes the policy data, so the registration of the manifest from the pipeline is gone. `MCP_GATEWAY_JWKS_URI` is the public
  `https://<domain>/.well-known/mcp-jwks.json`. TLS is the author's own certificate (`MCP_TLS_CERT` and `MCP_TLS_KEY`, or `MCP_TLS_KEYSTORE`), a self-signed
  one generated once into `MCP_STATE_DIR` (its fingerprint is in the log at start), or `MCP_PLAIN_HTTP=true`. The one-time `jti` is kept in memory unless
  `MCP_REDIS_HOST` is set; the cursor key (`MCP_PAGING_CURSOR_KEY`) is kept in the state directory. Removed from the configuration and from `deploy/`:
  `MCP_GATEWAY_POLICY_DATA_URL`, `MCP_GATEWAY_TLS_NAME`, `MCP_INTERNAL_URL`, `MCP_TLS_TRUSTSTORE`, the Redis service of `compose.yaml`; `certs/` is optional.
  `deploy/README.md` has the new procedure (the author starts the server, the operator enters its address in the portal and approves the connection after
  comparing the fingerprint from the log).
- Cedar policies in `mcp-app/src/main/resources/mcp/policies/` (schema in the namespace `GitLab`, `resource-types.json`, `read.cedar`,
  `update.cedar`, `restricted.cedar`), carried in the manifest with `policiesHash`. They replace `policies/gitlab/` of the `mcp-policies`
  repository; the rules are the same, except what the platform now generates for every server: the call rules by group, the forbid
  for `restricted` groups and projects (own topics and the topics of the parent group) and the rule for the service account.
- A deployed server whose policies or declarations changed waits for approval in the gateway: it refuses calls (`policies_not_approved`,
  `policies_differ`) until an administrator approves the new `policiesHash`. Register the new manifest and approve before the first call.
- The scopes and the inherited classification that the entity builder used to derive are now the job of the resolvers. Both give a
  group and a project the scope `GitLab::Group/<path>` of every group on the path (`Scopes.pathPrefixes`) and the attribute
  `inheritedClassification`, and both provide the grant probe (`probe(id)`), so a caller without a grant is refused before GitLab is asked.
- The `idPattern` of the declarations no longer expresses the 20 levels of subgroups or the ban on `.git` and `.atom` (CC-237).
  `GitLabPaths` checks both in the resolvers and in the argument checks; a path that breaks them is `invalid_argument` and never reaches
  GitLab. The ban now applies to every segment, as GitLab has it, not only to the end of the path. Path arguments take their pattern from
  the declared types (`@ArgumentLimits(resourceType = ...)`) instead of `GitLabGroupPath` and `GitLabProjectPath`.
- Removed: `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE`, `MCP_POLICY_STATE_DIR` and the signed policy bundle. Added:
  `MCP_GATEWAY_POLICY_DATA_URL` (and `MCP_GATEWAY_TLS_NAME` when the host in it is not a name of the gateway certificate). The server
  connects to the gateway for the policy data with its own certificate, which now needs the usage `clientAuth` next to `serverAuth`
  (CN `mcp-gitlab`, SAN `mcp-gitlab.internal`).
- `deploy/` has `compose.yaml`, `mcp-gitlab.env` and `certs/` next to the systemd files, and the procedure that adds the system `gitlab`
  in the broker and the server `mcp-gitlab` in the gateway without restarting anything.
- `.run/` has IntelliJ configurations for the server and for the `LocalGateway` of `mcp-platform-test`.
- Seven more tools (37 in all). Reads of a project: `list_tags`, `list_releases` (the description as untrusted Markdown, asset link names without
  their URLs), `list_project_members` (including inherited members, no e-mail addresses), `list_labels` (with the counts of open issues and merge
  requests and an optional search text) and `get_job_artifact_file` (one UTF-8 text file from the artifacts of a job of the project, with the size
  limit and the binary refusal of `get_file`; the file is not scanned for credentials). `get_current_user` returns id, username, name and state of the
  GitLab user behind the credential; it names no resource, so only the read group of the system gates it. One write, `create_branch` (`UPDATE`,
  from a branch, tag or commit; the name is checked before GitLab, an existing branch is refused with the fixed text of a rejected change).

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
