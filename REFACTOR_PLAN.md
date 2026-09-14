# Refactor Plan — Enterprise Network SIEM Console

## Purpose

Take this project from a hobby-scale demo (device ping monitor + audit log) to a
production-grade SIEM console: real correlation rules, an incident lifecycle,
event ingestion, authentication, and a SOC-style dashboard. This document tracks
phase status, what has actually been measured, and decisions still open.

## Current state (measured 2026-09-14)

- **Backend**: Spring Boot 4.1.0, `pom.xml` targets Java 26. `./mvnw -q verify`
  fails at the compile step because no Java 26 JDK is installed in this
  environment (Java 21 LTS is available) — see "Known blockers" below. No
  backend test result can be trusted until this is resolved.
- **Frontend**: Angular 21.2, standalone components. `ng build` succeeds, with
  two non-fatal warnings (CSS budget exceeded on `app.css`, two CommonJS
  dependencies causing an optimization bailout warning). `npm test` (the
  correct test command — not `npx vitest run` directly) fails to compile due to
  a naming collision between the `Device` service and `Device` type.
- **Dependencies**: `npm ci` installs cleanly but the tree carries 24 known
  vulnerabilities (1 critical, 10 high, 11 moderate, 2 low), not yet triaged.
- **Infrastructure**: `docker-compose.yml` already provisions Postgres,
  Prometheus, and Grafana alongside the backend and frontend. Database
  credentials are now supplied via `.env` (see `.env.example`); they were
  previously hardcoded in tracked files and have been rotated out of version
  control (the old value remains in git history and should be rotated for real
  if it was ever used outside local development).
- **Feature set**: only device status polling and a flat audit log exist today.
  Incident lifecycle, correlation rules, an event ingestion API, authentication,
  and the SOC-style multi-section frontend are not built yet.

## Known blockers (address at the start of the next phase of work)

1. **Java version mismatch.** `pom.xml` sets `<java.version>26</java.version>`;
   the only JDK available in this environment is 21 LTS. Decide whether to
   install a Java 26 toolchain or pin the project to 21, and record the
   reasoning as a decision record.
2. **Frontend test suite does not run.** `device.spec.ts` calls
   `TestBed.inject(Device)` where `Device` is a type, not the injectable
   service class, in `device.ts`. Needs a rename (service class vs. exported
   model type) before any frontend test result is meaningful.
3. **Dependency audit is red.** 1 critical, 10 high severity findings in the
   frontend dependency tree — needs triage before relying on `npm ci` output
   as a clean baseline.
4. **No schema migration tool yet.** The schema is currently managed by
   Hibernate (`ddl-auto=update`) against a single, unversioned
   `application.properties`. No dev/prod profile split exists.
5. **CORS is fully open.** A global CORS filter bean, `@CrossOrigin("*")` on
   the device controller, and an unrestricted WebSocket handshake origin are
   all in place. Acceptable only for local development; must be closed down
   before authentication is introduced.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 0 | Discovery, hardening the repo before feature work | In progress — hardcoded DB credential removed from tracked config; current build/test state measured (see above) |
| 1 | Backend hardening: profiles, Flyway, DTOs, validation, RFC 7807 errors, filtering | Not started |
| 2 | Correlation engine, incident lifecycle, event ingestion, anomaly detection, threat-intel stub | Not started |
| 3 | Real-time push (WebSocket/STOMP topics), virtual threads, scheduler locking, metrics | Not started |
| 4 | Authentication (JWT/RBAC), rate limiting, CORS hardening, audit trail | Not started |
| 5 | SOC-style frontend: feature routes, design tokens, charts, live threat level | Not started — visual identity already decided, see `docs/adr/0003-visual-identity-tactical-telemetry.md` and `docs/design/tokens.css` |
| 6 | Test coverage, CI/CD, documentation, seed data | Not started |

## Open decisions

- Target Java version: install 26, or pin the project to the 21 LTS actually
  available.
- Frontend test runner: keep the Angular-wrapped Vitest setup as-is, or adjust
  its configuration.
- Local development database strategy: require Postgres via docker-compose for
  all local work, or add a genuinely isolated dev-only fallback profile.

## Out of scope (explicitly, per project rules)

Splitting into microservices, adding a message broker (Kafka, RabbitMQ), or any
other infrastructure not already implied by the phases above. The target
architecture is a single modular monolith.
