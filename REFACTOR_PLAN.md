# Refactor Plan — Enterprise Network SIEM Console

## Purpose

Take this project from a hobby-scale demo (device ping monitor + audit log) to a
production-grade SIEM console: real correlation rules, an incident lifecycle,
event ingestion, authentication, and a SOC-style dashboard. This document tracks
phase status, what has actually been measured, and decisions still open.

## Current state (measured 2026-09-14)

- **Backend**: Spring Boot 4.1.0, Java 26. Package layout is `device/`,
  `common/`, `incident/`, `correlation/` (+ `correlation/threatintel/`),
  `ingestion/`, `anomaly/`, `event/`, `realtime/`, `security/`, `metrics/`,
  `config/`. Flyway is wired with eight migrations (`V1` baseline through `V8`
  latency samples). Two profiles: `postgres` (real database,
  `ddl-auto=validate`, used by docker-compose) and `h2` (isolated in-memory,
  `ddl-auto=create-drop`, used by the test suite — no external service needed
  to run tests). `./mvnw -q verify` passes: **117 tests, 0 failures, 0
  errors, 0 skipped** (JDK 26 required — scope `JAVA_HOME` to that install for
  the build command, the system default may still be 21). Testcontainers-backed
  Postgres/Flyway integration tests are part of that count and run for real
  when Docker is available; the module still builds (tests skip, not fail) if
  it is not. A live smoke test against a real Postgres instance confirmed the
  Flyway-managed schema matches the entities exactly (`ddl-auto=validate` did
  not reject anything). Code formatting is enforced with Spotless
  (`palantir-java-format`); `./mvnw spotless:check` / `spotless:apply` are
  separate goals, deliberately **not** bound to the `verify` phase (a plain
  `mvn verify` will not fail on formatting alone — see "Open decisions").
  springdoc-openapi is wired (`/v3/api-docs`, `/swagger-ui.html`, both public —
  read-only schema of an already-public API shape).
- **Frontend**: Angular 21.2, TypeScript 5.9, Vitest 4 (run through `ng test`,
  never `npx vitest run` directly — that skips the zone.js/TestBed setup and
  fails every spec with "describe is not defined"). `npm test`: **72 tests, 8
  files, all passing**. A Playwright suite (`network-ui/e2e/`) covers a full
  login → add device → acknowledge incident smoke path and theme-persistence
  across a reload, both against the app's own dev servers. `npx ng build`
  succeeds with only two pre-existing,
  unrelated CommonJS warnings (`@stomp/stompjs`, `sockjs-client`, pulled in by
  `realtime.service.ts`). ESLint is wired via `@angular-eslint/schematics`;
  `npm run lint` currently reports **4 known, deliberately unresolved**
  `prefer-inject` violations in `realtime.service.ts` — its constructor
  injection is kept because `realtime.service.spec.ts` constructs the service
  without Angular DI, and Angular's own `inject()` codemod breaks that test;
  fixing either side is an open decision, not a bug. `npm audit` reports **0
  vulnerabilities** (was 1 critical/10 high/11 moderate/2 low; resolved via a
  clean npm 11 dependency resolution, no `--force`, no major version bumps).
  The old `websocket.service.ts` is dead code (`@deprecated`, no importers) —
  left in place, not yet deleted (needs an explicit deletion decision, see
  project rules on destructive changes).
- **Infrastructure**: `docker-compose.yml` provisions Postgres, Prometheus,
  and Grafana alongside the backend and frontend; both app Dockerfiles exist
  (`demo/Dockerfile`, `network-ui/Dockerfile`). Database credentials come from
  `.env` (see `.env.example`); the previous hardcoded credential was removed
  from tracked files (it remains in git history — rotate it for real if it
  was ever used outside local development). `.github/workflows/ci.yml` now
  runs five real jobs: `build-backend` (`./mvnw -q verify`, Testcontainers
  included), `build-frontend` (`npm ci` + `npm test` + `npm run build`),
  `lint` (`spotless:check` + `npm run lint`, the latter `continue-on-error`
  until the `prefer-inject` decision above is made), `docker-build` (builds
  both images + validates `docker-compose.yml`), and `dependency-scan` (OWASP
  Dependency-Check + `npm audit`, both informational/non-blocking for now).
