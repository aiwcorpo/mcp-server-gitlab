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
(test), a test in the style of `RepositoryToolsTest` or `MergeRequestToolsTest`, and a row in the README table. A tool
that takes an id below a project must address it under the project and check that the answer names the same project.
There is deliberately no tool that calls an arbitrary API path or GraphQL.

Fixtures of a GitLab version live in `mcp-app/src/test/resources/fixtures/gitlab-<version>/` (what differs between
versions) and `common/`. They are written from documented response shapes; when you have a real recording, anonymize
hosts, e-mail addresses, tokens and user names first.

## Commits

Subject in English, imperative, up to 72 characters. The body describes the change, not the process. Tests go in the
same commit as the code they cover.

## License

Contributions are licensed under Apache-2.0 (see `LICENSE`).
