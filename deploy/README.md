# deploy/

Uruchomienie serwera na VM przez systemd. Opis krok po kroku: `docs/operator/install-vm.md` in `mcp-docs`.
Obraz kontenera: `Dockerfile` w katalogu głównym.

```
deploy/
├── systemd/
│   ├── mcp-gitlab.service         unit (konto mcp, sandbox systemd, logi do journald)
│   └── mcp-gitlab.env.template    zmienne środowiska -> /etc/mcp-gitlab/mcp-gitlab.env
└── scripts/
    ├── install.sh                  pierwsza instalacja (konto, katalogi, unit)
    ├── update.sh                   podmiana jara z kopią do wycofania
    └── healthcheck.sh              /actuator/health po mTLS
```
