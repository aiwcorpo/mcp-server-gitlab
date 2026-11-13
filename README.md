# mcp-gitlab

Part of [MCP Platform](https://github.com/aiwcorpo/mcp-platform), a self-hosted MCP gateway for companies. Documentation: [mcp.aiwcorpo.pl](https://mcp.aiwcorpo.pl/).

MCP server for self-managed GitLab. AI clients such as Claude Code reach it through the gateway of the aiwcorpo MCP platform;
the server calls GitLab with each user's own token.

## Quick start

```bash
cd deploy
# fill in MCP_GATEWAY_JWKS_URI, MCP_CONNECT_URL and MCP_GITLAB_URL in mcp-gitlab.env
docker compose up -d
docker compose logs mcp-gitlab | grep event=tls_certificate
```

Then the platform operator adds `https://<this host>:8443/mcp` in the portal. Full steps: [deploy/README.md](deploy/README.md).

## Tools

37 tools, shown to a client as `gitlab__<name>`. Reading needs the group `mcp-gitlab-read`, writing `mcp-gitlab-write`; no
tool deletes anything.

| Area | Read | Write |
| --- | --- | --- |
| Projects | `list_projects`, `get_project`, `list_project_members`, `list_labels` | |
| Repository | `get_file`, `list_repository_tree`, `search_code`, `list_branches`, `list_tags`, `list_releases`, `list_commits`, `get_commit`, `get_commit_diff` | `create_branch` |
| Issues | `list_issues`, `get_issue`, `list_issue_notes` | `create_issue`, `update_issue`, `comment_issue` |
| Merge requests | `list_merge_requests`, `get_merge_request`, `list_merge_request_diffs`, `list_merge_request_notes` | `create_merge_request`, `update_merge_request`, `comment_merge_request`, `approve_merge_request`, `merge_merge_request` |
| CI | `list_pipelines`, `get_pipeline`, `list_pipeline_jobs`, `get_job_log`, `get_job_artifact_file` | `retry_pipeline`, `cancel_pipeline` |
| User | `get_current_user` | |

User tokens: scope `read_api` for reading, `api` for writes. Writes are allowed only inside GitLab groups that the platform
operator maps to the user's group (scope mapping). Groups and projects with the topic `restricted` are closed to everyone.
Strings that look like credentials in job logs and artifact files are replaced by `[redacted]`.

## Supported GitLab versions

GitLab CE and EE 17.0 to 19.x, REST API v4. Other versions work in the `degraded` state, without a guarantee.

## Configuration

Required, in `deploy/mcp-gitlab.env`:

| Variable | Description |
| --- | --- |
| `MCP_GATEWAY_JWKS_URI` | key set of the gateway: `https://<platform>/.well-known/mcp-jwks.json` |
| `MCP_CONNECT_URL` | portal page where a user connects GitLab: `https://<platform>/connections?system={system}` |
| `MCP_GITLAB_URL` | address of GitLab without `/api/v4`, `https` only |

Optional settings (own certificate, Redis for several instances, size limits) are commented in the same file.

## Build

```bash
export JAVA_HOME=<path to JDK 25>
./mvnw verify
```

The jar is `mcp-app/target/mcp-gitlab.jar`; `docker build -t mcp-gitlab:dev .` builds the image. Tests need neither Docker nor
GitLab. Working on the code: [CONTRIBUTING.md](CONTRIBUTING.md).

## More

- [deploy/README.md](deploy/README.md): installation, troubleshooting, upgrade from 1.x
- [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md)

Apache License 2.0.
