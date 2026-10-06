# Systivex Development Guide

Scope: **control plane (Phases 0–1) + target services (Phases 2A–2B) +
telemetry pipeline (Phase 3A)**
(verified 2026-09-30). §§1–7 cover the control plane in `backend/`;
§8 covers the target services in `backend/target-services/`; §9 covers the
local telemetry infrastructure in `observability/`.
No cache, AI runtime, or frontend — none are required yet.

## 1. Prerequisites

| Requirement | Version / note | Status |
|---|---|---|
| JDK | **21** (e.g. `21.0.10 LTS`) | Required. Project compiles with `release 21`. |
| Maven | Wrapper-provided (3.9.16 via `./mvnw`) | No separate install needed |
| OS / shell | Windows 10/11 + PowerShell 5.1 (verified); any OS with JDK 21 works | — |
| PostgreSQL | 18.x locally (`systivex` + 3 target dbs, see §8a) | Required to run; tests use Testcontainers instead |
| Docker | Docker Desktop (daemon running) | Required for `./mvnw test` (Testcontainers spins up PG 18) and for the Phase 3A telemetry collector (`observability/`) |
| Redis | **Not required** in this phase | Deferred, only when justified |
| Ollama / Spring AI | **Not required** in this phase | Deferred |
| Node / React | **Not required** (no frontend yet) | Deferred |
| K8s / brokers | **Not required** (services run locally, not containerized) | Deferred |

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

Expected (verified 2026-09-30): `Tests run: 31, Failures: 0, Errors: 0` —
service unit tests plus Testcontainers-backed migration, repository, and API
integration tests plus 2 observability tests (tracing active, health UP).
Docker Desktop must be
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

Only `health` and `prometheus` actuator endpoints are exposed under
`/actuator` (Phase 3A; previously health only).
The twin API lives under `/api/v1/twin/entities` and
`/api/v1/twin/relationships` (create/get/list, JSON, 400/404/409 on misuse).

## 6. What is NOT needed yet

- No service containers or orchestration; the only Compose file is
  `observability/docker-compose.yml` (telemetry collector, §9).
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
7. Keep target services free of control-plane code (and vice versa) — the only
   link between them today is documentation.

## 8. Target services (Phase 2B)

Four standalone services under `backend/target-services/`. Each has its own
`pom.xml`, Maven wrapper, and application class — build and run each from its
own directory with the same JDK 21 setup as §1. Order, payment, and inventory
need their own PostgreSQL database (gateway stays stateless); tests use
Testcontainers, so Docker must be running for `./mvnw.cmd test`.

| Service | Directory | Port | Database / user |
|---|---|---|---|
| gateway-service | `backend/target-services/gateway-service/` | 8081 | — (none) |
| order-service | `backend/target-services/order-service/` | 8082 | `order_db` / `order_app` |
| payment-service | `backend/target-services/payment-service/` | 8083 | `payment_db` / `payment_app` |
| inventory-service | `backend/target-services/inventory-service/` | 8084 | `inventory_db` / `inventory_app` |

### 8a. Local target-database setup (once per machine, never committed)

The bootstrap script `backend/target-services/create-target-databases.sql`
creates the three databases and application users (each database owned by its
user). It contains **no passwords** — supply one per service on the command
line — never touches the control-plane `systivex` database, and issues no
destructive commands. Run it as a PostgreSQL superuser (e.g. `postgres`):

```powershell
$env:PGPASSWORD = "<your local postgres superuser password, never committed>"
& psql -h localhost -U postgres `
  -v order_pw="<choose a local order_app password>" `
  -v payment_pw="<choose a local payment_app password>" `
  -v inventory_pw="<choose a local inventory_app password>" `
  -f backend/target-services/create-target-databases.sql
Remove-Item Env:\PGPASSWORD
```

Then export the three service passwords for every shell that runs a service
(the live `application.properties` files are git-ignored and read them only
from the environment — same convention as the control plane):

```powershell
$env:ORDER_DB_PASSWORD="<your local order_app password>"
$env:PAYMENT_DB_PASSWORD="<your local payment_app password>"
$env:INVENTORY_DB_PASSWORD="<your local inventory_app password>"
```

