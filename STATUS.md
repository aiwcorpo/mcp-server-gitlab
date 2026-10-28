# STATUS - mcp-server-gitlab (ws-04)

M3: osiągnięty (2026-10-28) - starter 3.0.0-beta-2 (kontrakty 3.1.0), `@ToolTitle` na 4 narzędziach, `@ResourceRef` na `group` i `project`, resolvery `GitLab::Group` i `GitLab::Project` z `classification` i `attrs.ancestorClassifications` (cache na czas wywołania, `fetchAll` przy listach), `ResultFilter` na `list_projects`, `GET /mcp-manifest` zgodny z `tools/list`, 99 testów (mcp-app 82, mcp-tools 10, mcp-application 7). Nie sprawdzone na działającym GitLabie ani w mcp-platform-dev (patrz Propozycje CC 1).

M1: osiągnięty (2026-10-27, runda 2; 2026-10-28 przeniesiony na starter 3.0.0-alpha-10) - serwer z template'u na starterze 3.0.0-alpha-10, cztery narzędzia GitLaba (`list_projects`, `get_file`, `get_merge_request` READ_ONLY; `comment_merge_request` UPDATE), walidacja argumentów przez starter (C10 `invalid_argument` + C8), wzorce ścieżek przez `contractPattern`, każdy tekst z GitLaba jako `UntrustedText`, https poza `dev`, 78 testów na `StubBackend`, start w profilu `dev` sprawdzony na hoście (mTLS: runda 2, alpha-6).

Starter: BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-beta-2` (kontrakty 3.1.0, Spring Boot 4.1.0, Spring AI
2.0.0, Java 25). Tag `v3.0.0-beta-3` nie istniał przy zamknięciu M3; przejście to podbicie `mcp-platform.version`
w `pom.xml` i przejrzenie MIGRATION "Z beta-2 na beta-3".

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
| G2-M1 `@all` zapisane odwołaniem znakowym HTML | poprawione: przed sprawdzeniem treść jest dekodowana do stanu stałego (odwołania numeryczne dziesiętne i szesnastkowe, z średnikiem i bez, `&commat;`, `&amp;`; bez względu na wielkość liter). GitLab renderuje notatkę CommonMarkiem, który dekoduje odwołanie raz, a wzmianki są szukane w wyrenderowanym tekście; `&amp;#64;all` CommonMark pokaże dosłownie, ale odrzucamy je i tak (kolejny konsument HTML może zdekodować drugi raz, a dosłowne `&#64;all` w komentarzu do MR nie ma zastosowania). Niezbieżne w 8 rundach -> `invalid_argument` (fail-closed). `&#x40all` przechodzi: odwołanie szesnastkowe bez średnika zjada `a`, to U+040A. Nie sprawdzone na działającym GitLabie (brak mocka, ws-01) | `GitLabArguments.decodedCharacterReferences`; `GitLabArgumentsTest.commentBodyCannotMentionEveryoneThroughCharacterReferences` (`&#64;all`, `&#x40;all`, `&commat;all`, `@&#97;ll`, `@a&#108;l`, wielkość liter, zagnieżdżenia, 9 poziomów, przypadki dozwolone) |
| G2-L1 `ref` w `get_file` przy braku argumentu to nazwa gałęzi domyślnej jako zwykły napis | otwarte, nie robione: poprawka zmienia kształt wyniku narzędzia (`ref` jako `UntrustedText` albo osobne pole `defaultBranch`), więc potrzebuje decyzji o kształcie z ws-01/ws-02; nazwę gałęzi wybiera maintainer projektu | `GitLabService.getFile`, `RepositoryFile` |
| G2-L2 wzmianki grup i obrazki z obcych hostów (reszta G-L2) | otwarte, bez zmiany względem G-L2: do czasu decyzji o potwierdzeniu `comment_merge_request` w gateway'u (C11) model pod wstrzyknięciem może wyprowadzić dane przez URL obrazka. Kierunek bez zmiany kontraktu: odrzucanie `![...](http...)` do hostów innych niż `MCP_GITLAB_URL` | `GitLabArguments.commentBody` |

