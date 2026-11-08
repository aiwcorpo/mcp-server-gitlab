# mcp-gitlab

MCP server for self-managed GitLab (REST API v4). It is one of the native servers of the MCP platform and runs only behind
the platform gateway: it accepts mutual TLS traffic carrying the gateway's internal JWT and calls GitLab with the user's
credential, which the gateway attaches to every request. Without a valid JWT it answers 401; there is no anonymous mode.

The server does not log users in, decide who may call which tool, or ask for confirmation of destructive actions. The
gateway does that. The server provides the tools, the GitLab adapter, error mapping and the audit trail the starter writes.

Built on `mcp-platform-starter` 4.0.0 (Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, `mcp-contracts` 7.0.0).

## Supported GitLab versions

GitLab self-managed (CE and EE), `/api/v4`, tested for 17.0 to 19.x. The tests use fixtures for 17.8 and 19.5 written from the
vendor documentation, not recorded from a live instance. GitLab.com and GitLab Dedicated are not targets of the first release.

Versions outside the range (16.11 and older, 20 and newer) run in the `degraded` state: every tool still works, without any
guarantee. There is no write gate and no `*_WRITES_ON_UNKNOWN_VERSION` variable for this server. The API only grows, so there
are no per-version adapters; responses are read leniently and renamed fields are read under both names. The one difference
in requests is the draft filter of `list_merge_requests` (`draft` from 19.0, `wip` in 17.x and 18.x); outside the range the
server filters drafts itself on the fetched page.

GitLab answers `GET /api/v4/version` only to an authenticated caller and the server holds no credential of its own, so it learns
the version from the first request that carries a user credential and refreshes it once an hour. Until then the state is
`degraded`. It is visible in the log (`backend capability`, `event=backend_version`), in the metric
`mcp_backend_capability{probe="gitlab"}` and in the `backendCapability` detail of `/actuator/health`.

## Tools

30 tools: 20 `READ_ONLY` and 10 `UPDATE`; there is no `DESTRUCTIVE` tool. A client sees them as `gitlab__<name>`. Each accepts a
personal access token (`user-secret`) or an OAuth token (`user-oauth`). The generated table with each tool's title, risk, action
and required group is in `docs/reference/tools.md` of the `mcp-docs` repository (link below). It is not repeated here.

Reads cover group projects, files, repository trees, code search, branches, commits and diffs, merge requests with their
diffs and notes, issues and notes, pipelines, jobs and job logs. Writes cover merge request comments, creation, update, approval
and merge, issue creation, update and comments, and pipeline retry and cancel. Merge and approval are `UPDATE`, so the write
group is enough and no confirmation code is asked.

Token scopes: `read_api` for the read tools, `api` for the write tools (a read-only token used for a write gives
`not_found_or_denied`). Reporter is enough to read code, merge requests, issues and pipelines; Developer is needed for
writes and for job logs when the project hides them.

## Build and test

JDK 25 is required (`JAVA_HOME` must point to it; on macOS `/usr/libexec/java_home -v 25`).

```bash
export JAVA_HOME=/path/to/jdk-25
./mvnw verify      # build and tests, jar in mcp-app/target/mcp-gitlab.jar
```

The tests need neither Docker nor a GitLab. `TestGateway` from `mcp-platform-test` publishes a JWKS made from the contract test
keys and signs internal JWTs, and `StubBackend` imitates the API. `./mvnw verify` also leaves the manifest in
`mcp-app/target/mcp-manifest.json`; it is the document the running server serves at `/.well-known/mcp-manifest` for the gateway to pull, and an administrator approves every new or changed tool there. `docker build -t mcp-gitlab:dev .` builds the container image (`Dockerfile`, `eclipse-temurin:25-jre`, port 8443) from that jar and needs a Docker daemon. The GitHub, GitLab and Jenkins files run `./mvnw -B verify`.

## Policies

The Cedar policies of GitLab live here, in `mcp-app/src/main/resources/mcp/policies/`, and are part of the jar and of the manifest:

| File | Content |
| --- | --- |
| `schema.cedarschema` | namespace `GitLab`: `GitLab::Group` and `GitLab::Project` (parent: its group), both with `classification` and `inheritedClassification`; actions `read`, `update`, `delete`, `admin` |
| `resource-types.json` | the declarations of both types: `idPattern`, `scopePattern` for groups, the classification and the parent |
| `read.cedar` | reading needs `mcp-gitlab-read`; it follows the user's own rights in GitLab |
| `update.cedar` | changing a project or creating in a group needs `mcp-gitlab-write` and a scope on a group above the resource; deleting a project needs `mcp-gitlab-destructive` and the scope; a group is never deleted |
| `restricted.cedar` | closes a group or project below a group classified `restricted` (`inheritedClassification`) |

