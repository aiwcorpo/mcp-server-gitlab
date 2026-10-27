# STATUS - mcp-server-gitlab (ws-04)

M1: osiągnięty (2026-10-27) - serwer z template'u na starterze 3.0.0-alpha-5, cztery narzędzia GitLaba (`list_projects`, `get_file`, `get_merge_request` READ_ONLY; `comment_merge_request` UPDATE) z walidacją wg wzorców kontraktu, ścieżki rozwiązywane do id przez API v4, 49 testów na `StubBackend`, start produkcyjny po mTLS sprawdzony na hoście.

Starter: BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-alpha-5` (kontrakty 2.0.2, Spring Boot 4.1.0, Spring AI
2.0.0, Java 25). M2 nie jest zaczęty (manifest C6, `@ResourceRef`, `ResultFilter`).

## Co działa

- `server-id` `mcp-gitlab`, endpoint `/mcp`, konfiguracja produkcyjna z template'u (mTLS `need`, JWKS gateway'a
  po https, `jti` w Redisie). Dodana zmienna `MCP_GITLAB_FILE_MAX_BYTES` (domyślnie 262144).
- Narzędzia (szczegóły: README, `docs/api/tools-catalog.md`):

  | Narzędzie | Ryzyko | Uprawnienie C9 |
  | --- | --- | --- |
  | `list_projects(group)` | `READ_ONLY` | `mcp-gitlab-read` |
  | `get_file(project, path, ref?)` | `READ_ONLY` | `mcp-gitlab-read` |
  | `get_merge_request(project, iid)` | `READ_ONLY` | `mcp-gitlab-read` |
  | `comment_merge_request(project, iid, body)` | `UPDATE` | `mcp-gitlab-write` |

- Ścieżki grupy i projektu: wzorce `GitLabGroupPath`/`GitLabProjectPath` z `common.schema.json` (test porównuje
  wzorce i przechodzi wektory `resource-ids/gitlab-*.json` z jara kontraktów), potem `GET /groups/:path` albo
  `GET /projects/:path`; dalej tylko id, w wyniku ścieżka kanoniczna z GitLaba. Test z przeniesionym projektem
  (`team-a/old-app` -> `team-a/app`) i inną wielkością liter.
- `list_projects`: `with_shared=false`, `include_subgroups=false`, 100 na stronę, `truncated` z `X-Next-Page`;
  projekt z inną przestrzenią nazw niż rozwiązana grupa jest odrzucany po id.
- `get_file`: `path` bez `..`, `.`, pustych segmentów, `/` na początku i końcu, `\`, `%`, znaków sterujących,
  maks. 1024; `ref` jako zachowawczy podzbiór `git check-ref-format`, maks. 255; segmenty kodowane w całości
  (`EncodingMode.VALUES_ONLY`). HEAD przed pobraniem: plik za duży nie jest pobierany; treść czytana przy
  commicie z HEAD (`X-Gitlab-Commit-Id`). Plik binarny (NUL albo niepoprawny UTF-8) i za duży ->
  `invalid_argument` bez treści.
- `comment_merge_request`: linie zaczynające się od `/` są odrzucane, bo GitLab wykonałby je jako quick actions
  (`/merge`, `/approve`, `/assign`), czyli inną akcję niż komentarz; treść 1-10000 znaków.
- Treść z GitLaba (plik, opisy) w wyniku jako `{kind: "untrusted-data", origin, text, truncated}`.
- Poświadczenie wg `X-MCP-Credential-Type`: `user-secret` (schemat `Bearer` albo `Token`) -> `PRIVATE-TOKEN`;
  `user-oauth` i `token-exchange` (`Bearer`) -> `Authorization: Bearer`; inne -> `internal_error` bez
  wywołania GitLaba.
- Błędy: 404 i 403 -> ten sam `not_found_or_denied`; brak poświadczenia i 401 -> `credential_required`;
  obejście `defaultStatusHandler` z template'u zostaje (treść błędu GitLaba nie trafia do wyniku ani logu,
  test z odpowiedzią 500 z echem tokenu).
- Schemat wejścia: `additionalProperties: false` (Spring AI) oraz `maxLength`/`minLength`/`minimum`/`maximum` z
  adnotacji `@ArgumentLimits` (Spring AI ich nie generuje; tekst bez `maxLength` zatrzymuje start).
  Walidacja schematu w MCP SDK jest wyłączona, bo odpowiadała przed starterem tekstem spoza C10 i bez audytu;
  nieznane argumenty i złe typy JSON odrzuca `StrictArgumentsToolCallback` (`invalid_argument`, z audytem).
- Testy (`./mvnw verify`, JDK 25, bez Dockera): 49, 0 błędów.
  - mcp-app 33: `ListProjectsToolTest` 5, `GetFileToolTest` 7, `GetMergeRequestToolTest` 5,
    `CommentMergeRequestToolTest` 5, `ToolSurfaceAndCredentialTest` 8, `GatewayOnlyAccessTest` 3 (bez zmian
    poza nazwą narzędzia). Każde narzędzie: pozytywny, `credential_required` (brak i 401),
    `not_found_or_denied` (404 i 403), `invalid_argument` (każdy identyfikator).
  - mcp-tools 7 (`GitLabArgumentsTest`), mcp-application 9 (`GitLabServiceTest`).
- Smoke na hoście (port 18520, certyfikat `certs/mcp-gitlab`, użytkownik Redis `mcp-gitlab`): start,
  `/actuator/health` po mTLS z certyfikatem gateway'a 200, `POST /mcp` bez JWT 401, bez certyfikatu klienta
  handshake zerwany. Proces zatrzymany.

## Miejsce na QueryScope i ResultFilter (M3)

Jedno miejsce: port `ProjectResultFilter` (`mcp-core`), wołany w `GitLabService.listProjects` po pobraniu
listy i odrzuceniu obcych przestrzeni nazw. W M1 bean w `ApplicationWiringConfiguration` przepuszcza listę
(decyzję o wywołaniu podejmuje gateway, uprawnienia GitLaba działają, bo wołamy jako użytkownik). Ze starterem
beta: implementacja przez `ResultFilter.allowed` dla każdego projektu (`GitLab::Project`, rodzic `GitLab::Group`
z `namespace.full_path`, `classification` z topics przodków) oraz `queryConstraint` dla `gitlabGroups` linku:
grupa z argumentu musi leżeć w zawężeniu linku, zanim pójdzie zapytanie (w tym samym `listProjects`, przed
`listGroupProjects`). Dla narzędzi z identyfikatorem (`get_file`, `get_merge_request`,
`comment_merge_request`) `@ResourceRef` na `project` i resolver w `mcp-integration` z wyniku
`GET /projects/:path`.

## Odstępstwa od briefu

- `ref` w `get_file` opcjonalny (domyślna gałąź projektu); brief podaje `get_file(project, path, ref)`.
- `list_projects` zwraca pierwszą stronę (100, po ścieżce) z flagą `truncated`, bez stronicowania i bez
  podgrup; parametr strony można dodać w M2 (zmienia `definitionHash`).
- Brak `@ResourceRef`, `QueryScope`, `ResultFilter`, manifestu C6 (definicja M1 koordynatora).
- Testy na `StubBackend` w repo zamiast mocka z ws-01 (mocka GitLaba nie ma). Odpowiedzi wzorowane na
  dokumentacji API v4 (GitLab 17): `groups`, `projects`, `repository/files` (HEAD z `X-Gitlab-*`, `/raw`),
  `merge_requests`, `notes`; nie były porównane z działającym GitLabem.
- Własny `McpSyncServerCustomizer` zastępuje domyślny Spring AI (Spring AI przyjmuje dokładnie jeden), więc
  powtarza jego `immediateExecution(true)`; testy narzędzi sprawdzają, że poświadczenie dociera do GitLaba.

## Czego potrzebuje ws-01

- Mock GitLaba (API v4) w `mcp-platform-dev`, sprawdzający token (`PRIVATE-TOKEN` i `Authorization: Bearer`),
  z endpointami: `GET /api/v4/groups/:path`, `GET /api/v4/groups/:id/projects` (z `X-Next-Page`),
  `GET /api/v4/projects/:path` (także stara ścieżka przeniesionego projektu i inna wielkość liter),
  `HEAD /api/v4/projects/:id/repository/files/:path?ref=` (nagłówki `X-Gitlab-*`),
  `GET .../files/:path/raw?ref=`, `GET /api/v4/projects/:id/merge_requests/:iid`,
  `POST .../merge_requests/:iid/notes`; dane z przypadkami 403, 404, 401 dla unieważnionego tokenu, plik
  binarny i duży. Do M2 także dostawca OAuth (plan M2).
- Usługa `mcp-gitlab` w profilu `full` compose z `MCP_CONNECT_URL` (brak w compose, jak w STATUS template'u).

## Czego potrzebuje ws-02 (starter)

1. MCP SDK 2.0 waliduje argumenty względem `inputSchema` przed `AuditingToolCallback`: klient dostaje tekst
   `Tool (...) input validation failed: ...` (nie C10), a wywołanie nie ma zdarzenia audytu. Dotyczy każdego
   serwera z template'u (np. brak wymaganego argumentu). Propozycja: starter ustawia
   `validateToolInputs(false)` razem z `immediateExecution(true)` i sam odrzuca nieznane argumenty/złe typy
   z kodem `invalid_argument`. Obejście lokalne: `McpToolsConfiguration` + `StrictArgumentsToolCallback`.
2. Spring AI przyjmuje tylko jeden `McpSyncServerCustomizer` (`Optional`), więc własny customizer serwera wyłącza
   domyślny `immediateExecution(true)`. Starter mógłby wystawić własny punkt rozszerzenia albo sprawdzać przy
   starcie, że narzędzie wykonuje się na wątku żądania.
3. Limity długości w `inputSchema`: Spring AI nie generuje `maxLength`/`minimum`; adnotacja w rodzaju
   `@ArgumentLimits` i sprawdzenie przy starcie przydałyby się wszystkim serwerom.
4. `TestGateway.asGateway` zawsze wysyła `X-MCP-Credential-Type: user-secret`; serwer GitLaba testuje
   `user-oauth` własnym customizerem. Propozycja: przeciążenie z typem.
5. BOM nie zarządza `mcp-cedar-entities` (`ResourceIds`), więc wzorce GitLaba są skopiowane do
   `GitLabArguments` (test porównuje je z `common.schema.json`).

## Czego potrzebuje ws-03 (template)

- Te same obejścia co wyżej (wyłączona walidacja SDK, `ArgumentLimits`, ścisłe argumenty) warto przenieść do
  template'u albo startera, żeby pięć serwerów nie miało pięciu kopii.
- `docs/nowy-serwer.md` mówi o GitLabie tylko `PRIVATE-TOKEN` przez `credential.value()`; dla `user-oauth`
  GitLab chce `Authorization: Bearer` (`credential::applyTo`).

## Czego potrzebuje ws-05 (gateway)

- Dla `gitlab` gateway musi przekazywać `X-MCP-Credential-Type` zgodny z połączeniem (`user-secret` dla PAT,
  `user-oauth` dla OAuth) i schemat `Bearer` (albo `Token` dla PAT); inne kombinacje serwer odrzuca.

## Propozycje zmian kontraktu

Brak w M1.
