# mcp-gitlab: serwer MCP platformy dla GitLaba

Serwer MCP dla GitLaba (REST API v4), zbudowany na `mcp-platform-starter` 3.0.0-alpha-5 (Java 25, Spring Boot
4.1.0, Spring AI 2.0.0, kontrakty `mcp-contracts` 2.0.2). Stoi wyłącznie za gateway'em platformy: przyjmuje
ruch tylko po mTLS z wewnętrznym JWT gateway'a (C2), a do GitLaba woła z poświadczeniem użytkownika, które
gateway dołącza do każdego żądania (C3). Bez poprawnego JWT odpowiada 401, nie ma trybu anonimowego.

Logowania użytkowników, decyzji o dostępie do narzędzi i potwierdzeń akcji destrukcyjnych serwer nie robi:
to zadania gateway'a. Serwer odpowiada za narzędzia, adapter do GitLaba, poprawne błędy (C10) i audyt (C8),
który dokłada starter.

## Szybki start

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
./mvnw verify                 # build, testy przez TestGateway i atrapę backendu; jar: mcp-app/target/mcp-gitlab.jar
```

Testy nie potrzebują Dockera ani środowiska `mcp-platform-dev`: `TestGateway` z `mcp-platform-test` wystawia
JWKS z kluczy testowych kontraktów i podpisuje wewnętrzne JWT, a `StubBackend` (test) udaje API systemu.

## Moduły i miejsce każdej rzeczy

| Moduł | Co w nim jest | W tym serwerze |
| --- | --- | --- |
| `mcp-core` | domena i porty, czysta Java | `GitLabGroup`, `GitLabProject`, `ProjectList`, `RepositoryFile`, `MergeRequest`, `MergeRequestNote`, `UntrustedText`, `ResourceNotFoundException`, `FileNotReturnedException`; porty `GitLabUseCase`, `GitLabPort`, `ProjectResultFilter` |
| `mcp-application` | przypadki użycia (bez Springa) | `GitLabService`: ścieżka -> id przez GitLaba, dalej tylko id; limit i wykrywanie plików binarnych |
| `mcp-integration` | adaptery wyjściowe do systemu | `GitLabRestAdapter` (API v4, kodowanie segmentów, timeouty, bez przekierowań), `GitLabCredentials` (`PRIVATE-TOKEN` albo `Authorization: Bearer`), `GitLabBackendProperties` |
| `mcp-tools` | narzędzia MCP, powierzchnia publiczna | `GitLabTools` (4 narzędzia), `GitLabArguments` (walidacja), `ArgumentLimits`, `GitLabToolErrors` (wyjątki domeny -> kody C10) |
| `mcp-app` | bootstrap, wiring, jedyny jar, testy end-to-end | `McpToolsConfiguration` (lista narzędzi, limity w `inputSchema`), `StrictArgumentsToolCallback`, `ApplicationWiringConfiguration` |

Zależności idą do środka: `mcp-tools` -> `mcp-application` -> `mcp-core` <- `mcp-integration`; `mcp-app` składa
całość. Resolver zasobów (`@ResourceRef`, starter beta) dojdzie w `mcp-integration` obok adaptera, bo atrybuty
zasobu (projekt, klasyfikacja) pochodzą z systemu.

## Narzędzia

| Narzędzie | Ryzyko | Uprawnienie (C9) | Poświadczenie | Argumenty | Wynik |
| --- | --- | --- | --- | --- | --- |
| `list_projects` | `READ_ONLY` | `mcp-gitlab-read` | `user-oauth` albo `user-secret` | `group`: ścieżka grupy wg `GitLabGroupPath` z kontraktu, maks. 1024 | `{group, projects[{path, name, description, defaultBranch, archived, lastActivityAt, webUrl}], truncated}`; projekty bezpośrednio w grupie, bez udostępnionych z innych przestrzeni, maks. 100 |
| `get_file` | `READ_ONLY` | `mcp-gitlab-read` | jw. | `project`: `GitLabProjectPath`; `path`: 1-1024, względna, bez `..`, `.`, pustych segmentów, `\`, `%`, znaków sterujących; `ref` (opcjonalny): gałąź, tag albo SHA, maks. 255 | `{project, path, ref, commitId, size, content}`; plik binarny albo większy niż `MCP_GITLAB_FILE_MAX_BYTES` -> `invalid_argument` bez treści |
| `get_merge_request` | `READ_ONLY` | `mcp-gitlab-read` | jw. | `project`; `iid`: 1..2147483647 | `{project, iid, title, description, state, draft, sourceBranch, targetBranch, author, assignees, reviewers, labels, createdAt, updatedAt, mergedAt, detailedMergeStatus, hasConflicts, webUrl}` |
| `comment_merge_request` | `UPDATE` | `mcp-gitlab-write` | jw. | `project`; `iid`; `body`: 1-10000 znaków, bez linii zaczynających się od `/` (quick actions GitLaba) i znaków sterujących poza `\n`, `\r`, `\t` | `{project, mergeRequestIid, noteId, author, createdAt}` |

Nazwa widziana przez klienta to `gitlab__<narzędzie>` (prefiks dokłada gateway, C5). Pełny opis:
`docs/api/tools-catalog.md`.

Wspólne dla wszystkich narzędzi:

- Ścieżki grupy i projektu idą do GitLaba tylko raz, w zapytaniu `GET /groups/:id` albo `GET /projects/:id`
  (URL-encoded). GitLab zwraca id i kanoniczną ścieżkę (`full_path`, `path_with_namespace`); dalsze wywołania
  używają id, a wynik pokazuje ścieżkę kanoniczną. Serwer nie porównuje ścieżek jako napisów: GitLab jest
  nieczuły na wielkość liter i prowadzi starą ścieżkę przeniesionego projektu do nowej.
- Tekst pisany przez użytkowników GitLaba (treść pliku, opisy projektu i merge requestu) jest w wyniku
  obiektem `{kind: "untrusted-data", origin, text, truncated}`. Opisy są obcinane (projekt 2000, merge request
  20000 znaków).
- Błędy: `not_found_or_denied` (404 i 403 GitLaba, ten sam tekst), `credential_required` (brak poświadczenia
  albo 401 GitLaba), `invalid_argument` (walidacja przed GitLabem, nieznany argument, zły typ),
  `upstream_unavailable` (502-504, brak połączenia), `internal_error` (reszta; treść odpowiedzi GitLaba nigdy
  nie trafia do wyniku ani logu).
- Nie ma wyszukiwania po całym GitLabie: jedyna lista (`list_projects`) jest ograniczona do grupy z argumentu.

Poświadczenie wg `X-MCP-Credential-Type`: `user-secret` (PAT, schemat `Bearer` albo `Token`) idzie w nagłówku
`PRIVATE-TOKEN`; `user-oauth` i `token-exchange` (schemat `Bearer`) w `Authorization: Bearer`. Inne połączenia
(`service-account`, `Basic`, `ApiKey`) kończą się `internal_error` bez wywołania GitLaba.

## Jak przechodzi wywołanie

```
gateway --mTLS (CN mcp-gateway) + JWT C2 (aud mcp-gitlab, tool get_file) + X-MCP-Credential-->
  starter: InternalJwtFilter (inaczej 401) -> CredentialHeaderFilter -> AuditingToolCallback (C8)
  -> StrictArgumentsToolCallback: tylko zadeklarowane argumenty, typy JSON
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

