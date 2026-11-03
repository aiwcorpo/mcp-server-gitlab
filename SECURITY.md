# Security policy

## Reporting a vulnerability

Report vulnerabilities privately through GitHub: Security tab of this repository, "Report a vulnerability"
(private vulnerability reporting). Do not open a public issue for a security problem.

Include the affected version (`pom.xml`), the tool involved, the GitLab version, the configuration and steps to
reproduce. In scope: a request that gets past the internal JWT check, a way to read a GitLab credential from a log or a
tool result, a result that returns a project, merge request, issue or job log the policy denies, a tool that changes a
resource other than the one the policy judged (an id below a project that belongs to another project), a quick action
or mention that gets through the argument checks, a secret that survives the job log redaction in a form it claims to
catch, and text from GitLab that reaches a model as anything but untrusted data.

## Scope

In scope: the code in this repository (tools, the GitLab adapters, resolvers, the version detection).

Out of scope:

- The `dev` profile relaxes TLS, mTLS and the replay store by design; it listens on the loopback address only and
  is refused in a container, a Kubernetes pod and under systemd.
- The gateway, the credential broker, the policies and the starter are separate repositories.
- GitLab itself, and the permissions of the account whose token the user connects.
- Secrets in job logs that do not look like the documented patterns: redaction is a second line behind GitLab's masking.

## Supported versions

Only the latest released version receives fixes. Published versions are immutable; a fix ships as a new release.