The tracked `application.example.properties` in each service shows the same
shape with no secrets. Flyway migrates automatically at startup (inventory
also seeds `SKU-1001 × 10` units of clearly-marked dev stock via `V2`);
Hibernate only validates (`ddl-auto=validate`). Never set
`update`/`create`/`create-drop` here.

### 8b. Build, test, run (per service)

First run per service (same pattern for order/payment/inventory — example
shown once):

```powershell
cd backend/target-services/order-service
Copy-Item src/main/resources/application.example.properties src/main/resources/application.properties
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
$env:Path="C:\Program Files\Java\jdk-21.0.10\bin;" + $env:Path
./mvnw.cmd clean test
./mvnw.cmd -q package -DskipTests
```

The live `application.properties` is git-ignored (same convention as the
control plane); the tracked `.example` file is the template. No secrets exist
in tracked files — ports, downstream URLs, and datasource URLs/usernames are
safe to commit; passwords arrive only via `ORDER_DB_PASSWORD`,
`PAYMENT_DB_PASSWORD`, `INVENTORY_DB_PASSWORD`.

Run all four (one shell each, inventory/payment first, then order, then
gateway — or any order; each waits on its own port):

```powershell
./mvnw.cmd spring-boot:run   # from each service directory
```

Health: `GET http://localhost:808{1..4}/actuator/health` → `{"status":"UP"}`.

Checkout (verified end to end over real HTTP against real databases):

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/checkout" -Method Post `
  -Body (@{productId="SKU-1001"; quantity=1; customerId="CUST-1001"; amount=1499.00} | ConvertTo-Json) `
  -ContentType "application/json" | ConvertTo-Json
# -> {"orderId":"...","status":"CONFIRMED",...} with reservationId + authorizationId
```

Behavior notes (persistent, Phase 2B): inventory rejects quantities above
stored stock (422, seeded `SKU-1001` holds 10 — so quantity 99 fails while 1
succeeds), payment declines amounts above 5000.00 (422), dead downstream
surfaces as 503 at order level and 502 at gateway level. Both rejections are
recorded in their service's database. Checkout is not atomic across the three
databases (see `ARCHITECTURE.md` §4): a FAILED order can coexist with a
standing reservation.

Tests (57 total across the four services, Testcontainers PostgreSQL, no local databases touched): each
service has real-HTTP boundary tests — inventory/payment/order boot against
throwaway containers (Flyway-migrated, so migration startup is covered),
order/gateway tests stub only the downstream side with loopback stub servers.
Inventory additionally covers stock-decrease persistence, rejection paths, a
concurrent-reserve locking test, and repository constraint behavior. Each
service also has an observability test: Prometheus exposition plus business
counters, and (gateway/order) W3C traceparent propagation to stubbed
downstream hops — no collector needed for tests. Order-service additionally
has two telemetry privacy tests proving downstream 4xx bodies never reach
recorded span data (positive span-event assertions plus a negative-direction
test that fails with the sanitizer unregistered).

## 9. Telemetry pipeline (Phase 3A)

The Java services run locally (see §5/§8b); only the collection
infrastructure runs in Docker. From `observability/`:

```powershell
docker compose up -d          # start the OTel Collector (contrib, pinned image)
docker compose logs -f otel-collector
docker compose down           # stop it
```

What it does: receives OTLP traces on :4317/:4318, prints spans to its own
log (debug exporter — the trace verification path), scrapes each service's
`/actuator/prometheus` (ports 8080–8084 via `host.docker.internal`), and
re-exposes the collected metrics Prometheus-compatible on :8889. Collector
health: `GET http://localhost:13133/`.

Per-service checks (services running):

```powershell
Invoke-RestMethod -Uri "http://localhost:8082/actuator/prometheus"  # raw exposition
```

Distributed-trace check: `POST /api/v1/checkout` (§8b), then find the single
trace ID spanning `gateway-service` / `order-service` /
`inventory-service` / `payment-service` in the collector log
(`http post /api/v1/checkout` server span plus `checkout`,
`inventory.reservation`, `payment.authorization` business spans share it).

Configuration: `management.opentelemetry.tracing.export.otlp.endpoint`
defaults to `http://localhost:4318/v1/traces` and is overridable via
`OTEL_TRACING_ENDPOINT` — never a secret. Sampling is 1.0, correct only for
local development volume. Telemetry endpoints bind locally with no auth;
do not expose them beyond the machine (see `THREAT_MODEL.md` §3a).