Walidację `inputSchema` w MCP SDK serwer wyłącza (`McpToolsConfiguration`): SDK odpowiadało na naruszenie
schematu własnym tekstem spoza C10, zanim wywołanie trafiło do startera, więc bez zdarzenia audytu. Te same
reguły sprawdzają narzędzia i `StrictArgumentsToolCallback`; schemat nadal publikuje
`additionalProperties: false`, `maxLength`/`minLength` i `minimum`/`maximum`.

## Konfiguracja

Konfiguracja bazowa (`mcp-app/src/main/resources/application.yml`) jest produkcyjna: mTLS, JWKS gateway'a po
TLS z CA wewnętrznym, `jti` w Redisie. Profil `dev` wyłącza tylko TLS i służy testom; pozostałe ułatwienia
(klucze testowe, `jti` w pamięci, `mtls.required=false`) daje `TestGateway`, a starter odrzuca je poza `dev`.

| Zmienna | Znaczenie | Domyślnie |
| --- | --- | --- |
| `MCP_TLS_KEYSTORE`, `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD` | PKCS12: certyfikat serwera (SAN `mcp-gitlab.internal`) i CA wewnętrzne; truststore służy też do JWKS | wymagane |
| `MCP_GATEWAY_JWKS_URI` | JWKS wewnętrznego JWT, np. `https://mcp-gateway.internal:8443/.well-known/jwks.json` | wymagane |
| `MCP_GATEWAY_ISSUER` | `iss` C2 | `https://mcp-gateway.internal` |
| `MCP_CONNECT_URL` | strona portalu do połączenia konta, z `{system}`; trafia do `credential_required` | wymagane |
| `MCP_GITLAB_URL` | adres instancji GitLaba bez `/api/v4`, np. `https://gitlab.aiwcorpo.internal` | wymagane |
| `MCP_GITLAB_SSL_BUNDLE` | bundle SSL dla HTTPS GitLaba, np. `mcp-internal-ca`; puste = zaufanie JDK | puste |
| `MCP_GITLAB_FILE_MAX_BYTES` | największy plik zwracany przez `get_file` | `262144` |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis dla jednorazowego `jti` (użytkownik ACL `mcp-gitlab`, klucze `jti:mcp-gitlab:*`) | `localhost`, `6379`, `mcp-gitlab`, puste |
| `MCP_PORT`, `MCP_BIND` | port i adres nasłuchu | `8443`, `0.0.0.0` |

Endpoint MCP to `/mcp` (domyślne ustawienie startera, tak jak oczekuje gateway: `internal-url:
https://mcp-gitlab.internal:8443/mcp`). Bez JWT odpowiada tylko `/actuator/health` (i `liveness`,
`readiness`), ale TLS i tak wymaga certyfikatu klienta z CA wewnętrznego.