Zmiany z template'u od 7f65f8f: `BackendResponseException` bez treści, 3xx jako błąd, limit odpowiedzi,
`ProductionConfigurationTest`, `DevProfileLoopbackTest`, `BackendResponseTest` (nieczytelna odpowiedź,
przekierowanie, timeout, odpowiedź ponad limit), sekcje README o uruchomieniu lokalnym i z `mcp-platform-dev`,
`prometheus` niewystawiony, sandbox systemd, `install.sh`, `TRIAL_SYSTEM`/`contractSystemIsKnown`.


## Migracja na starter 3.0.0-alpha-10 (2026-10-28)

BOM alpha-6 -> alpha-10 (kontrakty 2.1.1). Źródło: `MIGRATION-3.0.md`, sekcje alpha-7 do alpha-10. M2 nie jest
zaczęty.

| Punkt migracji | Status |
| --- | --- |
| Limity na każdym poziomie schematu (S-M1) | bez zmian w kodzie: wszystkie argumenty już miały `@ArgumentLimits` (liczby z `minimum` i `maximum`, teksty z `maxLength`, wzorce `contractPattern` z kotwicami); brak list, map i `Object` |
| Wynik jako rekordy z `UntrustedText` startera | zrobione: własny rekord `UntrustedText` (z `origin`) usunięty, `mcp-core` zależy od `mcp-platform-starter` tylko dla tego typu; `origin` znika z wyniku (typ ma cztery pola) |
| `@Nullable` na polach wyniku, które system może zostawić puste | zrobione: `ProjectSummary` (nazwa, opis, gałąź, aktywność, adres), `MergeRequest` (wszystko poza `project`, `iid`, flagami i listami), `MergeRequestNote` (autor, czas). Test `aMergeRequestWithEmptyOptionalFieldsIsReturnedWithNulls` |
| Bez `@JsonProperty`/`@JsonIgnore` na komponentach wyniku | było czyste; adnotacje zostają tylko w prywatnych odpowiedziach GitLaba w adapterze |
| Kolejność: konwersja, `normalize`, `cut`, filtr, `plainCut`/`markdownCut` | zrobione w `SystemText` (jedno miejsce dla tekstu z GitLaba); GitLab nie ma filtra ani konwersji, więc tekst wychodzi bez zmiany treści. Test: `textIsNormalizedBeforeItLeavesTheServer`, w logach testów brak `untrusted_text_not_normalized` |
| Spójna obsługa treści w trzech serwerach (wspólne 1 z rundy 2) | gitlab: surowy tekst bez escapowania i bez usuwania znaczników, klient nie renderuje go jako HTML; opisane w README i `docs/api/tools-catalog.md` |
| Blokada `@all` i quick actions po normalizacji | `commentBody` normalizuje treść (`TextHygiene.normalize`), sprawdza znormalizowaną i tę wysyła; znaki, które starter odrzuca w tekście, odrzuca także tu (`firstProblem`). Warianty z `GitLabArgumentsTest` dalej łapane; nowe: `\u200D/merge`, `@all\u200D`, `cc\u200C@all`, `x\r\n/close`; `@a\u200Cll` przechodzi jako `@a ll` |
| `tool_failed` domenowe na INFO | zrobione: nie było testów oczekujących ERROR; dodany `ToolFailureLogLevelTest` (not_found_or_denied i invalid_argument -> INFO) |
| `dev` poza `.env`, compose, systemd i obrazem; obejścia w `application-dev.yml` | w repo nie było `SPRING_PROFILES_ACTIVE=dev` poza poleceniem uruchomienia w README (zostaje); z `application-dev.yml` usunięte `replay-store: memory` i `mtls.required: false` (ssl i jwks-ssl-bundle nie były ustawione); `dev_profile_overrides` w logu ma `overridden=[]` |
| Własne beany polityki i audytu | brak (sprawdzone grepem po `ToolInputPolicy`, `ToolCallbackAuditPostProcessor`, `RegisteredToolsCheck`, `ToolHintsPostProcessor`, `PlatformConfigurationGuard`, `InternalJwt*`) |
| Claimy `vs` i `vsc:{}` z gateway'a M2 | zaakceptowane: `GatewayLinkClaimsTest` (wywołanie z `virtualServer("team-a-review", Map.of())` przechodzi); zawężenia linku nie są egzekwowane (M2) |
| Zgodność z mockiem GitLaba (ws-01) | mocka GitLaba w `mcp-platform-dev` nie sprawdzano (STATUS ws-01 go nie obiecuje); `StubBackend` bez zmian w kształtach odpowiedzi |

