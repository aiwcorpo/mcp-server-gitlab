# Katalog narzędzi

Narzędzia to powierzchnia publiczna serwera. Każda zmiana nazwy, opisu, schematu albo ryzyka zmienia
`definitionHash` (C6) i wymaga ponownego zatwierdzenia w gateway'u, więc przechodzi przez code review razem z
tą tabelą.

| Narzędzie (klient widzi `gitlab__<nazwa>`) | Ryzyko | Akcja (C6) | Argumenty | Uprawnienie (C9) | Poświadczenie |
| --- | --- | --- | --- | --- | --- |
| `list_projects` | `READ_ONLY` | `read` | `group` (`GitLabGroupPath`, maks. 1024) | `mcp-gitlab-read` | z C3: `user-secret` -> `PRIVATE-TOKEN`, `user-oauth`/`token-exchange` -> `Authorization: Bearer` |
| `get_file` | `READ_ONLY` | `read` | `project` (`GitLabProjectPath`, maks. 1024), `path` (1-1024, względna, bez `..`/`.`/`//`/`\`/`%`), `ref` opcjonalny (gałąź, tag, SHA; maks. 255) | `mcp-gitlab-read` | jw. |
| `get_merge_request` | `READ_ONLY` | `read` | `project`, `iid` (1..2147483647) | `mcp-gitlab-read` | jw. |
| `comment_merge_request` | `UPDATE` | `update` | `project`, `iid`, `body` (1-10000, bez quick actions) | `mcp-gitlab-write` | jw. |

Zasoby C7 (do `@ResourceRef` ze starterem beta, M3): `group` -> `GitLab::Group`, `project` -> `GitLab::Project`.
Identyfikator i rodzic zasobu to `full_path`/`path_with_namespace` zwrócone przez GitLaba, nigdy argument.

Wyniki: tekst pisany przez użytkowników GitLaba (treść pliku, opisy) jest obiektem
`{kind: "untrusted-data", origin, text, truncated}`; `origin` to `gitlab-file-content`,
`gitlab-project-description` albo `gitlab-merge-request-description`.

Wywołania GitLab API v4 (wszystkie jako użytkownik, bez przekierowań):

| Narzędzie | Wywołania |
| --- | --- |
| `list_projects` | `GET /groups/:path`, `GET /groups/:id/projects?per_page=100&page=1&order_by=path&sort=asc&include_subgroups=false&with_shared=false` |
| `get_file` | `GET /projects/:path`, `HEAD /projects/:id/repository/files/:file_path?ref=`, `GET /projects/:id/repository/files/:file_path/raw?ref=<commit>` |
| `get_merge_request` | `GET /projects/:path`, `GET /projects/:id/merge_requests/:iid` |
| `comment_merge_request` | `GET /projects/:path`, `POST /projects/:id/merge_requests/:iid/notes` |

Konwencje: nazwy `snake_case`, czasownik i obiekt, bez prefiksu systemu (dokłada go gateway); opis mówi, co
narzędzie zwraca, bez instrukcji dla modelu.
