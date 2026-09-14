-- Distributed lock table for the scheduled network scan.
--
-- Without it, every running instance would probe every device on its own timer:
-- the same host would be pinged N times per cycle and N competing status rows
-- would be written for it. One row per scheduled task name is enough to make
-- the cycle run exactly once across the whole deployment.
--
-- Table and column names are the defaults expected by the JDBC lock provider,
-- so no column-name override is needed in application code. The timestamp
-- columns are deliberately "without time zone": the provider binds their values
-- through a calendar pinned to an explicit zone, so storing an offset with them
-- would record the same instant twice in two different forms.

CREATE TABLE shedlock (
    name       character varying(64)          NOT NULL,
    lock_until timestamp(3) without time zone NOT NULL,
    locked_at  timestamp(3) without time zone NOT NULL,
    locked_by  character varying(255)         NOT NULL,
    CONSTRAINT shedlock_pkey PRIMARY KEY (name)
);