Weryfikacja: `./mvnw -B clean verify` (JDK 25) -> 78 testów, 0 błędów (mcp-app 59, mcp-tools 10, mcp-application 9).
Uruchomienie wg README: profil `dev` na 18520, health 200, `POST /mcp` bez JWT 401; proces zatrzymany.

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
- Treść z GitLaba (plik, opisy) w wyniku jako `UntrustedText` startera `{kind, format, text, truncated}`.
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

## M3: zasoby, polityki, manifest

Kamienie: (1) przejście na beta-2 (alpha-12..14 i beta-1 w jednym kroku: wersja BOM, tytuły, `policy.*` i `manifest.*`,
higiena tekstu beta-1), (2) resolvery i `@ResourceRef`, (3) testy polityki i manifestu, (4) dokumentacja.

| Punkt | Stan |
| --- | --- |
| Konfiguracja | `policy.main.*`, `policy.state-dir`, `manifest.*` w `application.yml` (zmienne `MCP_POLICY_*`, `MCP_INTERNAL_URL`); `dev`: paczka z `127.0.0.1:18747` i klucz testowy z `mcp-policies/keys/dev`, `allow-test-keys` tylko w `dev` (`ProductionConfigurationTest`) |
| Higiena tekstu beta-1 | ZWNJ/ZWJ/LRM/RLM i CRLF zostają w treści komentarza; kontrole quick action i `@all` robione na tekście z nimi i bez nich (fail-closed); `body` ma `mentionFiltered = true`. Zmienione testy: `@a<ZWNJ>ll` jest teraz odrzucane, CRLF idzie do GitLaba bez zmian |
| Zasoby | `GitLab::Group` (`list_projects`) i `GitLab::Project` (reszta); merge request nie jest typem zasobu kontraktu, jest adresowany przez projekt i `iid` |
| Atrybuty | `classification` z `topics`; `ancestorClassifications` dokładnie dla grup nad zasobem (projekt: z rodzicem; rodzic projektu: bez siebie; grupa najwyższego poziomu `{}`); `AncestorLookup`, `GitLabGroupResolver`, `GitLabProjectResolver` |
| Cache na wywołanie | `CallCache` w adapterze, klucz: `ResourceContext` wywołania (WeakHashMap), wielkość liter pomijana; obejmuje `findGroup` i stronę projektów grupy |
| Listy | `ProjectSummary.path` jest `@ResourceRef`; `fetchAll` bierze projekty ze strony grupy (1 wywołanie na grupę), awaryjnie `GET /projects/:path` |
| Wyszukiwanie | `list_projects(group)`: zakres to `@ResourceRef` grupy (link zawęża przez politykę), zapytanie buduje serwer (id grupy, stałe parametry); `StructuredQuery`/`QueryScope` nie są użyte, bo GitLab nie ma języka zapytań w tym narzędziu |
| Manifest | `ManifestTest`: reguły bramy, definicje = `tools/list`, `definitionHash`, `resourceRefs`, ryzyko |
| Usunięte | port `ProjectResultFilter` (zastąpiony przez filtr startera) |

Uruchomienie z mcp-platform-dev nie było sprawdzane: mock GitLaba (`mocks/systems/gitlab_rest.py`) nie zwraca
`topics`, więc klasyfikacja w nim jest pusta.

## Odstępstwa od briefu

- `ref` w `get_file` opcjonalny (domyślna gałąź projektu); brief podaje `get_file(project, path, ref)`.
- `list_projects` zwraca pierwszą stronę (100, po ścieżce) z flagą `truncated`, bez stronicowania i bez
  podgrup; parametr strony można dodać w M2 (zmienia `definitionHash`).
- M3: `QueryScope` nie jest używany (patrz tabela M3). `list_projects` bez `search`: model podaje tylko grupę.
- Projekty w wyniku `list_projects` filtruje starter, więc przy `truncated: true` strona może mieć mniej niż 100 elementów.
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
- Zalecenie CC-072 (`outputSchema`) spełnia od alpha-7 sam starter; nic do zrobienia.

## Czego potrzebuje ws-03 (template)

