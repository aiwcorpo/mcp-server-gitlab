# mcp-gitlab: serwer MCP platformy dla GitLaba

Serwer MCP dla GitLaba (REST API v4), zbudowany na `mcp-platform-starter` 3.0.0-beta-13 (Java 25, Spring Boot
4.1.0, Spring AI 2.0.0, kontrakty `mcp-contracts` 6.2.1). Stoi wyłącznie za gateway'em platformy: przyjmuje
ruch tylko po mTLS z wewnętrznym JWT gateway'a (C2), a do GitLaba woła z poświadczeniem użytkownika, które
gateway dołącza do każdego żądania (C3). Bez poprawnego JWT odpowiada 401, nie ma trybu anonimowego.

Logowania użytkowników, decyzji o dostępie do narzędzi i potwierdzeń akcji destrukcyjnych serwer nie robi:
to zadania gateway'a. Serwer odpowiada za narzędzia, adapter do GitLaba, poprawne błędy (C10) i audyt (C8),
który dokłada starter.

## Szybki start

```bash
# JDK 25 w JAVA_HOME (macOS: /usr/libexec/java_home -v 25)
./mvnw verify                 # build, testy przez TestGateway i atrapę backendu; jar: mcp-app/target/mcp-gitlab.jar
```

Testy nie potrzebują Dockera ani środowiska `mcp-platform-dev`: `TestGateway` z `mcp-platform-test` wystawia
JWKS z kluczy testowych kontraktów i podpisuje wewnętrzne JWT, a `StubBackend` (test) udaje API systemu.

## Moduły i miejsce każdej rzeczy

| Moduł | Co w nim jest | W tym serwerze |
| --- | --- | --- |
| `mcp-core` | domena i porty, czysta Java | `GitLabGroup`, `GitLabProject`, `BackendPage`, `RepositoryFile`, `MergeRequest`, `Issue`, `Pipeline`, `JobLog`, `Commit`, `FileDiff` i inne wyniki; wyjątki `ResourceNotFoundException`, `FileNotReturnedException`, `BackendRefusedException`; porty wejściowe `GitLabUseCase`, `RepositoryUseCase`, `MergeRequestUseCase`, `IssueUseCase`, `PipelineUseCase`, wyjściowe `GitLabPort`, `RepositoryPort`, `MergeRequestPort`, `IssuePort`, `PipelinePort`; `AncestorTopics`, `GitLabGroupResource`, `GitLabProjectResource` (zasób z topics przodków) |
| `mcp-application` | przypadki użycia (bez Springa) | `GitLabService`, `RepositoryService`, `MergeRequestService`, `IssueService`, `PipelineService`: ścieżka -> id przez GitLaba, dalej tylko id; limit i wykrywanie plików binarnych; `JobLogText` (kody ANSI, wzorce poświadczeń, ogon logu) |
| `mcp-integration` | adaptery wyjściowe do systemu | `GitLabHttp` (jeden klient API v4: kodowanie segmentów przez `GitLabQuery`, timeouty, limity odpowiedzi, bez przekierowań, https poza `dev`, mapowanie statusów), adaptery `GitLabRestAdapter`, `RepositoryRestAdapter`, `MergeRequestRestAdapter`, `IssueRestAdapter`, `PipelineRestAdapter`, `GitLabResponses` (tolerancyjne odczyty), `GitLabCapabilities` i `GitLabVersionProbe` (wersja GitLaba), `BoundedBodyInterceptor`, `BackendResponseException`, `GitLabCredentials` (`PRIVATE-TOKEN` albo `Authorization: Bearer`), `GitLabBackendProperties` |
| `mcp-tools` | narzędzia MCP, powierzchnia publiczna | `GitLabTools`, `RepositoryTools`, `MergeRequestTools`, `IssueTools`, `PipelineTools` (30 narzędzi), `PageCursors`, `GitLabArguments` (walidacja, druga linia za schematem), `GitLabToolErrors` (wyjątki domeny -> kody C10) |
| `mcp-app` | bootstrap, wiring, jedyny jar, testy end-to-end | `McpToolsConfiguration` (lista narzędzi przez `SchemaLimitedToolCallbacks` ze startera), `ApplicationWiringConfiguration` |

Zależności idą do środka: `mcp-tools` -> `mcp-application` -> `mcp-core` <- `mcp-integration`; `mcp-app` składa
całość. Resolvery zasobów (`GitLabGroupResolver`, `GitLabProjectResolver`, `AncestorLookup`) są w `mcp-integration`
obok adaptera, bo atrybuty zasobu (ścieżka kanoniczna, topics) pochodzą z systemu.

## Narzędzia

Trzydzieści narzędzi: 20 do odczytu (`READ_ONLY`) i 10 zmieniających (`UPDATE`). Poświadczenie każdego: `user-oauth` albo
`user-secret` (patrz niżej). `project` to ścieżka projektu wg `GitLabProjectPath` z kontraktu, `group` wg `GitLabGroupPath`
(maks. 1024 znaki), `iid` to numer w projekcie (1..2147483647), `cursor` to `nextCursor` poprzedniego wyniku. Teksty wolne
(`body`, `description`, `title`, `query`) mają limity w schemacie; pole z `?` jest opcjonalne.

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

Argumenty `group` i `project` są `@ResourceRef` (typy `GitLab::Group` i `GitLab::Project`). Merge request, zgłoszenie,
pipeline i zadanie nie są osobnymi zasobami polityki: są adresowane przez projekt i numer, więc polityka ocenia projekt.
Osobny typ `GitLab::MergeRequest` wymaga zmiany kontraktu i jest zapisany jako propozycja CC-216 (`CONTRACT_CHANGES.md` w
`mcp-contracts`). Tytuł jest częścią `definitionHash`.

