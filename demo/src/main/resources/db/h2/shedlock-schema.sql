-- H2 counterpart of the lock table created for PostgreSQL in V5__shedlock.sql.
--
-- The in-memory profile runs without Flyway and the lock table is not a JPA
-- entity, so nothing else would create it; the scheduled scan would then fail
-- to take its lock on every run. IF NOT EXISTS is required because the in-memory
-- database outlives a single application context (DB_CLOSE_DELAY=-1) and this
-- script therefore runs again for every context the test suite builds.

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
