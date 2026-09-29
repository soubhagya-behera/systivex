# Systivex Development Guide

Scope: **control-plane persistence foundation** (verified 2026-09-29).
Covers `backend/` with PostgreSQL + Flyway + JPA. No cache, AI runtime,
or frontend — none are required yet.

## 1. Prerequisites

| Requirement | Version / note | Status |
|---|---|---|
| JDK | **21** (e.g. `21.0.10 LTS`) | Required. Project compiles with `release 21`. |
| Maven | Wrapper-provided (3.9.16 via `./mvnw`) | No separate install needed |
| OS / shell | Windows 10/11 + PowerShell 5.1 (verified); any OS with JDK 21 works | — |
| PostgreSQL | 18.x locally (`systivex` db, `systivex_app` user) | Required to run; tests use Testcontainers instead |
| Docker | Docker Desktop (daemon running) | Required for `./mvnw test` (Testcontainers spins up PG 18) |
| Redis | **Not required** in this phase | Deferred, only when justified |
| Ollama / Spring AI | **Not required** in this phase | Deferred |
| Node / React | **Not required** (no frontend yet) | Deferred |
| Docker | **Not required** (no Compose yet) | Deferred |

> Environment setup (2026-09-28, updated): the machine default `JAVA_HOME`/PATH
> points at JDK 17, which **cannot** build this project. This is solved
> workspace-locally — no global change, Java 17 projects unaffected:
>
> - **Inside VS Code:** `.vscode/settings.json` (local-only, git-ignored; see the
>   tracked template `.vscode/settings.json.example`) pins the Java extension to
>   `C:\Program Files\Java\jdk-21.0.10` and injects `JAVA_HOME` + a prepended
>   JDK 21 `bin` entry into every **new** integrated terminal. Just open the
>   `Systivex` folder, kill any old terminal (trash-can icon), and open a new
>   one (`Ctrl+Shift+\``) — `java -version` must report 21.x.
> - **Outside VS Code** (standalone PowerShell/cmd): set it manually per session:
>
> ```powershell
> $env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
> $env:Path="C:\Program Files\Java\jdk-21.0.10\bin;" + $env:Path
> ```

Verify:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version   # expect 21.x
./mvnw -version                             # expect Java version: 21.x
```

## 2. Layout

```text
backend/
├── pom.xml            # spring-boot-starter-parent 4.1.1, java.version 21
├── mvnw / mvnw.cmd / .mvn/wrapper/
└── src/
    ├── main/java/com/soubhagya/systivex/
    │   ├── SystivexApplication.java
    │   └── twin/{model,repository,service,api}/
    ├── main/resources/
    │   ├── application.properties                 # LOCAL ONLY, git-ignored
    │   ├── application.example.properties         # tracked safe template
    │   └── db/migration/V1__create_system_twin.sql
    └── test/java/com/soubhagya/systivex/  # unit + Testcontainers integration tests
```

Backend coordinates: `com.soubhagya:systivex:0.0.1-SNAPSHOT`.
Active dependencies: `webmvc`, `validation`, `actuator`, `data-jpa`, `flyway`
(+ `flyway-database-postgresql`, `postgresql` driver;
test: `webmvc-test`, `restclient-test`, `testcontainers-postgresql`).
Do not add more without a recorded decision.

## 2a. Local database setup (once per machine, never committed)

The app expects PostgreSQL 18 on `localhost:5432` with database `systivex`
and user `systivex_app` (owner of the database so Flyway can migrate).
Create them with your admin credentials; the app password itself lives only
in your shell session, never in a file:

```powershell
$env:SYSTIVEX_DB_PASSWORD="<your local systivex_app password>"
```

`backend/src/main/resources/application.properties` reads
`spring.datasource.password=${SYSTIVEX_DB_PASSWORD:}` and is git-ignored —
verify with `git check-ignore` if unsure. The tracked
`application.example.properties` shows the same shape with no secrets.
Flyway migrates automatically at startup; Hibernate only validates
(`ddl-auto=validate`). Never set `update`/`create`/`create-drop` here.

## 3. Maven Wrapper commands

All commands run from `backend/`. In a **new** VS Code integrated terminal the
JDK 21 environment is automatic (workspace settings); in an external shell,
export `JAVA_HOME`/PATH first (see §1).

```powershell
cd backend

# run the test suite
./mvnw test

# full build (runs tests, produces executable jar)
./mvnw package

# build without tests (not a substitute for ./mvnw test in verification)
./mvnw -q package -DskipTests

# start the backend (foreground, Tomcat on :8080)
./mvnw spring-boot:run
```

## 4. How to run tests

```powershell
cd backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
$env:Path="C:\Program Files\Java\jdk-21.0.10\bin;" + $env:Path
./mvnw test
```

Expected (verified 2026-09-29): `Tests run: 29, Failures: 0, Errors: 0` —
service unit tests plus Testcontainers-backed migration, repository, and API
integration tests (one shared PG 18 container per run). Docker Desktop must be
running; the developer's local database is never touched by tests.

## 5. How to start the backend

```powershell
cd backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
$env:Path="C:\Program Files\Java\jdk-21.0.10\bin;" + $env:Path
$env:SYSTIVEX_DB_PASSWORD="<your local systivex_app password>"
./mvnw spring-boot:run
```

Expected startup (verified): Spring Boot `v4.1.1`, Tomcat `11.0.24` on port `8080`,
Flyway migrates `V1` on first boot, Hibernate validates, then
`Started SystivexApplication`.

Health check:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" | ConvertTo-Json
# {"status":"UP","groups":["liveness","readiness"]} (shape may vary; "status":"UP" is the assertion)
```

Only the `health` actuator endpoint is exposed under `/actuator` by default.
The twin API lives under `/api/v1/twin/entities` and
`/api/v1/twin/relationships` (create/get/list, JSON, 400/404/409 on misuse).

## 6. What is NOT needed yet

- No `docker-compose.yml`; local PostgreSQL is installed directly.
- No Redis connection, no vector database, no broker.
- No `OLLAMA_*` configuration, no model pull.
- No `frontend/` install or build.

If a future milestone introduces any of these, this guide must be updated
at the same time as the code — docs must reflect reality.

## 7. Contribution rules for this phase

1. Do not add dependencies unless required for the current phase (recorded in `DECISIONS.md`).
2. Do not create placeholder packages or speculative modules.
3. Do not add Docker Compose, Redis/AI configuration, or frontend scaffolding.
4. Do not weaken tests to make them pass.
5. Do not claim features work unless verified by `./mvnw test` / startup / endpoint check.
6. Never commit `application.properties`, credentials, or machine-local settings.
