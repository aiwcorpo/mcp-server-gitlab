# Architektura

## Miejsce w platformie

```
klient AI --OIDC--> MCP gateway --mTLS + JWT C2 + poświadczenie C3--> mcp-gitlab --API v4--> GitLab
                       |  ^
                       |  +-- credential broker (poświadczenie użytkownika)
                       +-- polityki (Cedar), potwierdzenia, audyt
```

Gateway uwierzytelnia użytkownika, decyduje o dostępie do narzędzia, pobiera z brokera poświadczenie
użytkownika do systemu i wysyła do serwera nowy wewnętrzny JWT na każde żądanie HTTP. Serwer sprawdza, że
żądanie przyszło od gateway'a (certyfikat klienta CN `mcp-gateway`, JWT z `aud` = `mcp-gitlab`, `tool` ==
nazwa wywoływanego narzędzia, jednorazowy `jti`), i woła system jako ten użytkownik. Serwer nie zna tokenu
IdP użytkownika i nie przechowuje żadnego sekretu.

## Heksagon w modułach

```
            mcp-tools (@Tool)                      mcp-integration (REST do systemu)
                  |                                         |
                  v                                         v implementuje
           mcp-application (przypadki użycia) ---> mcp-core (domena, porty in/out)
                              mcp-app: bootstrap, wiring, jar, testy end-to-end
             wspólne: mcp-platform-starter (JWT C2, C3, audyt C8, błędy C10, transport MCP)
```

`mcp-core` nie zależy od Springa ani MCP, więc reguły domeny testuje się bez transportu. Kod przekrojowy jest
w starterze; serwer go nie powiela.

## Transport

Streamable HTTP (Spring AI 2.0, MCP SDK 2.0, tryb SYNC), endpoint `/mcp`, wersje protokołu 2025-03-26 ..
2025-11-25, sesje `Mcp-Session-Id`. Gateway trzyma osobną sesję na użytkownika (`sub`), więc serwer nie wiąże
sesji z użytkownikiem sam. `CallerContext` i `CredentialContext` są per wątek żądania, dlatego starter odrzuca
tryb ASYNC.

## Decyzje

- mTLS kończy sam serwer (`server.ssl.client-auth: need`), bez nginx przed nim: certyfikat klienta musi dotrzeć
  do aplikacji, a gateway sprawdza SAN `mcp-gitlab.internal` serwera.
- Konfiguracja bazowa jest produkcyjna; profil `dev` istnieje dla testów i wyłącza tylko TLS.
- Jeden system na serwer: `server-id` = `mcp-<system>`, `CredentialContext.require` innego systemu to błąd
  programisty.
- Adapter nie podąża za przekierowaniami i nie czyta treści błędów systemu.
- Ponawianie i circuit breaker w adapterze: M2 template'u (teraz tylko timeouty).
- Ścieżki GitLaba rozwiązuje GitLab (`GET /groups/:path`, `GET /projects/:path`), dalej tylko id; wynik
  pokazuje ścieżkę kanoniczną. Porównanie napisów myliłoby wielkość liter i przeniesione projekty.
- `get_file`: HEAD przed pobraniem (rozmiar), treść przy commicie z HEAD; plik binarny (NUL albo niepoprawny
  UTF-8) i za duży nie są zwracane.
- Argumenty sprawdza starter wobec `inputSchema` (od 3.0.0-alpha-6, bez zmian do alpha-10; walidacja MCP SDK jest wyłączona, serwer nie
  ma własnego customizera): błędy są w C10 i audytowane. Schemat buduje `SchemaLimitedToolCallbacks`
  z `@ArgumentLimits`; wzorce ścieżek to `contractPattern`, nie kopie.
- Adapter: `https` poza `dev`, limit rozmiaru odpowiedzi, 3xx jako błąd, `BackendResponseException` bez treści.
- Listę filtruje starter: `ProjectSummary.path` jest `@ResourceRef`, a `GitLabProjectResolver.fetchAll` rozwiązuje wszystkie wyniki jednym przejściem (strona projektów grupy z pamięci wywołania, przodkowie ze ścieżki w `AncestorLookup`, bez wywołań).
