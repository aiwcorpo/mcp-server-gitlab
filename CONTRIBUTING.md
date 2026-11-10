# Contributing

The server follows the contracts of `mcp-contracts`. A change that needs a different contract (a resource type such as a
merge request, a query field, the manifest) starts as a `proposed` entry in `CONTRACT_CHANGES.md` of that repository and
needs approval before it is implemented here.

## Setup

JDK 25. No container runtime is needed: tests run against `TestGateway` and an in-process stub of GitLab. The
`mcp-platform-bom` artifacts (starter, contracts) come from Maven Central, or must already be in `~/.m2`. Linux and macOS
with bash 3.2 or newer; Windows only through WSL2.

```bash
./mvnw -B verify     # the same command runs in CI
```

`docs/developer/tools.md` in `mcp-docs` describes how to add a tool. A new tool needs: `@ToolTitle`, `@ToolRisk`,
`@ResourceRef` on the argument that names the project or group, a closed argument schema, an entry in `ToolCatalog`
(test), a test in the style of `RepositoryToolsTest` or `MergeRequestToolsTest`, and its name in the tools table of the README. A tool
that takes an id below a project must address it under the project and check that the answer names the same project.
There is deliberately no tool that calls an arbitrary API path or GraphQL.

Fixtures of a GitLab version live in `mcp-app/src/test/resources/fixtures/gitlab-<version>/` (what differs between
versions) and `common/`. They are written from documented response shapes; when you have a real recording, anonymize
hosts, e-mail addresses, tokens and user names first.

## Policies

The Cedar policies of GitLab are in `mcp-app/src/main/resources/mcp/policies/` and go into the jar and the manifest:

| File | Content |
| --- | --- |
| `schema.cedarschema` | namespace `GitLab`: `GitLab::Group` and `GitLab::Project` (parent: its group), both with `classification` and `inheritedClassification`; actions `read`, `update`, `delete`, `admin` |
| `resource-types.json` | the declarations of both types: `idPattern`, `scopePattern` for groups, the classification and the parent |
| `read.cedar` | reading needs `mcp-gitlab-read`; it follows the user's own rights in GitLab |
| `update.cedar` | changing a project or creating in a group needs `mcp-gitlab-write` and a scope on a group above the resource |
| `restricted.cedar` | closes a group or project below a group classified `restricted` (`inheritedClassification`) |

A scope `GitLab::Group/team-a` covers `team-a`, every subgroup and every project below them, not `team-ab`: the resolvers give
each group and project the scope of every group on its path. A change to any file here changes `policiesHash` in the manifest,
and an administrator approves it before the server decides anything. `GitLabPolicyDecisionsTest` states the decisions.

Two GitLab limits cannot be written as an `idPattern`, so `GitLabPaths` checks them: subgroups nest up to 20 levels, and no
path segment ends in `.git` or `.atom`.

The gateway pushes the scope mapping, the allowed AI clients and the emergency overrides to `POST /.well-known/mcp-policy-data`
after every change and every minute. A server without fresh data answers `tools/call` with `409 policy_data_stale`, and the
gateway pushes and repeats the call.

## GitLab versions

GitLab answers `GET /api/v4/version` only to an authenticated caller, so the server learns the version from the first request
with a user credential and refreshes it once an hour; until then the state is `degraded`. The state shows in the log
(`event=backend_version`), in the metric `mcp_backend_capability{probe="gitlab"}` and in `/actuator/health`. The one request
that differs between versions is the draft filter of `list_merge_requests` (`draft` from 19.0, `wip` before).

## Running against a real GitLab

`.run/` has two IntelliJ configurations. Start `LocalGateway` (serves the JWKS and pushes policy data to the server on port
18445, with the policies of this working tree and the mapping in `.run/scope-mapping.json`), then
`McpGitlab (dev, behind LocalGateway)` with `MCP_GITLAB_URL` set to your GitLab. Restart `LocalGateway` after a change to the
policies: it approves the hash of the files it read.

## Commits

Subject in English, imperative, up to 72 characters. The body describes the change, not the process. Tests go in the
same commit as the code they cover.

## License

Contributions are licensed under Apache-2.0 (see `LICENSE`).
