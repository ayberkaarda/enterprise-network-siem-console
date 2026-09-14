# 0001 — Use PostgreSQL as the primary datastore

Status: Accepted

## Context

The original implementation used an in-memory H2 database. That is fine for a
demo, but a SIEM console needs durable storage: incidents, correlation rules,
and audit trails must survive a restart, and the schema needs to support
concurrent access from a scheduler and an API in the same process without data
loss.

## Decision

PostgreSQL is the primary database for every environment, provisioned via
`docker-compose.yml` (`siem-db` service). Schema changes are managed by
versioned migrations (Flyway), not by letting Hibernate generate or alter the
schema at startup. An H2-based profile may exist for isolated, fast local
iteration, but it is never the database used for integration tests or anything
resembling a real environment — it cannot silently diverge from the Postgres
schema.

## Consequences

- Local development requires either a running Postgres instance (via
  docker-compose) or an explicit, isolated dev profile.
- Schema evolution must go through migration files; ad hoc column changes via
  `ddl-auto=update` are not acceptable once migrations are introduced.
- Integration tests exercise a real Postgres instance (e.g. via Testcontainers)
  rather than relying on H2 behaving identically.