Nazwa widziana przez klienta to `gitlab__<narzędzie>` (prefiks dokłada gateway, C5). Pełny opis:
`docs/api/tools-catalog.md`.

Czego serwer celowo nie ma: wywołania dowolnej ścieżki API ani GraphQL, wyszukiwania po całym GitLabie, uruchamiania
pipeline'u ani zadań ręcznych (zmienne CI uruchamiają dowolne dane na runnerach projektu), zapisu do repozytorium
(commity, gałęzie, pliki) i usuwania czegokolwiek. `merge_merge_request` i `approve_merge_request` są `UPDATE`
(decyzja W-8): zmieniają merge request w imieniu użytkownika, a wymagania GitLaba (rola, wynik pipeline'u, liczba
zatwierdzeń, chroniona gałąź) nadal obowiązują. Oba wymagają pełnego id commita (`expectedHeadSha`, w `headSha` z
`get_merge_request`): gałąź, która ruszyła po recenzji, kończy się odmową GitLaba (`invalid_argument`, stały tekst o
konflikcie ze stanem projektu), a nie zatwierdzeniem czy scaleniem niewidzianej zmiany.

### Powtarzalność narzędzi `UPDATE`

Wywołanie `UPDATE` jest dostarczane co najmniej raz (po `502` z balancera klient może je powtórzyć). Narzędzia dobrane tak,
żeby powtórzenie było nieszkodliwe: `update_merge_request` i `update_issue` ustawiają wartości (etykiety to pełny zbiór),
`create_merge_request` jest odrzucane przez GitLaba, gdy dla tych gałęzi jest już otwarty merge request,
`approve_merge_request` dodaje do zbioru, `merge_merge_request` zwraca `alreadyMerged` dla merge requesta scalonego na tym
samym commicie, `retry_pipeline` i `cancel_pipeline` działają na zadaniach w danym stanie. Powtórzenie dubluje wpis tylko w
`comment_merge_request`, `comment_issue` i `create_issue`; opis narzędzia to mówi.

### Odmowy GitLaba zależne od stanu

`409` (zmieniony commit, drugi otwarty MR) i `400`, `405`, `406`, `422` (szkic, nierozwiązane blokady, brak gałęzi) to
`invalid_argument` ze stałym tekstem, bez treści odpowiedzi GitLaba. `404` i `403` to `not_found_or_denied`, także dla tokenu
z samym `read_api` użytego do zapisu (GitLab odpowiada `403 insufficient_scope`). `approve` i `merge` GitLab odpowiada `401`, gdy
użytkownik nie może ich wykonać (własny merge request, brak roli); serwer sprawdza wtedy raz `GET /user` tym samym tokenem:
działający token to `not_found_or_denied`, wygasły to `credential_required`.

### Przynależność do projektu

Każde wywołanie jest adresowane przez id projektu, który starter już sprawdził (`/projects/:id/...`), i odpowiedź jest
potwierdzana: merge request, zgłoszenie, pipeline i zadanie muszą nazywać ten sam projekt (`project_id`, `pipeline.project_id`),
notatka ten sam projekt i numer. Niezgodność to `not_found_or_denied` (pojedynczy element listy jest pomijany). Log zadania jest
czytany dopiero po tym, jak rekord zadania potwierdzi, że należy do pipeline'u tego projektu. Plik: `X-Gitlab-File-Path`
musi zgadzać się z żądaną ścieżką.

Wspólne dla wszystkich narzędzi:

- Ścieżki grupy i projektu idą do GitLaba tylko raz, w zapytaniu `GET /groups/:id` albo `GET /projects/:id`
  (URL-encoded). GitLab zwraca id i kanoniczną ścieżkę (`full_path`, `path_with_namespace`); dalsze wywołania
  używają id, a wynik pokazuje ścieżkę kanoniczną. Serwer nie porównuje ścieżek jako napisów: GitLab jest
  nieczuły na wielkość liter i prowadzi starą ścieżkę przeniesionego projektu do nowej.
- Tekst pisany przez użytkowników GitLaba jest w wyniku obiektem `UntrustedText` z kontraktu (CC-072):
  `{kind: "untrusted-data", format: "text/plain" | "text/markdown", text, truncated}` (typ
  `pl.aiwcorpo.mcp.platform.text.UntrustedText` startera; narzędzia publikują `outputSchema`). Dotyczy treści
  pliku, opisów (markdown), tytułu merge requestu, nazw projektów, etykiet, gałęzi i nazw użytkowników. Tekst
  wychodzi bez zmiany treści (markdown zostaje markdownem, `<script>` z opisu zostaje tekstem, klient nie
  renderuje go jako HTML); kolejność: `TextHygiene.normalize`, cięcie do limitu, fabryka `plainCut`|`markdownCut`. Pole, które
  GitLab może zostawić puste (opis, autor, `mergedAt`, status scalania, adres), bywa `null`. Limity:
  tytuł 1000, nazwa, etykieta, gałąź i użytkownik 255, opis projektu 2000, opis merge requestu 20000 znaków.
  Stany, flagi, znaczniki czasu, ścieżki kanoniczne i adresy URL to pola systemu, nie dane użytkownika.
- Błędy: `not_found_or_denied` (404 i 403 GitLaba, ten sam tekst), `credential_required` (brak poświadczenia
  albo 401 GitLaba), `invalid_argument` (walidacja przed GitLabem, nieznany argument, zły typ),
  `upstream_unavailable` (502-504, brak połączenia), `internal_error` (reszta; treść odpowiedzi GitLaba nigdy
  nie trafia do wyniku ani logu).
