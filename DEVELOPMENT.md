# Systivex Development Guide

Scope: **initial repository foundation only** (verified 2026-09-28).
Covers the existing `backend/` skeleton. Nothing here assumes a database,
cache, AI runtime, or frontend — none are required yet.

## 1. Prerequisites

| Requirement | Version / note | Status |
|---|---|---|
| JDK | **21** (e.g. `21.0.10 LTS`) | Required. Project compiles with `release 21`. |
| Maven | Wrapper-provided (3.9.16 via `./mvnw`) | No separate install needed |
| OS / shell | Windows 10/11 + PowerShell 5.1 (verified); any OS with JDK 21 works | — |
| PostgreSQL | **Not required** in this phase | Deferred |
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
    ├── main/java/com/soubhagya/systivex/SystivexApplication.java
    ├── main/resources/application.properties   # only spring.application.name=systivex
    └── test/java/com/soubhagya/systivex/SystivexApplicationTests.java  # contextLoads
```

Backend coordinates: `com.soubhagya:systivex:0.0.1-SNAPSHOT`.
Active dependencies: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`,
`spring-boot-starter-actuator` (+ `-test` starters). Do not add more without a recorded decision.

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

Expected (verified 2026-09-28): `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
— `SystivexApplicationTests.contextLoads`. Build time ~7 s on the reference machine.

## 5. How to start the backend

```powershell
cd backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
$env:Path="C:\Program Files\Java\jdk-21.0.10\bin;" + $env:Path
./mvnw spring-boot:run
```

Expected startup (verified): Spring Boot `v4.1.1`, Tomcat `11.0.24` on port `8080`,
`Started SystivexApplication in ~1.4 s`.

Health check:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" | ConvertTo-Json
# {"status":"UP","groups":["liveness","readiness"]} (shape may vary; "status":"UP" is the assertion)
```

Only the `health` actuator endpoint is exposed under `/actuator` by default —
this is expected at this stage. No application REST endpoints exist yet.

## 6. What is NOT needed yet

- No `DATABASE_URL`, no schema migration, no `docker-compose.yml`.
- No Redis connection, no vector database, no broker.
- No `OLLAMA_*` configuration, no model pull.
- No `frontend/` install or build.

If a future milestone introduces any of these, this guide must be updated
at the same time as the code — docs must reflect reality.

## 7. Contribution rules for this phase

1. Do not add dependencies unless required for the already-generated backend.
2. Do not create placeholder packages or speculative modules.
3. Do not add Docker Compose, DB/Redis/AI configuration, or frontend scaffolding.
4. Do not weaken tests to make them pass.
5. Do not claim features work unless verified by `./mvnw test` / startup / endpoint check.
