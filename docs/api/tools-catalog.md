# Katalog narzędzi

Narzędzia to powierzchnia publiczna serwera. Każda zmiana nazwy, opisu, schematu albo ryzyka zmienia
`definitionHash` (C6) i wymaga ponownego zatwierdzenia w gateway'u, więc przechodzi przez code review razem z
tą tabelą.

Klient widzi `gitlab__<nazwa>`. Poświadczenie z C3: `user-oauth` (domyślny) -> `Authorization: Bearer`, `user-secret` -> `PRIVATE-TOKEN`; inne typy odrzuca starter.

| Narzędzie | Tytuł | Ryzyko | Akcja | Uprawnienie (C9) | Argumenty | Wynik |
| --- | --- | --- | --- | --- | --- | --- |
| `list_projects` | Lista projektów grupy | `READ_ONLY` | `read` | `mcp-gitlab-read` | `group`, `cursor?` | strona projektów bezpośrednio w grupie (nie z podgrup, nie udostępnionych), 100 na stronę: ścieżka, nazwa, opis, gałąź domyślna, archived, ostatnia aktywność, adres |
| `get_project` | Pobierz projekt | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project` | ścieżka, nazwa, opis, gałąź domyślna, widoczność, archived, ostatnia aktywność, adres |
| `get_file` | Pobierz plik z repozytorium | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `path`, `ref?` | plik UTF-8 przy commicie z HEAD refa, `hiddenCharactersRemoved`; binarny albo większy niż `MCP_GITLAB_FILE_MAX_BYTES` -> `invalid_argument` bez treści |
| `list_repository_tree` | Zawartość katalogu repozytorium | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `path?`, `ref?`, `cursor?` | jeden poziom katalogu, 100 na stronę: ścieżka i typ (`blob`, `tree`, `commit`) |
| `search_code` | Szukaj w kodzie projektu | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `query` (1-200 znaków), `ref?`, `cursor?` | szukanie słowa lub frazy w plikach jednego projektu (zakres wymusza ścieżka żądania), 20 trafień na stronę: ścieżka, numer linii, fragment |
| `list_branches` | Lista gałęzi | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `search?`, `cursor?` | 50 na stronę: nazwa, domyślna, chroniona, scalona, ostatni commit |
| `list_commits` | Lista commitów | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `ref?`, `path?`, `since?`, `until?`, `cursor?` | 50 na stronę: id, tytuł, autor, czas, adres; filtry ref, ścieżka, okres |
| `get_commit` | Pobierz commit | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `sha` (7-40 znaków hex) | id, tytuł, pełny komunikat, autor, czas, liczba dodanych i usuniętych linii |
| `get_commit_diff` | Zmiany w commicie | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `sha`, `cursor?` | 20 plików na stronę: ścieżki, flagi nowy/przeniesiony/usunięty, diff ucięty do 16000 znaków na plik |
| `list_merge_requests` | Lista merge requestów | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `state?`, `author?`, `reviewer?`, `labels?`, `targetBranch?`, `draft?`, `cursor?` | 30 na stronę, od ostatnio zmienionych; filtry: stan, autor, recenzent, etykiety (wszystkie), gałąź docelowa, szkic |
| `get_merge_request` | Pobierz merge request | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `iid` | tytuł, opis, stan, szkic, gałęzie, autor, osoby, etykiety, czasy, status scalenia, konflikt, kto scalił, `headSha`, adres |
| `list_merge_request_diffs` | Zmiany w merge requeście | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `iid`, `cursor?` | jak `get_commit_diff`, dla merge requesta |
| `list_merge_request_notes` | Komentarze merge requesta | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `iid`, `cursor?` | 20 na stronę, w kolejności napisania: autor, treść (do 4000 znaków), notatka systemowa, czas |
| `comment_merge_request` | Dodaj komentarz do merge requesta | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `iid`, `body` | `{project, mergeRequestIid, noteId, author, createdAt}`; powtórzenie dubluje komentarz |
| `create_merge_request` | Utwórz merge request | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `sourceBranch`, `targetBranch`, `title`, `description?` | projekt, numer, tytuł, stan, szkic, adres; GitLab odrzuca drugi otwarty MR dla tych samych gałęzi, więc powtórzenie nic nie zmienia |
| `update_merge_request` | Zmień merge request | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `iid`, `title?`, `description?`, `labels?`, `targetBranch?` | tytuł, opis, etykiety (pełny zbiór), gałąź docelowa; stan docelowy, powtórzenie nic nie zmienia |
| `approve_merge_request` | Zatwierdź merge request | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `iid`, `expectedHeadSha` (40 znaków hex) | zatwierdzenie dla wskazanego commita; zbiór zatwierdzających, powtórzenie nic nie dodaje |
| `merge_merge_request` | Scal merge request | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `iid`, `expectedHeadSha`, `squash?`, `removeSourceBranch?` | scalenie dla wskazanego commita (UPDATE wg decyzji W-8); już scalony na tym commicie -> `alreadyMerged: true` |
| `list_issues` | Lista zgłoszeń | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `state?`, `author?`, `assignee?`, `labels?`, `search?`, `cursor?` | 30 na stronę; filtry: stan, autor, przypisany, etykiety, słowo w tytule i opisie |
| `get_issue` | Pobierz zgłoszenie | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `iid` | tytuł, opis, stan, typ, autor, osoby, etykiety, czasy, adres |
| `list_issue_notes` | Komentarze zgłoszenia | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `iid`, `cursor?` | jak `list_merge_request_notes`, dla zgłoszenia |
| `create_issue` | Utwórz zgłoszenie | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `title`, `description?`, `labels?` | projekt, numer, tytuł, stan, adres; powtórzenie tworzy kolejne zgłoszenie |
| `update_issue` | Zmień zgłoszenie | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `iid`, `title?`, `description?`, `labels?`, `state?` | tytuł, opis, etykiety (pełny zbiór), stan `opened` albo `closed`; stan docelowy |
| `comment_issue` | Dodaj komentarz do zgłoszenia | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `iid`, `body` | jak `comment_merge_request`, dla zgłoszenia |
| `list_pipelines` | Lista pipeline'ów | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `ref?`, `status?`, `cursor?` | 30 na stronę, od najnowszych: id, status, źródło, ref, commit, kto uruchomił, czasy, czas trwania, adres; filtry ref i status |
| `get_pipeline` | Pobierz pipeline | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `pipelineId` | jak wiersz `list_pipelines` |
| `list_pipeline_jobs` | Zadania pipeline'u | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `pipelineId`, `cursor?` | 100 na stronę: id, nazwa, etap, status, czy porażka dozwolona, czasy, adres |
| `get_job_log` | Log zadania CI | `READ_ONLY` | `read` | `mcp-gitlab-read` | `project`, `jobId`, `lines?` | ostatnie linie logu (domyślnie 200, najwyżej 2000, do 60000 znaków), bez kodów ANSI, z `[redacted]` w miejscu tokenów GitLaba, nagłówków `Authorization`, haseł w URL i kluczy prywatnych; `redactions` to liczba zamian; log większy niż `MCP_GITLAB_MAX_LOG_SIZE` jest odrzucany |
| `retry_pipeline` | Ponów nieudane zadania pipeline'u | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `pipelineId` | ponawia nieudane i anulowane zadania; pozostałe zostają, więc powtórzenie nie startuje pipeline od nowa |
| `cancel_pipeline` | Anuluj pipeline | `UPDATE` | `update` | `mcp-gitlab-write` | `project`, `pipelineId` | anuluje trwające zadania; zakończony pipeline zostaje bez zmian |

Tytuły (`@ToolTitle`) są w kolumnie "Tytuł"; wchodzą do `definitionHash`.

Zasoby C7 (`@ResourceRef`): `group` -> `GitLab::Group` (`list_projects`), `project` -> `GitLab::Project` (pozostałe;
merge request, zgłoszenie i pipeline nie są typami zasobu kontraktu, propozycja w `CONTRACT_CHANGES.md`). Identyfikator i rodzic zasobu to `full_path`/`path_with_namespace`
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
| `get_project` | `GET /projects/:path`, `GET /projects/:id` |
| `list_repository_tree` | `GET /projects/:id/repository/tree?path=&ref=&per_page=100&page=` |
| `search_code` | `GET /projects/:id/search?scope=blobs&search=&ref=&per_page=20&page=` |
| `list_branches` | `GET /projects/:id/repository/branches?search=&per_page=50&page=` |
| `list_commits` | `GET /projects/:id/repository/commits?ref_name=&path=&since=&until=&per_page=50&page=` |
| `get_commit` | `GET /projects/:id/repository/commits/:sha?stats=true` |
| `get_commit_diff` | `GET /projects/:id/repository/commits/:sha/diff?per_page=20&page=` |
| `list_merge_requests` | `GET /projects/:id/merge_requests?state=&author_username=&reviewer_username=&labels=&target_branch=&draft=yes\|no` albo `wip=yes\|no` wg wersji, `order_by=updated_at&sort=desc&per_page=30&page=` |
| `list_merge_request_diffs` | `GET /projects/:id/merge_requests/:iid/diffs?per_page=20&page=` |
| `list_merge_request_notes` | `GET /projects/:id/merge_requests/:iid/notes?order_by=created_at&sort=asc&per_page=20&page=` |
| `create_merge_request` | `POST /projects/:id/merge_requests` |
| `update_merge_request` | `PUT /projects/:id/merge_requests/:iid` |
| `approve_merge_request` | `POST /projects/:id/merge_requests/:iid/approve` (`sha`); przy 401 `GET /user` |
| `merge_merge_request` | `PUT /projects/:id/merge_requests/:iid/merge` (`sha`, opcjonalnie `squash`, `should_remove_source_branch`); przy odmowie `GET /projects/:id/merge_requests/:iid`, przy 401 `GET /user` |
| `list_issues` | `GET /projects/:id/issues?state=&author_username=&assignee_username=&labels=&search=&per_page=30&page=` |
| `get_issue` | `GET /projects/:id/issues/:iid` |
| `list_issue_notes` | `GET /projects/:id/issues/:iid/notes?per_page=20&page=` |
| `create_issue`, `update_issue` | `POST /projects/:id/issues`, `PUT /projects/:id/issues/:iid` (`state_event=close\|reopen`) |
| `comment_issue` | `POST /projects/:id/issues/:iid/notes` |
| `list_pipelines` | `GET /projects/:id/pipelines?ref=&status=&order_by=id&sort=desc&per_page=30&page=` |
| `get_pipeline` | `GET /projects/:id/pipelines/:pid` |
| `list_pipeline_jobs` | `GET /projects/:id/pipelines/:pid/jobs?per_page=100&page=` |
| `get_job_log` | `GET /projects/:id/jobs/:jid`, `GET /projects/:id/jobs/:jid/trace` |
| `retry_pipeline`, `cancel_pipeline` | `POST /projects/:id/pipelines/:pid/retry`, `POST /projects/:id/pipelines/:pid/cancel` |
| (każde narzędzie z projektem, raz na godzinę) | `GET /version` poświadczeniem użytkownika |

Konwencje: nazwy `snake_case`, czasownik i obiekt, bez prefiksu systemu (dokłada go gateway); opis mówi, co
narzędzie zwraca, bez instrukcji dla modelu.
