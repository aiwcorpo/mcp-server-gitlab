# mcp-gitlab: serwer MCP platformy dla GitLaba

Serwer MCP dla GitLaba (REST API v4), zbudowany na `mcp-platform-starter` 3.0.0-alpha-6 (Java 25, Spring Boot
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
| `mcp-integration` | adaptery wyjściowe do systemu | `GitLabRestAdapter` (API v4, kodowanie segmentów, timeouty, limit odpowiedzi, bez przekierowań, https poza `dev`), `BoundedBodyInterceptor`, `BackendResponseException`, `GitLabCredentials` (`PRIVATE-TOKEN` albo `Authorization: Bearer`), `GitLabBackendProperties` |
| `mcp-tools` | narzędzia MCP, powierzchnia publiczna | `GitLabTools` (4 narzędzia), `GitLabArguments` (walidacja, druga linia za schematem), `GitLabToolErrors` (wyjątki domeny -> kody C10) |
| `mcp-app` | bootstrap, wiring, jedyny jar, testy end-to-end | `McpToolsConfiguration` (lista narzędzi przez `SchemaLimitedToolCallbacks` ze startera), `ApplicationWiringConfiguration` |

Zależności idą do środka: `mcp-tools` -> `mcp-application` -> `mcp-core` <- `mcp-integration`; `mcp-app` składa
całość. Resolver zasobów (`@ResourceRef`, starter beta) dojdzie w `mcp-integration` obok adaptera, bo atrybuty
zasobu (projekt, klasyfikacja) pochodzą z systemu.

## Narzędzia

| Narzędzie | Ryzyko | Uprawnienie (C9) | Poświadczenie | Argumenty | Wynik |
| --- | --- | --- | --- | --- | --- |
| `list_projects` | `READ_ONLY` | `mcp-gitlab-read` | `user-oauth` albo `user-secret` | `group`: ścieżka grupy wg `GitLabGroupPath` z kontraktu, maks. 1024 | `{group, projects[{path, name*, description*, defaultBranch*, archived, lastActivityAt, webUrl}], truncated}` (pola z `*` to `untrusted-data`, patrz niżej); projekty bezpośrednio w grupie, bez udostępnionych z innych przestrzeni, maks. 100 |
| `get_file` | `READ_ONLY` | `mcp-gitlab-read` | jw. | `project`: `GitLabProjectPath`; `path`: 1-1024, względna, bez `..`, `.`, pustych segmentów, `\`, `%`, znaków sterujących; `ref` (opcjonalny): gałąź, tag albo SHA, maks. 255 | `{project, path, ref, commitId, size, content}`; plik binarny albo większy niż `MCP_GITLAB_FILE_MAX_BYTES` -> `invalid_argument` bez treści |
| `get_merge_request` | `READ_ONLY` | `mcp-gitlab-read` | jw. | `project`; `iid`: 1..2147483647 | `{project, iid, title*, description*, state, draft, sourceBranch*, targetBranch*, author*, assignees*[], reviewers*[], labels*[], createdAt, updatedAt, mergedAt, detailedMergeStatus, hasConflicts, webUrl}` |
| `comment_merge_request` | `UPDATE` | `mcp-gitlab-write` | jw. | `project`; `iid`; `body`: 1-10000 znaków, bez linii zaczynających się od `/` (quick actions GitLaba), bez `@all` (także zapisanego odwołaniem znakowym HTML) i znaków sterujących poza `\n`, `\r`, `\t` | `{project, mergeRequestIid, noteId, author, createdAt}` |

Nazwa widziana przez klienta to `gitlab__<narzędzie>` (prefiks dokłada gateway, C5). Pełny opis:
`docs/api/tools-catalog.md`.

Wspólne dla wszystkich narzędzi:

- Ścieżki grupy i projektu idą do GitLaba tylko raz, w zapytaniu `GET /groups/:id` albo `GET /projects/:id`
  (URL-encoded). GitLab zwraca id i kanoniczną ścieżkę (`full_path`, `path_with_namespace`); dalsze wywołania
  używają id, a wynik pokazuje ścieżkę kanoniczną. Serwer nie porównuje ścieżek jako napisów: GitLab jest
  nieczuły na wielkość liter i prowadzi starą ścieżkę przeniesionego projektu do nowej.
- Tekst pisany przez użytkowników GitLaba jest w wyniku obiektem `UntrustedText` z kontraktu (CC-072):
  `{kind: "untrusted-data", format: "text/plain" | "text/markdown", origin, text, truncated}`. Dotyczy treści
  pliku, opisów (markdown), tytułu merge requestu, nazw projektów, etykiet, gałęzi i nazw użytkowników. Limity:
  tytuł 1000, nazwa, etykieta, gałąź i użytkownik 255, opis projektu 2000, opis merge requestu 20000 znaków.
  Stany, flagi, znaczniki czasu, ścieżki kanoniczne i adresy URL to pola systemu, nie dane użytkownika.
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
| `MCP_GITLAB_FILE_MAX_BYTES` | największy plik zwracany przez `get_file`; nie może przekraczać `gitlab.backend.max-response-size` (1 MB), inaczej serwer nie wstaje | `262144` |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis dla jednorazowego `jti` (użytkownik ACL `mcp-gitlab`, klucze `jti:mcp-gitlab:*`) | `localhost`, `6379`, `mcp-gitlab`, puste |
| `MCP_PORT`, `MCP_BIND` | port i adres nasłuchu | `8443`, `0.0.0.0` |

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
zwrotnej: starter (od 3.0.0-alpha-6) odmawia startu z `dev` przy innym `server.address`, a test
`DevProfileLoopbackTest` pilnuje tego w serwerze. Nie ustawiaj `SERVER_ADDRESS` ani `MCP_BIND` przy `dev` i
nigdy nie włączaj `dev` na hoście ani w kontenerze, do którego łączy się gateway: tam działa wyłącznie
konfiguracja bazowa z mTLS.

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
export MCP_PLATFORM_DEV=<workspace>/mcp-platform-dev           # ścieżka bezwzględna
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
| `ToolSurfaceAndCredentialTest` | `tools/list`: 4 narzędzia, `additionalProperties: false`, limit dla każdego argumentu, hinty ryzyka, opisy bez instrukcji; nieznany argument i zły typ -> `invalid_argument` (bez nazwy argumentu w wyniku i logu); wzorce w schemacie = wzorce kontraktu; `user-oauth` w `Authorization`, PAT w `PRIVATE-TOKEN`, nieobsługiwane typy bez wywołania GitLaba; poświadczenie i treść błędu GitLaba poza wynikiem i logiem |
| `BackendResponseTest` | nieczytelna odpowiedź GitLaba, przekierowanie (nie podążamy, nie jest "brakiem"), timeout -> `upstream_unavailable`, odpowiedź ponad limit; treść odpowiedzi poza wynikiem i logiem |
| `ProductionConfigurationTest` | `client-auth: need` poza `dev`, `https` dla GitLaba poza `dev` (wyjątek `ALLOW_PLAIN_HTTP`), `dev` tylko na pętli zwrotnej |
| `DevProfileLoopbackTest` | `dev` na `0.0.0.0` nie wstaje |
| `GatewayOnlyAccessTest` | bez JWT 401 (`tools/call`, `ping`, `initialize`), JWT dla innego serwera, innego narzędzia, wariant serwisowy i przeterminowany -> 401; bez JWT tylko health |
| `GitLabArgumentsTest` (mcp-tools) | wektory `resource-ids/gitlab-*.json` z kontraktu, limit ścieżki = limit kontraktu, ścieżka pliku, ref, iid, quick actions, `@all` |
| `GitLabServiceTest` (mcp-application) | filtr przestrzeni nazw, `ProjectResultFilter`, odczyt przy commicie, limity, wykrywanie binariów, merge request innego projektu |

Nowe narzędzie dostaje test w tym samym stylu: klasa dziedziczy po `ServerTestSupport`, odpowiedź systemu
dopisuje się w `StubBackend`.

## Dokumentacja

- `docs/jak-napisac-narzedzie.md`: zasady i kolejność kroków przy nowym narzędziu.
- `docs/architecture/overview.md`: architektura i decyzje.
- `docs/security/SECURITY.md`: niezmienniki bezpieczeństwa w tym serwerze.
- `docs/api/tools-catalog.md`: rejestr narzędzi.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: wdrożenie.