Serwer nie wstaje bez `MCP_GATEWAY_JWKS_URI`, `MCP_CONNECT_URL`, `MCP_GITLAB_URL` i plików TLS.

Na hoście ze środowiskiem `mcp-platform-dev` (sprawdzone 2026-10-27: health po mTLS 200, `POST /mcp` bez JWT
401, bez certyfikatu klienta zerwany handshake). Mocka GitLaba w `mcp-platform-dev` jeszcze nie ma, więc
`MCP_GITLAB_URL` wskazuje dowolny adres; do wywołań narzędzi służą testy na `StubBackend`:

```bash
dev=../mcp-platform-dev
export MCP_TLS_STORE_PASSWORD="$(grep '^TLS_STORE_PASSWORD=' $dev/.secrets/dev.env | cut -d= -f2-)"
export MCP_TLS_KEYSTORE=$dev/certs/mcp-gitlab/keystore.p12 MCP_TLS_TRUSTSTORE=$dev/certs/ca/truststore.p12
export MCP_REDIS_PASSWORD="$(grep '^REDIS_MCP_GITLAB_PASSWORD=' $dev/.secrets/dev.env | cut -d= -f2-)"
export MCP_PORT=18520 MCP_BIND=127.0.0.1 MCP_REDIS_PORT=16379 MCP_GITLAB_URL=http://127.0.0.1:18521
export MCP_GATEWAY_JWKS_URI=https://localhost:18445/.well-known/jwks.json
export MCP_CONNECT_URL='http://localhost:18090/connections?system={system}'
java -jar mcp-app/target/mcp-gitlab.jar
curl --cacert $dev/certs/ca/ca.crt --cert $dev/certs/mcp-gateway/tls.crt --key $dev/certs/mcp-gateway/tls.key \
     --resolve mcp-gitlab.internal:18520:127.0.0.1 https://mcp-gitlab.internal:18520/actuator/health   # 200
```

`POST /mcp` bez JWT daje 401, połączenie bez certyfikatu klienta jest zrywane w handshake'u.

## Testy

| Klasa | Co sprawdza |
| --- | --- |
| `ListProjectsToolTest` | ścieżka grupy rozwiązana przez GitLaba (wielkość liter), lista po id grupy z `with_shared=false`, projekt innej przestrzeni odrzucony, PAT w `PRIVATE-TOKEN`, brak poświadczenia i 401, 404 = 403, zła ścieżka nie dociera do GitLaba |
| `GetFileToolTest` | ścieżka pliku zakodowana jako jeden segment, treść przy commicie z HEAD, przeniesiony projekt -> ścieżka kanoniczna, gałąź domyślna, treść jako `untrusted-data`, plik binarny i za duży bez treści (duży nie jest pobierany), 404 = 403, walidacja `project`/`path`/`ref` |
| `GetMergeRequestToolTest` | tylko pola domeny, opis jako `untrusted-data`, brak poświadczenia i 401, 404 = 403, walidacja `iid` (zakres, typ) i `project` |
| `CommentMergeRequestToolTest` | notatka wysłana jako użytkownik (POST, treść), wynik bez echa treści, brak poświadczenia i 401, 404 = 403 (bez POST dla niewidocznego projektu), quick actions i limity odrzucone przed GitLabem |
| `ToolSurfaceAndCredentialTest` | `tools/list`: 4 narzędzia, `additionalProperties: false`, limit dla każdego argumentu, hinty ryzyka, opisy bez instrukcji; nieznany argument i zły typ -> `invalid_argument`; `user-oauth` w `Authorization`, PAT w `PRIVATE-TOKEN`, nieobsługiwane typy bez wywołania GitLaba; poświadczenie i treść błędu GitLaba poza wynikiem i logiem |
| `GatewayOnlyAccessTest` | bez JWT 401 (`tools/call`, `ping`, `initialize`), JWT dla innego serwera, innego narzędzia, wariant serwisowy i przeterminowany -> 401; bez JWT tylko health |
| `GitLabArgumentsTest` (mcp-tools) | wzorce ścieżek = wzorce z `common.schema.json`, wektory `resource-ids/gitlab-*.json` z kontraktu, ścieżka pliku, ref, iid, quick actions |
| `GitLabServiceTest` (mcp-application) | filtr przestrzeni nazw, `ProjectResultFilter`, odczyt przy commicie, limity, wykrywanie binariów, merge request innego projektu |

Nowe narzędzie dostaje test w tym samym stylu: klasa dziedziczy po `ServerTestSupport`, odpowiedź systemu
dopisuje się w `StubBackend`.

## Dokumentacja

- `docs/jak-napisac-narzedzie.md`: zasady i kolejność kroków przy nowym narzędziu.
- `docs/architecture/overview.md`: architektura i decyzje.
- `docs/security/SECURITY.md`: niezmienniki bezpieczeństwa w tym serwerze.
- `docs/api/tools-catalog.md`: rejestr narzędzi.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: wdrożenie.