- **Feature set**: device status polling/audit log (original), a real
  incident lifecycle with a state machine, a correlation engine evaluating
  rules stored as data (device flapping, same-subnet outage, latency anomaly,
  event burst) with condition-type validation at write time (an unknown or
  missing `type` is rejected at the API instead of silently never firing), a
  full Rule CRUD API (`/api/v1/rules`, ADMIN-gated writes), an event ingestion
  API, an EWMA-based latency anomaly detector, a threat-intel provider stub
  with a local blocklist implementation, per-device latency history
  (`GET /api/v1/devices/{id}/latency`) with a daily, `ShedLock`-guarded
  retention purge (default 30 days, `siem.latency.retention`), and live push
  over STOMP: `/topic/devices`, `/topic/incidents`, `/topic/metrics` (payload
  contract in `docs/LESSONS.md`). Device scanning is parallelized on virtual
  threads; the scheduled sweep is locked with ShedLock so a second instance
  won't double-scan; `@Async` work (`IncidentRealtimeListener`) now runs on an
  explicitly named, virtual-thread-backed executor bean (`AsyncConfig`) instead
  of an ambiguous implicit one. Micrometer metrics (`siem.scan.duration`,
  `siem.incidents.open`, `siem.events.ingest.rate`) have an importable Grafana
  dashboard at `docs/grafana/siem-dashboard.json`. A one-time, gated
  `DataSeeder` (`siem.seed.enabled`, on by default only in the `h2` profile)
  seeds 9 realistic demo devices so a fresh checkout doesn't start empty.
  Backend requires authentication (JWT access/refresh, roles
  `ADMIN`/`ANALYST`/`VIEWER`) on everything except `/api/v1/auth/**`,
  `/actuator/health`, `/actuator/prometheus` (private network only),
  `/v3/api-docs/**` + `/swagger-ui/**`, and the WebSocket HTTP handshake
  (`/ws-siem/**`, see below); `POST /api/v1/events` is rate-limited (~20
  req/s/IP); every `AuditLog` row records which authenticated user (or
  `"system"`) performed the action. The WebSocket handshake now only accepts
  the same origins configured for REST CORS (`siem.cors.allowed-origins`,
  previously a wildcard), and the STOMP CONNECT frame itself is authenticated
  by `StompAuthChannelInterceptor` using the same bearer-token chain as the
  REST filter — a CONNECT without a valid access token is refused outright, so
  no anonymous session can subscribe to any topic. The frontend has a login
  screen, route guard, token-refresh interceptor, role-based action gating
  matching the backend's `@PreAuthorize` rules, a `RealtimeService`
  (exponential backoff, polling fallback after repeated failed reconnects),
  hand-rolled inline-SVG chart components (`shared/charts/bar-chart`,
  `shared/charts/sparkline-chart` — `ngx-charts`/`ngx-echarts` were tried and
  rejected, both have peer-dependency conflicts with Angular 21.2) driving an
  incident-trend/severity-distribution/device-uptime analytics panel, a
  per-device latency-history drawer, a live threat-level indicator, a
  Rules CRUD screen (ADMIN-gated writes, matching the backend contract), an
  incident comment thread (read open to any role, posting gated to
  `ANALYST`/`ADMIN`, author always the signed-in username), and a Settings
  screen with a four-theme picker (`gunmetal`/`daylight`/`phosphor`/
  `high-contrast`, `[data-theme]` token overrides, persisted to
  `localStorage`, applied pre-paint — see
  `docs/adr/0004-theme-variants-via-token-overrides.md`).

## Known blockers

All the blockers from earlier revisions of this document are resolved,
including the `device.spec.ts`/`Device` naming collision, the red dependency
audit, the unauthenticated STOMP CONNECT frame, the WebSocket wildcard
origin, the missing incident-comments UI, and the missing Playwright e2e
suite. What is currently open:

1. **Bare `java -jar` or a plain `docker run` without an active Spring
   profile will still fail** (Postgres-flavored Flyway SQL gets attempted
   against the H2 dialect, or vice versa) — only `docker compose` (sets
   `SPRING_PROFILES_ACTIVE=postgres`) and the test suite (pinned to `h2`) are
   safe entry points. See "Open decisions".
2. **Settings screen covers appearance only.** The nav now has a Settings
   destination (`ThemeService`, `network-ui/src/app/services/theme.service.ts`)
   with a four-theme picker (gunmetal/daylight/phosphor/high-contrast),
   persisted to `localStorage` and applied pre-paint via an inline script in
   `index.html`. It does not cover anything beyond colour theme; any other
   settings content is a separate, not-yet-scoped decision.
3. **Backend "80%+ coverage on critical business logic" is unmeasured.**
   117 tests pass, but no coverage tool (JaCoCo or similar) has been run
   against that target yet.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 0 | Discovery, hardening the repo before feature work | Done |
| 1 | Backend hardening: profiles, Flyway, DTOs, validation, RFC 7807 errors, filtering | Done |
| 2 | Correlation engine, incident lifecycle, event ingestion, anomaly detection, threat-intel stub | Done |
| 3 | Real-time push (WebSocket/STOMP topics), virtual threads, scheduler locking, metrics | Done |
| 4 | Authentication (JWT/RBAC), rate limiting, CORS hardening, audit trail | Done, including STOMP CONNECT-level auth and origin restriction (previously deferred) |
| 5 | SOC-style frontend: feature routes, design tokens, charts, live threat level | Overview/Devices/Incidents/Rules/Logs/Settings views, charts, threat-level indicator, Rules CRUD, incident comments, and a four-theme picker are all functional and wired to real endpoints |
| 6 | Test coverage, CI/CD, documentation, seed data | CI (5 real jobs), README, ADRs, Dockerfiles, DataSeeder, springdoc, Testcontainers integration tests, and a Playwright e2e smoke suite (login/device/incident + theme persistence) are done; a measured backend coverage number is still open |

## Open decisions

- Whether to add a `spring.profiles.default` fallback so a bare `java -jar`
  fails less confusingly, or leave it as an explicit requirement.
- Whether to bind `spotless:check` into the Maven `verify` phase (stronger
  guarantee, but breaks any in-flight branch that hasn't been reformatted
  yet) or keep it a separate CI-only step (current state).
- `realtime.service.ts`'s 4 `prefer-inject` ESLint violations: rewrite
  `realtime.service.spec.ts` to go through Angular's TestBed/DI so the service
  itself can switch to `inject()`, or accept constructor injection here
  permanently and suppress the rule for this one file.
- Whether to delete the dead `websocket.service.ts` shim now that nothing
  imports it, or keep it a little longer in case an external caller still
  exists.
- Local development database strategy beyond docker-compose (host port 5432
  may already be occupied by another local Postgres instance on some
  machines — see `docs/LESSONS.md`).

## Out of scope (explicitly, per project rules)

Splitting into microservices, adding a message broker (Kafka, RabbitMQ), or any
other infrastructure not already implied by the phases above. The target
architecture is a single modular monolith.