- `docs/nowy-serwer.md` mówi o GitLabie tylko `PRIVATE-TOKEN` przez `credential.value()`; dla `user-oauth`
  GitLab chce `Authorization: Bearer` (`credential::applyTo`).
- Logi aplikacji i audyt w jednym stdout bez escapowania (wspólne 3 z recenzji).

## Czego potrzebuje ws-05 (gateway)

- Dla `gitlab` gateway musi przekazywać `X-MCP-Credential-Type` zgodny z połączeniem (`user-secret` dla PAT,
  `user-oauth` dla OAuth) i schemat `Bearer` (albo `Token` dla PAT); inne kombinacje serwer odrzuca.

## Propozycje CC

1. Źródło klasyfikacji grupy GitLaba (C7, CC-129). API v4 GitLaba nie ma `topics` dla grup (są tylko dla projektów),
   a kontrakt zakłada "topics grupy". Serwer czyta pole `topics` z odpowiedzi `GET /groups/:id`, jeśli jest, a brak
   traktuje jako brak klasyfikacji. Na prawdziwym GitLabie `restricted` na grupie nie zadziała, dopóki kontrakt nie
   wskaże źródła: np. etykieta w opisie grupy, atrybut własny grupy (wymaga admina), albo konwencja nazwy. Do decyzji
   człowieka. Dotyczy też mocka ws-01: potrzebuje `topics` na grupach, żeby demo M3 pokazało zamknięcie poddrzewa.
2. Niewidoczny przodek (C7). Użytkownik będący tylko członkiem podgrupy może nie widzieć grupy nadrzędnej (404/403).
   Serwer traktuje wtedy zasób jak nieistniejący, bo nie zna topics ukrytej grupy. Alternatywa wymaga konta serwisowego
   do odczytu topics przodków (poza modelem poświadczeń użytkownika); do rozstrzygnięcia.
3. Brak innych zmian kontraktu. Kształt `UntrustedText` (CC-072) to od alpha-7 typ startera; pole `origin` zniknęło z wyniku.

## Runda 4: poprawki przeglądu alpha-10 (2026-10-28)

| Znalezisko | Status | Dowód |
| --- | --- | --- |
| G3-M1 `@all` w emfazie (`_@all_`, `__@all__`, `_&#64;all_`) | poprawione: lookbehind `(?<![A-Za-z0-9])` zamiast `\w`, ta sama zmiana we wzorcu za `@all` (`(?![A-Za-z0-9-])`), bo zamykający `_` też jest `\w`; odrzucane jest też `_@all` i `@all_x` (fałszywy alarm bez znaczenia w recenzji) | `GitLabArgumentsTest.commentBodyCannotMentionEveryone` |
| G3-L1 brak testu, że do GitLaba idzie treść znormalizowana | poprawione | `CommentMergeRequestToolTest.lineBreaksAreSentAsTheyCame` (dawniej `gitLabReceivesTheNormalizedBodyNotTheOneTheModelSent`) (CRLF -> LF w ciele POST) |
| G3-L2 `get_file` gubi znaki bidi bez śladu | poprawione wariantem pola systemu: `hiddenCharactersRemoved` (liczba usuniętych znaków sterujących, niewidocznych i zmieniających kierunek, liczona na całym pliku przed cięciem); treść nadal oczyszczona, `truncated` bez zmian; opis w README i `tools-catalog.md`. Odrzucone: zamiana na widoczny znacznik (zmieniałaby kod, który model cytuje) | `GetFileToolTest.invisibleCharactersAreCountedBecauseTheyDisappearFromTheContent` |
| A10-M1 `DevProfileLoopbackTest` | bez zmian, rozwiąże starter alpha-11 | |

## Runda 5: starter alpha-11 (2026-10-28)

BOM alpha-10 -> alpha-11 (MIGRATION-3.0.md, sekcja "Zmiany w 3.0.0-alpha-11"). `DevProfileLoopbackTest` sprawdza regułę guarda przez `DevProfileGuardAssertions.assertDevProfileLoopbackRule()` (A10-M1), więc przechodzi także w kontenerze CI. `./mvnw clean verify`: 78 testów, 0 błędów; `INVOCATION_ID=ci ./mvnw -B verify`: bez różnic. Start lokalny w profilu `dev`: health 200, POST /mcp bez JWT 401.
