# Bezpieczeństwo serwera

Niezmienniki platformy (`mcp-platform-plan/00-overview.md`) i to, gdzie serwer je spełnia.

| Niezmiennik | W serwerze |
| --- | --- |
| Ruch tylko od gateway'a: mTLS + wewnętrzny JWT, inaczej 401 | `server.ssl.client-auth: need` z CA wewnętrznym; `InternalJwtFilter` startera (CN `mcp-gateway`, ES256 z JWKS gateway'a, `iss`, `aud`, czas życia <= 60 s, `jti` jednorazowy w Redisie, `tool` == `params.name`, `rid` == `X-Request-Id`). Bez JWT tylko `/actuator/health*`. Test: `GatewayOnlyAccessTest` |
| Token IdP nigdy do systemu | serwer go nie dostaje; do systemu idzie wyłącznie poświadczenie z C3 (`CredentialContext.require`) |
| Fail-closed | brak JWKS, Redisa, konfiguracji albo poświadczenia -> 401/503/`credential_required`/brak startu, nigdy przepuszczenie; starter odrzuca relaksacje `dev` w innych profilach |
| Sekrety nigdy w logach, audycie, wynikach i błędach | nagłówki poświadczeń ukryte po odczycie, `BackendCredential.toString()` bez wartości, redakcja komunikatów w starterze; adapter zamienia błędy i nieczytelne odpowiedzi systemu na wyjątki bez treści; GitLab tylko po `https`. Testy: `theCredentialAndTheBackendErrorBodyNeverAppearInTheResultOrTheLog`, `anUnreadableAnswerIsAnInternalErrorWithoutItsContent`, `outsideDevTheBackendMustBeHttps` |
| "Nie istnieje" i "brak dostępu" -> `not_found_or_denied` | 404 i 403 systemu dają ten sam błąd i tekst. Testy: `missingAndForbidden*` w każdej klasie narzędzia |
| Treść z systemu to dane | każdy tekst pisany przez użytkowników GitLaba (treść pliku, opisy, tytuł, nazwy, etykiety, gałęzie, nazwy użytkowników) w wyniku jako `UntrustedText` (`{kind: "untrusted-data", format, text, truncated}`, CC-072; normalizacja, potem cięcie, treść bez zmian); komentarz nie może zawierać quick actions (linia od `/`), które GitLab wykonałby jako użytkownik (merge, approve, zmiana przypisań), ani `@all` (powiadomienie całego projektu) |
| Ścieżki rozwiązywane przez system | grupa i projekt -> id przez `GET /groups/:path`, `GET /projects/:path`; dalej tylko id, w wyniku ścieżka kanoniczna; lista projektów z `with_shared=false` i odrzuceniem projektów innej przestrzeni nazw |
| Audyt z `requestId` | jedno zdarzenie C8 `tool_call` na wywołanie (logger `pl.aiwcorpo.mcp.audit`, stdout, czysta linia JSON), `requestId` z JWT |

## Zasady dla kodu

- Każdy argument narzędzia to dane od modelu: wzorzec, długość, zamknięte listy, przed wywołaniem systemu.
  Szczegóły: `docs/jak-napisac-narzedzie.md`.
- Adresy: tylko `MCP_GITLAB_URL` z konfiguracji (poza `dev` wyłącznie `https`; `http` tylko jawnym
  `MCP_GITLAB_ALLOW_PLAIN_HTTP` dla mocków środowiska dev), ścieżki API z szablonu URI; każda zmienna kodowana
  w całości (`EncodingMode.VALUES_ONLY`, `/` -> `%2F`), więc argument nie doda segmentu ani parametru zapytania;
  bez przekierowań (3xx to błąd, nie "brak zasobu"); żadnych adresów z argumentów (SSRF).
- Odpowiedź GitLaba: najwyżej `max-response-size` (1 MB), tylko pola domeny, nigdy w komunikacie ani logu (także
  odpowiedź 2xx, której nie da się sparsować).
- Profil `dev` (klucze testowe, bez mTLS) jest w jarze, ale wstaje tylko na adresie pętli zwrotnej (starter,
  test `DevProfileLoopbackTest`). Serwer, do którego łączy się gateway, działa wyłącznie w konfiguracji bazowej.
- Serwer nie ma konta w systemie. Typ `service-account` (np. service desk, D10) przychodzi też przez C3, a
  wtedy serwer jest główną granicą autoryzacji i musi sprawdzać zakres zasobu sam (starter beta).
- Hinty MCP (`readOnlyHint`, `destructiveHint`) są informacją dla klienta, nie zabezpieczeniem.

## Wdrożenie

Konto bez uprawnień, sandbox systemd (`deploy/systemd/mcp-gitlab.service`), sekrety tylko w pliku
środowiska (0640, root:mcp) albo z magazynu sekretów, certyfikat serwera z SAN `mcp-gitlab.internal` z CA
wewnętrznego. Redis: użytkownik ACL `mcp-gitlab` z kluczami `jti:mcp-gitlab:*` i komendą `SET`.