- Nie ma wyszukiwania po całym GitLabie: lista (`list_projects`) jest ograniczona do grupy z argumentu, a `search_code`, `list_issues` i pozostałe do jednego projektu.
- Listy zwracają `{items, nextCursor, truncated, omitted}` (`PagedResult` startera). `nextCursor` to podpisany kursor związany z użytkownikiem, narzędziem i zapytaniem (ważny 15 minut); nie jest uprawnieniem, każda strona jest czytana od nowa poświadczeniem użytkownika. Nie ma `total`. `truncated` znaczy, że serwer uciął treść elementu (diff, notatka), nie że jest następna strona. Instancje za jednym gateway'em muszą mieć ten sam `MCP_PAGING_CURSOR_KEY`.

Typy poświadczeń (`manifest.accepted-credential-types`): `[user-oauth, user-secret]`; pierwszy, `user-oauth`, jest typem
domyślnym systemu (`context.credentialType` w Cedar). Adapter wybiera nagłówek wg `X-MCP-Credential-Type`:
`user-oauth` (schemat `Bearer`) idzie w `Authorization: Bearer`, `user-secret` (PAT, schemat `Bearer` albo `Token`)
w `PRIVATE-TOKEN`. Żądanie z poświadczeniem, w którym nagłówka typu brak, ma wartość pisaną inaczej (`User-OAuth`)
albo spoza zbioru (`service-account`, `token-exchange`), odrzuca starter jako `internal_error` przed Cedar i bez
wywołania GitLaba, z audytem `policy_decision` i powodem `credential_type_mismatch`. Schemat niepasujący do typu
(`Basic`, `ApiKey`) daje `credential_required` (zdarzenie `credential_unsupported` w logu), też bez wywołania GitLaba.

## Zasoby i polityki (M3)

