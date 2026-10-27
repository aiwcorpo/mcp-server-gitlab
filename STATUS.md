# STATUS - mcp-server-gitlab (ws-04)

M1: osiągnięty (2026-10-27, runda 2) - serwer z template'u na starterze 3.0.0-alpha-6, cztery narzędzia GitLaba (`list_projects`, `get_file`, `get_merge_request` READ_ONLY; `comment_merge_request` UPDATE), walidacja argumentów przez starter (C10 `invalid_argument` + C8), wzorce ścieżek przez `contractPattern`, każdy tekst z GitLaba jako `UntrustedText`, https poza `dev`, 67 testów na `StubBackend`, start w profilu `dev` i po mTLS sprawdzony na hoście.

Starter: BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-alpha-6` (kontrakty 2.0.2, Spring Boot 4.1.0, Spring AI
2.0.0, Java 25). M2 nie jest zaczęty (manifest C6, `@ResourceRef`, `ResultFilter`).

## Runda 2: znaleziska recenzji M1

| Znalezisko | Status |
| --- | --- |
| Wspólne 0 (alpha-5 wydane ponownie, serwer nie startował) | zrobione: BOM alpha-6, usunięty własny `@Primary McpSyncServerCustomizer` (`toolInputsCheckedByTheTools`) |
| Wspólne 1 (walidacja w starterze, nie w SDK) | zrobione: usunięty `StrictArgumentsToolCallback`; testy oczekują C10 `invalid_argument`; test, że nazwa nieznanego argumentu nie trafia do wyniku ani logu |
| Wspólne 2 (limity w `inputSchema`) | zrobione: `@ArgumentLimits` i `SchemaLimitedToolCallbacks.of(...)` ze startera, własna kopia usunięta; `contractPattern` dla `GitLabGroupPath`/`GitLabProjectPath`, test: wzorzec w schemacie = wzorzec kontraktu |
| Wspólne 4 (fabryka klienta: https, limit rozmiaru, 3xx) | zrobione lokalnie w adapterze (jak template) |
| Wspólne 5 (kształt oznaczenia treści) | zastosowane CC-072 (patrz G-M1) |
| Wspólne 3 (logi aplikacji i audyt w jednym stdout) | ws-03; serwer nie loguje nazw argumentów ani wartości |
| Wspólne 6 (kod błędu dla zasobu poza zakresem) | kategoria (b), bez zmian: gitlab zwraca `not_found_or_denied`; decyzja w C10/C7 |
| G-M1 tekst użytkowników poza `untrusted-data` | zrobione: tytuł MR, nazwy projektów, etykiety, gałęzie (w tym domyślna), nazwy użytkowników, opisy, treść pliku jako `{kind, format, origin, text, truncated}`; `format` `text/plain` albo `text/markdown`; testy na wstrzykniętych tytule i etykiecie |
| G-M2 `http://` poza `dev` | zrobione: `https` albo jawne `MCP_GITLAB_ALLOW_PLAIN_HTTP=true` (ostrzeżenie w logu); `ProductionConfigurationTest` |
| G-L1 brak limitu rozmiaru odpowiedzi | zrobione: `BoundedBodyInterceptor`, `max-response-size` 1 MB; start odmawia, gdy `MCP_GITLAB_FILE_MAX_BYTES` je przekracza |
| G-L2 wzmianki i obrazki w komentarzu | częściowo: odrzucane `@all` (po dowolnym znaku niebędącym znakiem słowa, bez względu na wielkość liter). Nie robione: wzmianki grup i użytkowników (nie do odróżnienia od zwykłego tekstu) oraz obrazki z obcych hostów; to decyzja polityki (potwierdzenie `comment_merge_request` w gateway'u, C11) |
| G-L3 liczba spoza `long` daje `internal_error` | zrobione: schemat startera (`maximum`) odrzuca ją jako `invalid_argument`, test z 10^29 |
| G-L4 własny `@Primary` customizer | zrobione (usunięty); starter pilnuje jednego customizera |

Zmiany z template'u od 7f65f8f: `BackendResponseException` bez treści, 3xx jako błąd, limit odpowiedzi,
`ProductionConfigurationTest`, `DevProfileLoopbackTest`, `BackendResponseTest` (nieczytelna odpowiedź,
przekierowanie, timeout, odpowiedź ponad limit), sekcje README o uruchomieniu lokalnym i z `mcp-platform-dev`,
`prometheus` niewystawiony, sandbox systemd, `install.sh`, `TRIAL_SYSTEM`/`contractSystemIsKnown`.

Nie przeniesione, bo poza zakresem: `outputSchema` dla narzędzi (zalecenie CC-072, objęcie kształtu
`definitionHash`; Spring AI `@Tool` go nie generuje, do ustalenia ze starterem w M2).

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
- Schemat wejścia: `additionalProperties: false`, `maxLength`/`minLength`/`pattern`/`minimum`/`maximum` z
  `@ArgumentLimits` i `SchemaLimitedToolCallbacks` startera (tekst bez `maxLength` zatrzymuje start). Starter
  sprawdza każde wywołanie wobec schematu: `invalid_argument` (C10) ze zdarzeniem C8, bez wartości i bez nazw
  spoza schematu. `GitLabArguments` zostaje drugą linią (wzorce czytane z kontraktu) i dokłada reguły spoza
  schematu.
- Testy (`./mvnw verify`, JDK 25, bez Dockera): 67, 0 błędów (mcp-application 9, mcp-tools 8, mcp-app 50).
  - mcp-app 33: `ListProjectsToolTest` 5, `GetFileToolTest` 7, `GetMergeRequestToolTest` 5,
    `CommentMergeRequestToolTest` 5, `ToolSurfaceAndCredentialTest` 8, `GatewayOnlyAccessTest` 3 (bez zmian
    poza nazwą narzędzia). Każde narzędzie: pozytywny, `credential_required` (brak i 401),
    `not_found_or_denied` (404 i 403), `invalid_argument` (każdy identyfikator).
  - mcp-tools 7 (`GitLabArgumentsTest`), mcp-application 9 (`GitLabServiceTest`).
- Smoke na hoście (port 18520, profil `dev` wg README): health 200, `POST /mcp` bez JWT 401; proces zatrzymany.
  Wariant mTLS (certyfikat `certs/mcp-gitlab`, Redis `mcp-gitlab`) sprawdzony w rundzie 1 na alpha-5.

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

- Wzorce z `common.schema.json` dla `GitLabArguments` czytam z zasobu kontraktu przy starcie klasy; helper w
  starterze (np. `ContractPatterns.of("GitLabProjectPath")`) usunąłby tę kopię kodu u pięciu serwerów.
- Zalecenie CC-072 (`outputSchema` z kształtem `UntrustedText`): potrzebny sposób deklaracji w
  `SchemaLimitedToolCallbacks`, bo `@Tool` nie generuje `outputSchema`.

## Czego potrzebuje ws-03 (template)

- `docs/nowy-serwer.md` mówi o GitLabie tylko `PRIVATE-TOKEN` przez `credential.value()`; dla `user-oauth`
  GitLab chce `Authorization: Bearer` (`credential::applyTo`).
- Logi aplikacji i audyt w jednym stdout bez escapowania (wspólne 3 z recenzji).

## Czego potrzebuje ws-05 (gateway)

- Dla `gitlab` gateway musi przekazywać `X-MCP-Credential-Type` zgodny z połączeniem (`user-secret` dla PAT,
  `user-oauth` dla OAuth) i schemat `Bearer` (albo `Token` dla PAT); inne kombinacje serwer odrzuca.

## Propozycje zmian kontraktu

Brak. Kształt `UntrustedText` (CC-072) zastosowany bez zmiany zależności od kontraktów; wzorzec `origin` jako pole
systemowe obok czterech wymaganych jest zgodny z `additionalProperties: true` definicji.