The group a tool needs, the closed `restricted` groups and projects (from the declared topics, also through the parent group) and the
limits of the service account are generated by the platform. Who holds which group scope is administrator data in the gateway (the
scope mapping), not part of this repository. A scope `GitLab::Group/team-a` covers `team-a`, every subgroup and every project below
them, not `team-ab`: the resolvers give each group and project the scope of every group on its path. A change to any file here changes
`policiesHash` in the manifest, and an administrator has to approve it before the server decides anything. `GitLabPolicyDecisionsTest`
states the decisions with `PolicyDecisions`.

Two limits of GitLab cannot be written in an `idPattern` (the pattern grammar of the contracts has no lookaround and no counted
repetition of a group), so the server checks them itself in `GitLabPaths`: subgroups nest up to 20 levels (a group path has at most 20
segments, a project path 21) and no segment ends in `.git` or `.atom`. A path that breaks them is an `invalid_argument` before GitLab is asked.

## Running against a real GitLab

`.run/` has two IntelliJ configurations. Start `LocalGateway` (the gateway of `mcp-platform-test`, serving the JWKS and pushing the policy data to the server
on port 18445 with the policies of this working tree and the mapping in `.run/scope-mapping.json`), then
`McpGitlab (dev, behind LocalGateway)` with `MCP_GITLAB_URL` set to your GitLab. Edit the mapping and the `--groups` of the `LocalGateway`
configuration for your group paths, and restart `LocalGateway` after a change to the policies (it approves the hash of the files it
read). The configurations need the `4.0.0` artifacts in `~/.m2`.

## Configuration

The base configuration (`mcp-app/src/main/resources/application.yml`) is the production one. Outside the `dev` profile the
server does not start without these:

| Variable | Meaning |
| --- | --- |
| `MCP_GATEWAY_JWKS_URI` | public key set of the gateway's internal JWT: `https://<domain of the platform>/.well-known/mcp-jwks.json` |
| `MCP_CONNECT_URL` | portal page where a user connects an account; returned in `credential_required` |
| `MCP_GITLAB_URL` | address of the GitLab instance without `/api/v4`; `https` only |
| `MCP_ACCEPTED_CREDENTIAL_TYPES` | accepted credential types for the manifest (`user-oauth,user-secret`; the first is the default) |

Also used: `MCP_GITLAB_SSL_BUNDLE` (SSL bundle for a GitLab with an internal CA), `MCP_PAGING_CURSOR_KEY` (Base64, at least 32
bytes, optional for one instance (kept in the state directory), required and the same on every instance for several; `openssl rand -base64 32`), `MCP_TLS_CERT` and `MCP_TLS_KEY`, or `MCP_TLS_KEYSTORE` with `MCP_TLS_STORE_PASSWORD` (your own certificate; without them the server generates a
self-signed one into `MCP_STATE_DIR`, and `MCP_PLAIN_HTTP=true` means no TLS inside the platform network), `MCP_REDIS_HOST` and the other `MCP_REDIS_*`
(one-time `jti` in Redis instead of memory: for several instances behind a balancer, which also need the same `MCP_PAGING_CURSOR_KEY`) and `MCP_PORT`/`MCP_BIND` (default `8443`, `0.0.0.0`). Size limits: `MCP_GITLAB_FILE_MAX_BYTES` (default 262144, at most 400 KiB),
`MCP_GITLAB_MAX_JSON_SIZE` and `MCP_GITLAB_MAX_LOG_SIZE` (8MB each). `MCP_GITLAB_ALLOW_PLAIN_HTTP=true` allows `http` and exists
only for the mocks of the development stack. The full list with defaults is in `docs/reference/env-servers.md` of `mcp-docs`.

## Documentation

Installation, operation, upgrades, the checks to run on a first real instance and the tool reference live in
[mcp-docs](https://github.com/aiwcorpo/mcp-docs):

- `docs/reference/tools.md`: tools with risk and required group
- `docs/reference/env-servers.md`: environment variables of every server
- `docs/operator/real-systems.md`: supported product versions, read-only checks on a real instance

See also `CONTRIBUTING.md`, `SECURITY.md` and `CHANGELOG.md`. Licensed under Apache-2.0.