Przed metodą narzędzia starter pyta GitLaba poświadczeniem użytkownika (`GitLabGroupResolver`,
`GitLabProjectResolver`) i ocenia politykę Cedar z podpisanej paczki (ta sama co w gateway'u) na zasobie takim,
jakim opisuje go GitLab: kanoniczna ścieżka i `classification`.

Decyzja (2026-10-28): w GitLabie klasyfikujemy tylko projekty. `classification` projektu to jego `topics`.
`classification` grupy jest zawsze pusta (serwer nie czyta `topics` z odpowiedzi grupy; API v4 ich dla grup nie ma).
`attrs.ancestorClassifications` powstaje ze ścieżki kanonicznej zasobu: każda grupa-przodek z pustą listą (dla
`hr/payroll/team/app`: `hr`, `hr/payroll`, `hr/payroll/team`; dla grupy najwyższego poziomu `{}`; rodzic projektu
dostaje mapę bez siebie). Nie ma wywołań API po przodków, więc grupa nadrzędna niewidoczna dla użytkownika niczego nie
zmienia. Skutek: `restricted` na grupie nie zamyka jej poddrzewa; zamyka tylko projekt z tym topikiem.

- Brak zasobu, 403 z GitLaba i projekt z `restricted` w topics dają ten sam `not_found_or_denied` z tym samym
  tekstem. Brak grupy narzędzia to `access_denied`.
- Odpowiedzi GitLaba (grupa listingu i strona jej projektów) są pamiętane do końca wywołania (`CallCache`, klucz:
  kontekst wywołania startera, wielkość liter pomijana). `list_projects` czyta stronę projektów grupy raz; `ResultFilter`
  rozwiązuje wyniki jednym `fetchAll`, który bierze projekty z tej strony zamiast `GET /projects/:path` na każdy.
- `list_projects` jest wyszukiwaniem w zakresie jednej grupy: serwer buduje zapytanie (id grupy, `with_shared=false`,
  `include_subgroups=false`, 100 na stronę), model podaje tylko grupę. Grupa jest `@ResourceRef`, więc o dostępie do
  niej decyduje polityka (grupa bez grantu to `not_found_or_denied`, bez ujawniania, że istnieje); `QueryScope` nie
  jest potrzebny. Ścieżka każdego projektu w wyniku to `@ResourceRef`: projekty z `restricted` w topics znikają z
  listy, bez błędu. `truncated` mówi tylko, że GitLab ma następną stronę.

## Jak przechodzi wywołanie

```
gateway --mTLS (CN mcp-gateway) + JWT C2 (aud mcp-gitlab, tool get_file) + X-MCP-Credential-->
  starter: InternalJwtFilter (inaczej 401) -> CredentialHeaderFilter -> AuditingToolCallback (C8)
  -> starter: argumenty wobec inputSchema (nieznane, typy, pattern, długości) -> invalid_argument
  -> GitLabTools.getFile: walidacja project, path, ref
  -> GitLabService -> GitLabRestAdapter: CredentialContext.require("gitlab") + GitLabCredentials
  -> GET  {MCP_GITLAB_URL}/api/v4/projects/team-a%2Fapp                          -> id 42, default_branch
  -> HEAD {MCP_GITLAB_URL}/api/v4/projects/42/repository/files/src%2Fmain.py?ref=main  -> X-Gitlab-Size, X-Gitlab-Commit-Id
  -> GET  {MCP_GITLAB_URL}/api/v4/projects/42/repository/files/src%2Fmain.py/raw?ref=<commit>
```

Rozmiar pliku jest sprawdzany przed pobraniem treści (HEAD), a treść czytana przy commicie z HEAD, więc
przesunięcie gałęzi między wywołaniami nie podmieni pliku. Adapter nie podąża za przekierowaniami
(poświadczenie nie może pójść pod inny adres), czyta tylko pola domeny i nie wkłada treści odpowiedzi
GitLaba do żadnego komunikatu.

Argumenty sprawdza starter w `AuditingToolCallback` wobec `inputSchema` (MCP SDK ma walidację wyłączoną):
naruszenie to C10 `invalid_argument` ze zdarzeniem audytu, bez wartości i bez nazw spoza schematu. Schemat
publikuje `SchemaLimitedToolCallbacks` z `@ArgumentLimits` na argumentach: `additionalProperties: false`,
`maxLength`/`minLength`, `minimum`/`maximum`, a dla ścieżek grupy i projektu `contractPattern`
(`GitLabGroupPath`, `GitLabProjectPath` z `common.schema.json`, bez kopii wzorca). `GitLabArguments` powtarza
sprawdzenie z tych samych wzorców (czytanych z kontraktu) i dokłada reguły spoza schematu: ścieżka pliku, ref,
quick actions i `@all` w komentarzu.

## Wspierane wersje GitLaba

Serwer jest dla GitLaba self-managed (CE i EE), `/api/v4`. Przetestowany zakres to **17.0 do 19.x** (fixtures 17.8 i 19.5 w
`mcp-app/src/test/resources/fixtures/`, pisane ręcznie z udokumentowanych odpowiedzi, nie nagrane z instancji). Minimalna
wspierana wersja: **17.0**. Wersje 16.11 i starsze oraz 20 i nowsze działają w trybie `degraded` (niżej), bez gwarancji;
GitLab nie ma wersji LTS, a poprawki bezpieczeństwa wychodzą tylko dla trzech ostatnich wydań miesięcznych, więc instancja
starsza niż kilka miesięcy jest sama w sobie ryzykiem. GitLab.com (SaaS) i Dedicated nie są celem pierwszego wydania.

API v4 tylko przybywa, więc nie ma adapterów per wersja. Odpowiedzi są czytane tolerancyjnie (nieznane pola pomijane,
brakujące to `null`), a pole przemianowane przez GitLaba z obu nazw, nowszą pierwszą: `detailed_merge_status` albo
`merge_status`, `merge_user` albo `merged_by`, `draft` albo `work_in_progress` albo przedrostek tytułu `Draft:`.
Jedyna różnica w żądaniu: filtr szkiców w `list_merge_requests` to `draft=yes|no` od 19.0, `wip=yes|no` w 17.x i 18.x, a w
wersji spoza zakresu serwer nie wysyła żadnego z nich (mógłby go nie znać i zwrócić listę bez filtra) i odfiltrowuje szkice
sam, na pobranej stronie.

`GET /api/v4/version` GitLab odpowiada tylko uwierzytelnionemu, a serwer nie ma własnego poświadczenia (sonda platformy działa
poza żądaniem). Wersję serwer poznaje więc z pierwszego żądania z poświadczeniem użytkownika (przy rozwiązywaniu projektu) i
odświeża raz na godzinę; błąd tego zapytania nigdy nie psuje wywołania. Do pierwszego żądania oraz dla wersji spoza zakresu
stan to `degraded` (log `backend capability`, metryka `mcp_backend_capability{probe="gitlab"}`, szczegół `backendCapability`
w `/actuator/health`); w nim wszystkie narzędzia działają, a filtr szkiców jest lokalny. Wersja jest też w logu jako
`event=backend_version`.

## Wymagane uprawnienia konta i scope tokenu

Serwer działa jako użytkownik, więc może tyle, ile jego token i rola w projekcie:

| Zakres użycia | Token osobisty (`user-secret`) | Token OAuth (`user-oauth`) | Rola w projekcie |
| --- | --- | --- | --- |
| tylko odczyt (20 narzędzi) | `read_api` | `read_api` | Reporter wystarcza do kodu, MR, zgłoszeń, pipeline'ów; log zadania wymaga Developera, jeśli projekt tak ustawił widoczność logów |
| zmiany (10 narzędzi) | `api` | `api` | Developer do komentarzy, MR, zgłoszeń, ponawiania; scalenie i zatwierdzenie wg reguł projektu (chronione gałęzie, zatwierdzenia) |

`api` to szeroki sekret: w portalu zalecane są osobne połączenia, `read_api` do odczytu i `api` tylko tam, gdzie polityka
dopuszcza zapis. Token tylko do odczytu użyty przy zapisie daje `not_found_or_denied`. Konto serwisowe do sondy nie jest potrzebne.

## Instalacja

VM z systemd: `deploy/` i `docs/operations/deployment-vm-systemd.md` (`deploy/scripts/install.sh`, plik zmiennych z
`deploy/systemd/mcp-gitlab.env.template`, unit z sandboxem). Kontener: `Dockerfile` w katalogu głównym (po `./mvnw verify`,
jar `mcp-app/target/mcp-gitlab.jar`; tag `ci/run.sh image`), zmienne z tabeli "Konfiguracja" jako środowisko, certyfikaty jako
zamontowane pliki. Na obu: Redis (`jti`), paczka polityk, certyfikat serwera z SAN `mcp-gitlab.internal` i CA wewnętrzne,
`MCP_GITLAB_URL` (https) oraz, przy kilku instancjach, wspólny `MCP_PAGING_CURSOR_KEY`. Rejestracja manifestu: patrz niżej.

## Codzienna obsługa

- Zdrowie: `/actuator/health` (po mTLS), stan `backendCapability` i metryka `mcp_backend_capability`: `degraded` oznacza wersję
  spoza 17.0 do 19.x albo jeszcze nie poznaną, nie awarię.
- Po aktualizacji GitLaba: pierwsze żądanie użytkownika po upływie godziny poznaje nową wersję. Jeśli wersja wyszła poza 19.x, `degraded` w logu przypomina o przeglądzie fixtures.
- Rotacja klucza kursorów (`MCP_PAGING_CURSOR_KEY`): kursory w locie przestają działać (ważą 15 minut), reszta bez zmian.
- Nowa wersja serwera zmienia manifest: zbuduj go (`mcp-app/target/mcp-manifest.json`), zarejestruj, zatwierdź nowe narzędzia.
- Logi nie zawierają poświadczeń ani treści odpowiedzi GitLaba; błąd `internal_error` z linii `tool_failed` ma klasę wyjątku.

## Pierwsze uruchomienie u klienta (tylko GET)

1. `GET <MCP_GITLAB_URL>/api/v4/version` tokenem `read_api` (curl z nagłówkiem `PRIVATE-TOKEN`): wersja musi mieścić się w 17.0 do 19.x.
2. Uruchom serwer z `MCP_GITLAB_URL` i sprawdź w logu `backend capability` oraz `event=backend_version` po pierwszym żądaniu.
3. Przez gateway wywołaj tylko narzędzia odczytu na znanym projekcie: `get_project`, `list_repository_tree`, `list_merge_requests`,
   `list_pipelines`, `get_job_log` na jednym zadaniu. Porównaj z interfejsem GitLaba: czy pola szkicu, statusu scalenia i autora scalenia są
   wypełnione, czy paginacja (`nextCursor`) prowadzi do drugiej strony.
4. Narzędzia `UPDATE` zostaw niezatwierdzone w gateway'u do czasu przeglądu polityk.

## Konfiguracja

Konfiguracja bazowa (`mcp-app/src/main/resources/application.yml`) jest produkcyjna: mTLS, JWKS gateway'a po
TLS z CA wewnętrznym, `jti` w Redisie. Profil `dev` (`application-dev.yml`, testy i uruchomienie lokalne)
wyłącza TLS i mTLS, trzyma `jti` w pamięci i dopuszcza klucze testowe; starter odrzuca te ustawienia poza `dev`.

| Zmienna | Znaczenie | Domyślnie |
| --- | --- | --- |
| `MCP_TLS_KEYSTORE`, `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD` | PKCS12: certyfikat serwera (SAN `mcp-gitlab.internal`) i CA wewnętrzne; truststore służy też do JWKS | wymagane |
| `MCP_GATEWAY_JWKS_URI` | JWKS wewnętrznego JWT, np. `https://mcp-gateway.internal:8443/.well-known/jwks.json` | wymagane |
| `MCP_GATEWAY_ISSUER` | `iss` C2 | `https://mcp-gateway.internal` |
| `MCP_CONNECT_URL` | strona portalu do połączenia konta, z `{system}`; trafia do `credential_required` | wymagane |
| `MCP_GITLAB_URL` | adres instancji GitLaba bez `/api/v4`, np. `https://gitlab.aiwcorpo.internal`; poza `dev` tylko `https` | wymagane |
| `MCP_GITLAB_ALLOW_PLAIN_HTTP` | `true` dopuszcza `http` poza `dev`, wyłącznie dla mocków `mcp-platform-dev` (poświadczenie idzie wtedy otwartym tekstem; ostrzeżenie w logu) | `false` |
| `MCP_GITLAB_SSL_BUNDLE` | bundle SSL dla HTTPS GitLaba, np. `mcp-internal-ca`; puste = zaufanie JDK | puste |
| `MCP_GITLAB_FILE_MAX_BYTES` | największy plik zwracany przez `get_file`; nie może przekraczać `gitlab.backend.max-response-size` (1 MB) ani 400 KiB (treść po escapowaniu JSON musi zmieścić się w wyniku 1 MiB), inaczej serwer nie wstaje | `262144` |
| `MCP_GITLAB_MAX_LOG_SIZE` | największy log zadania, który `get_job_log` czyta, żeby uciąć jego koniec (GitLab nie zwraca samego końca); większy log jest odrzucany | `8MB` |
| `MCP_PAGING_CURSOR_KEY` | klucz HMAC kursorów (Base64, min. 32 bajty, `openssl rand -base64 32`); taki sam na każdej instancji; bez niego każda losuje własny i kursor z innej instancji jest odrzucany | puste |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis dla jednorazowego `jti` (użytkownik ACL `mcp-gitlab`, klucze `jti:mcp-gitlab:*`) | `localhost`, `6379`, `mcp-gitlab`, puste |
| `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE` | paczka polityk (podpisana, Ed25519): adres `https`, id klucza i plik klucza publicznego; serwer nie wstaje bez pierwszej paczki | wymagane |
| `MCP_POLICY_STATE_DIR` | katalog na ostatnią przyjętą rewizję (osobny na instancję); bez niego restart przyjąłby starszą paczkę | wymagane poza `dev` |
| `MCP_INTERNAL_URL`, `MCP_ACCEPTED_CREDENTIAL_TYPES` | adres serwera w manifeście (`https://mcp-gitlab.internal:8443/mcp`) i przyjmowane typy poświadczeń | wymagane; `user-oauth,user-secret` (pierwszy to typ domyślny) |
| `MCP_PORT`, `MCP_BIND` | port i adres nasłuchu | `8443`, `0.0.0.0` |

### Manifest i rejestracja w gateway'u

Do rejestracji idzie artefakt budowy, nie odpowiedź działającego serwera: `mcp-app/target/mcp-manifest.json`.
Powstaje w zwykłym buildzie (`./mvnw package` albo `verify`, także z `-DskipTests`): `ManifestExport` uruchamia w
fazie `package` `exec-maven-plugin` (`mcp-app/pom.xml`, `manifest.export.skip=false`). Czyta konfigurację produkcyjną
(`application.yml` bez profilu) i listę narzędzi z `McpToolsConfiguration`, nie startuje serwera i używa tego samego
`ManifestGenerator` z tymi samymi kontrolami co serwer przy starcie, więc manifest, który gateway by odrzucił,
zatrzymuje build. `internalUrl` jest informacyjny (z `MCP_INTERNAL_URL`, a gdy go brak,
`https://mcp-gitlab.internal:8443/mcp`). `ManifestTest` pilnuje, że narzędzia tego pliku są identyczne z `tools/list`
działającego serwera (bez `definitionHash`, który obejmuje też typy poświadczeń ustawione w testach), że manifest nie ma
`environment` i że przechodzi `ManifestRules`. Nowa klasa narzędzi trafia i do `McpToolsConfiguration`, i do
`ManifestExport.manifest()`. CI bierze ten plik z `mcp-app/target/` po buildzie przeglądanego commitu (przy innej
ścieżce, np. `registry-register.sh` w `mcp-platform-dev`, podaje się katalog z plikiem) i rejestruje certyfikatem
`mcp-registry-ci-mcp-gitlab`.
Przy wdrożeniu zatwierdzane są tylko narzędzia `READ_ONLY`; narzędzia `UPDATE` (w tym `merge_merge_request` i
`approve_merge_request`) czekają na `approve` administratora. Obniżenie `risk` nie jest odrzucane
(CC-167): `PUT` kończy się stanem `pending_approval` z `riskLowered: true`, a w dół prowadzi tylko `approve` z
`acceptRiskLowering`. W profilu `devstack` manifest ma `"environment": "dev"` i gateway w trybie produkcyjnym go
odrzuca (`environment_mismatch`).

### CI

Cała logika jest w `ci/run.sh` (`make ci` woła `ci/run.sh verify`); `.gitlab-ci.yml`, `Jenkinsfile` i `.github/workflows/ci.yml`
to cienkie nakładki, więc to samo działa lokalnie, w obrazie `maven:3.9.12-eclipse-temurin-25` i w każdym z trzech systemów.

| Krok | Co robi |
| --- | --- |
| `ci/run.sh verify` (domyślny) | `hygiene`, `mvnw clean verify`, potem kontrola, że każdy moduł z `CI_TEST_MODULES` uruchomił testy (pominięte nie liczą się) i że powstał `mcp-app/target/mcp-manifest.json` |
| `ci/run.sh hygiene` | cudzysłowy, spacje i wielokropek spoza ASCII oraz ślady narzędzi w źródłach (perl czyta przez `<<>>`) |
| `ci/run.sh image` | `docker build` jaru z `verify` jako `mcp-server-gitlab:<prefiks>-<sha7>` (prefiks z `CI_IMAGE_TAG_PREFIX`, domyślnie `m5`); tylko gałąź `main`, wymaga demona Dockera |

Inny argument kończy się kodem 2. Merge request i pull request nie dostają sekretów ani gniazda Dockera: testy nie
potrzebują kontenerów, a rejestr Maven z artefaktami platformy (`MCP_MAVEN_REPOSITORY_URL`, zwykła zmienna) jest wtedy
czytany anonimowo (`.ci/settings-anonymous.xml`). Dane dostępu (`MCP_MAVEN_REPOSITORY_USER`, `_PASSWORD`; w GitLabie
zmienne Protected i Masked, w Jenkinsie poświadczenie `mcp-maven-registry`) widzi tylko gałąź `main`, na której działa też
krok `image` (runner z gniazdem Dockera; w GitLabie tag `docker-socket` musi należeć do runnera chronionego (protected),
inaczej gniazdo trafiłoby w ręce merge requestu; w GitHubie sekrety są w Environment `ci-main`). Moduły z wymaganymi
testami: `CI_TEST_MODULES` (domyślnie `mcp-application mcp-tools mcp-app`, `mcp-integration` nie ma testów), dolna granica dla `mcp-app`: `CI_APP_MIN_TESTS` (20).
Rejestracja manifestu w gateway'u (`PUT` z certyfikatem `mcp-registry-ci-mcp-gitlab`) jest osobnym krokiem pipeline'u
serwera, bo wymaga sekretu, którego repo nie zawiera.

### Metryki do alertów

Licznik `mcp_audit_authentication_suppressed_total` (tagi `component=server`, `reason`) rośnie, gdy starter pomija
nadmiarowe zdarzenia `authentication` (ten sam nadawca w pętli odrzuceń). Wzrost oznacza, że audyt nie widzi
każdej odmowy uwierzytelnienia: alert powinien reagować na dodatni przyrost w 5 minutach. Dawna nazwa
`mcp_server_audit_authentication_suppressed_total` nie istnieje.

Endpoint MCP to `/mcp` (domyślne ustawienie startera, tak jak oczekuje gateway: `internal-url:
https://mcp-gitlab.internal:8443/mcp`). Bez JWT odpowiada tylko `/actuator/health` (i `liveness`,
`readiness`), ale TLS i tak wymaga certyfikatu klienta z CA wewnętrznego.

Poza profilem `dev` serwer nie wstaje bez `MCP_GATEWAY_JWKS_URI` (tylko `https`), `MCP_CONNECT_URL`, `MCP_GITLAB_URL` (`https`) i plików TLS.

`java` w `PATH` może być starsza niż 25 (wtedy `UnsupportedClassVersionError`), dlatego poniżej zawsze
`$JAVA_HOME/bin/java`.

## Uruchomienie lokalne bez środowiska

Profil `dev` (musi być jedynym aktywnym profilem): zwykłe HTTP na 127.0.0.1, `jti` w pamięci, bez Redisa,
certyfikatów i sekretów. Gateway'a nie ma, więc każde żądanie z JWT kończy się 503 (JWKS niedostępny); ten tryb
służy do sprawdzenia, że serwer wstaje i odrzuca ruch bez JWT. Narzędzia sprawdzają testy (`./mvnw verify`).

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
./mvnw -B verify
SPRING_PROFILES_ACTIVE=dev MCP_PORT=18520 "$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-gitlab.jar
# w drugim terminalu:
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18520/actuator/health          # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' http://127.0.0.1:18520/mcp            # 401
```

Wartości domyślne profilu są w `mcp-app/src/main/resources/application-dev.yml` (backend
`http://localhost:18081`, portal `http://localhost:18090`); każdą można nadpisać zmienną z tabeli wyżej.

Ryzyko: `application-dev.yml` jest w jarze produkcyjnym, a profil `dev` przyjmuje JWT podpisane opublikowanymi
kluczami testowymi kontraktów i nie wymaga certyfikatu klienta. Dlatego `dev` słucha wyłącznie na adresie pętli
zwrotnej: starter odmawia startu z `dev` przy innym `server.address`, a test
`DevProfileLoopbackTest` pilnuje tego w serwerze. Nie ustawiaj `SERVER_ADDRESS` ani `MCP_BIND` przy `dev` i
nigdy nie włączaj `dev` na hoście ani w kontenerze, do którego łączy się gateway: tam działa wyłącznie
konfiguracja bazowa z mTLS.

`SPRING_PROFILES_ACTIVE=dev` wolno ustawić tylko w poleceniu z terminala, jak wyżej. Nie trafia do `.env`, szablonów
compose, jednostek systemd ani obrazu: od 3.0.0-alpha-9 starter nie startuje z `dev` w kontenerze, na Kubernetesie
i pod systemd, a `application-dev.yml` nie ustawia już TLS, mTLS ani magazynu `jti` (robi to starter).

## Uruchomienie z mcp-platform-dev (mTLS)

Konfiguracja produkcyjna na hoście, z certyfikatami i Redisem środowiska `mcp-platform-dev` (`make up` już
uruchomione). Działa tylko dla systemu z listy `SystemId`: środowisko ma certyfikaty (`certs/mcp-<system>`, SAN
`mcp-<system>.internal`, z `scripts/gen-certs.sh`) i użytkowników Redisa (`mcp-<system>`, `redis/start.sh`)
dokładnie dla tych pięciu systemów. Nowy system wymaga najpierw zmiany kontraktu (`SystemId`) i dopisania go w
ws-01 (lista `identities` w `gen-certs.sh`, pętla w `redis/start.sh`); bez tego zostaje tryb `dev`.

Magazyny PKCS12 i Redis są chronione hasłami z `$MCP_PLATFORM_DEV/.secrets/dev.env`, więc ten wariant wymaga
ich odczytu. Przykład dla tego serwera (system `gitlab`):

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
export MCP_PLATFORM_DEV=$HOME/src/mcp/mcp-platform-dev                         # ścieżka bezwzględna
export MCP_TLS_STORE_PASSWORD="$(grep '^TLS_STORE_PASSWORD=' "$MCP_PLATFORM_DEV/.secrets/dev.env" | cut -d= -f2-)"
export MCP_REDIS_PASSWORD="$(grep '^REDIS_MCP_GITLAB_PASSWORD=' "$MCP_PLATFORM_DEV/.secrets/dev.env" | cut -d= -f2-)"
export MCP_TLS_KEYSTORE="$MCP_PLATFORM_DEV/certs/mcp-gitlab/keystore.p12"
export MCP_TLS_TRUSTSTORE="$MCP_PLATFORM_DEV/certs/ca/truststore.p12"
export MCP_PORT=18521 MCP_BIND=127.0.0.1 MCP_REDIS_PORT=16379
export MCP_GITLAB_URL=http://127.0.0.1:18529 MCP_GITLAB_ALLOW_PLAIN_HTTP=true   # mocka GitLaba w środowisku jeszcze nie ma
export MCP_GATEWAY_JWKS_URI=https://localhost:18445/.well-known/jwks.json
export MCP_CONNECT_URL='http://localhost:18090/connections?system={system}'
"$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-gitlab.jar
# w drugim terminalu (te same zmienne MCP_PLATFORM_DEV), certyfikat klienta gateway'a:
TLS=(--cacert "$MCP_PLATFORM_DEV/certs/ca/ca.crt" --resolve mcp-gitlab.internal:18521:127.0.0.1
     --cert "$MCP_PLATFORM_DEV/certs/mcp-gateway/tls.crt" --key "$MCP_PLATFORM_DEV/certs/mcp-gateway/tls.key")
curl -s -o /dev/null -w '%{http_code}\n' "${TLS[@]}" https://mcp-gitlab.internal:18521/actuator/health   # 200
curl -s -o /dev/null -w '%{http_code}\n' "${TLS[@]}" -X POST -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' https://mcp-gitlab.internal:18521/mcp                     # 401
```

Sprawdzone 2026-10-27 (health po mTLS 200, `POST /mcp` bez JWT 401). Połączenie bez certyfikatu klienta jest zrywane w
handshake'u. Pełne wywołanie narzędzia wymaga gateway'a z hosta (`mcp-gateway/scripts/run-dev-host.sh`, JWKS na
`localhost:18445`) skonfigurowanego na ten serwer.

## Testy

| Klasa | Co sprawdza |
| --- | --- |
| `ListProjectsToolTest` | ścieżka grupy rozwiązana przez GitLaba (wielkość liter), lista po id grupy z `with_shared=false`, projekt innej przestrzeni odrzucony, PAT w `PRIVATE-TOKEN`, brak poświadczenia i 401, 404 = 403, zła ścieżka nie dociera do GitLaba |
| `GetFileToolTest` | ścieżka pliku zakodowana jako jeden segment, treść przy commicie z HEAD, przeniesiony projekt -> ścieżka kanoniczna, gałąź domyślna, treść jako `untrusted-data`, plik binarny i za duży bez treści (duży nie jest pobierany), 404 = 403, walidacja `project`/`path`/`ref` |
| `GetMergeRequestToolTest` | tylko pola domeny, tytuł, opis, gałęzie, etykiety i użytkownicy jako `untrusted-data` z formatem, brak poświadczenia i 401, 404 = 403, walidacja `iid` (zakres, `long`, typ) i `project` |
| `CommentMergeRequestToolTest` | notatka wysłana jako użytkownik (POST, treść), wynik bez echa treści, brak poświadczenia i 401, 404 = 403 (bez POST dla niewidocznego projektu), quick actions i limity odrzucone przed GitLabem |
| `RepositoryToolsTest` | projekt, drzewo, wyszukiwanie w kodzie (zakres projektu, kursor związany z zapytaniem, sfałszowany kursor), gałęzie, commity, diff z ucięciem i licznikiem ukrytych znaków, zastrzeżony projekt zamknięty dla odczytu i zapisu |
| `MergeRequestToolsTest` | lista w kształcie 17.8 i 19.5, filtr szkiców wg wersji (`wip`, `draft`, lokalnie dla wersji spoza zakresu), wersja poznana z poświadczenia użytkownika, pola przemianowane, notatki innego MR odrzucone, create/update/approve/merge z odmowami (409, 400, 405, 401 z tokenem działającym i wygasłym), `alreadyMerged`, zapis bez grupy zapisu |
| `IssueToolsTest` | lista bez zgłoszeń innego projektu, zgłoszenie innego projektu = `not_found_or_denied`, notatki, create/update/comment, quick actions i `@all` odrzucone |
| `PipelineToolsTest` | pipeline'y i zadania innego projektu odrzucone, log bez kodów ANSI i z `[redacted]`, ogon logu, log ponad limit, zadanie obcego projektu nieczytane, retry/cancel |
| `ToolSurfaceAndCredentialTest` | `tools/list`: dokładnie katalog 30 narzędzi (`ToolCatalog`), brak narzędzia typu dowolne wywołanie API lub GraphQL, `additionalProperties: false`, limit dla każdego argumentu, hinty ryzyka, opisy bez instrukcji; nieznany argument i zły typ -> `invalid_argument` (bez nazwy argumentu w wyniku i logu); wzorce w schemacie = wzorce kontraktu; `user-oauth` w `Authorization`, PAT w `PRIVATE-TOKEN`, schemat niepasujący do typu -> `credential_required` (zdarzenie `credential_unsupported` w logu) bez wywołania GitLaba; poświadczenie i treść błędu GitLaba poza wynikiem i logiem |
| `BackendResponseTest` | nieczytelna odpowiedź GitLaba, przekierowanie (nie podążamy, nie jest "brakiem"), timeout -> `upstream_unavailable`, odpowiedź ponad limit; treść odpowiedzi poza wynikiem i logiem |
| `ProductionConfigurationTest` | domyślne typy produkcji `user-oauth,user-secret` w tej kolejności (zamiana daje czerwony test); `client-auth: need` poza `dev`, `https` dla GitLaba poza `dev` (wyjątek `ALLOW_PLAIN_HTTP`), `dev` tylko na pętli zwrotnej |
| `DevProfileLoopbackTest` | reguła adresu `dev` (`assertDevProfileLoopbackRule`) i obecność guardów w kontekście serwera (`assertServerIsGuarded`) |
| `GatewayOnlyAccessTest` | bez JWT 401 (`tools/call`, `ping`, `initialize`), JWT dla innego serwera, innego narzędzia, wariant serwisowy i przeterminowany -> 401; token z claimem `vs` albo `vsc` (usunięte w kontraktach 5.x) -> 401 bez dojścia do GitLaba; bez JWT tylko health |
| `GitLabArgumentsTest` (mcp-tools) | wektory `resource-ids/gitlab-*.json` z kontraktu, limit ścieżki = limit kontraktu, ścieżka pliku, ref, iid, quick actions, `@all` |
| `ResourcePolicyTest` | projekt z topikiem `restricted` -> `not_found_or_denied` identyczne jak dla nieistniejącego (odczyt i zapis); grupa nie jest klasyfikowana, także gdy GitLab poda dla niej topics (`hr`, `hr/payroll/team`); brak wywołań po grupy-przodków; zapis bez grantu zakresu odrzucony przed resolwerem (GitLab nie dostaje żadnego żądania); lista bez projektu zastrzeżonego i obcego; `access_denied` bez grupy; zakres zapisu |
| `CredentialTypeTest` | OAuth w `Authorization: Bearer`, PAT w `PRIVATE-TOKEN`; `service-account` na narzędziach odczytu i zapisu i `token-exchange` -> `internal_error`; brak nagłówka typu, `User-OAuth` i nieznany typ -> `internal_error`; żądanie bez poświadczenia -> `credential_required`; w każdym przypadku odmowy GitLab nie dostaje żądania |
| `ManifestTest` | `/mcp-manifest` przechodzi `ManifestRules`, definicje = `tools/list`, `definitionHash`, tytuły, zamknięte schematy, `resourceRefs` i ryzyko; narzędzia z `ManifestExport` (plik z budowy) równe `tools/list`, bez `environment`, typy `user-oauth`, `user-secret` |
| `GitLabServiceTest` (mcp-application) | filtr przestrzeni nazw, odczyt przy commicie, limity, wykrywanie binariów, merge request innego projektu |

Nowe narzędzie dostaje test w tym samym stylu: klasa dziedziczy po `ServerTestSupport`, odpowiedź systemu
dopisuje się w `StubBackend`.

## Dokumentacja

- `docs/jak-napisac-narzedzie.md`: zasady i kolejność kroków przy nowym narzędziu.
- `docs/architecture/overview.md`: architektura i decyzje.
- `docs/security/SECURITY.md`: niezmienniki bezpieczeństwa w tym serwerze.
- `docs/api/tools-catalog.md`: rejestr narzędzi.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: wdrożenie.
