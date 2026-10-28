# Katalog narzędzi

Narzędzia to powierzchnia publiczna serwera. Każda zmiana nazwy, opisu, schematu albo ryzyka zmienia
`definitionHash` (C6) i wymaga ponownego zatwierdzenia w gateway'u, więc przechodzi przez code review razem z
tą tabelą.

| Narzędzie (klient widzi `gitlab__<nazwa>`) | Ryzyko | Akcja (C6) | Argumenty | Uprawnienie (C9) | Poświadczenie |
| --- | --- | --- | --- | --- | --- |
| `list_projects` | `READ_ONLY` | `read` | `group` (`GitLabGroupPath`, maks. 1024) | `mcp-gitlab-read` | z C3: `user-secret` -> `PRIVATE-TOKEN`, `user-oauth`/`token-exchange` -> `Authorization: Bearer` |
| `get_file` | `READ_ONLY` | `read` | `project` (`GitLabProjectPath`, maks. 1024), `path` (1-1024, względna, bez `..`/`.`/`//`/`\`/`%`), `ref` opcjonalny (gałąź, tag, SHA; maks. 255) | `mcp-gitlab-read` | jw. |
| `get_merge_request` | `READ_ONLY` | `read` | `project`, `iid` (1..2147483647) | `mcp-gitlab-read` | jw. |
| `comment_merge_request` | `UPDATE` | `update` | `project`, `iid`, `body` (1-10000, bez quick actions i `@all`) | `mcp-gitlab-write` | jw. |

Tytuły (`@ToolTitle`): `list_projects` - "Lista projektów grupy", `get_file` - "Pobierz plik z repozytorium",
`get_merge_request` - "Pobierz merge request", `comment_merge_request` - "Dodaj komentarz do merge requesta".

Zasoby C7 (`@ResourceRef`): `group` -> `GitLab::Group` (`list_projects`), `project` -> `GitLab::Project` (pozostałe;
merge request nie jest typem zasobu kontraktu). Identyfikator i rodzic zasobu to `full_path`/`path_with_namespace`
zwrócone przez GitLaba, nigdy argument; atrybuty: `classification` (topics projektu; grupa zawsze `[]`, klasyfikujemy tylko projekty) i
`ancestorClassifications` (każda grupa nad zasobem z `[]`, `{}` dla grupy najwyższego poziomu). Elementy wyniku `list_projects` (`path`) są
`@ResourceRef` typu `GitLab::Project`: zastrzeżone znikają z listy.

Wyniki: tekst pisany przez użytkowników GitLaba jest obiektem `UntrustedText` (CC-072):
`{kind: "untrusted-data", format: "text/plain" | "text/markdown", text, truncated}`; typ ma tylko te cztery
pola, więc rodzaj treści wynika z nazwy pola wyniku. Tekst jest normalizowany (`TextHygiene.normalize`: znaki
niewidoczne i sterujące zastępuje spacja), cięty do limitu i oddawany bez dalszych zmian. Pola, które GitLab
może zostawić puste, są `@Nullable` w schemacie wyniku. Pola `name`, `description`, `defaultBranch` (projekt) oraz `title`, `description`, `sourceBranch`,
`targetBranch`, `author`, `assignees[]`, `reviewers[]`, `labels[]` (merge request) i `content` (plik) mają ten
kształt; pozostałe to pola systemu. `get_file` zwraca obok `content` pole systemu `hiddenCharactersRemoved`
(liczba znaków sterujących, niewidocznych i zmieniających kierunek tekstu, które normalizacja usunęła z całego pliku;
0 dla czystego pliku, 1 dla pliku z BOM), żeby recenzent kodu nie dostał po cichu wersji oczyszczonej z ataku typu
Trojan Source.

Wywołania GitLab API v4 (wszystkie jako użytkownik, bez przekierowań):

| Narzędzie | Wywołania |
| --- | --- |
| `list_projects` | `GET /groups/:path`, `GET /groups/:id/projects?per_page=100&page=1&order_by=path&sort=asc&include_subgroups=false&with_shared=false` |
| `get_file` | `GET /projects/:path`, `HEAD /projects/:id/repository/files/:file_path?ref=`, `GET /projects/:id/repository/files/:file_path/raw?ref=<commit>` |
| `get_merge_request` | `GET /projects/:path`, `GET /projects/:id/merge_requests/:iid` |
| `comment_merge_request` | `GET /projects/:path`, `POST /projects/:id/merge_requests/:iid/notes` |

Konwencje: nazwy `snake_case`, czasownik i obiekt, bez prefiksu systemu (dokłada go gateway); opis mówi, co
narzędzie zwraca, bez instrukcji dla modelu.
