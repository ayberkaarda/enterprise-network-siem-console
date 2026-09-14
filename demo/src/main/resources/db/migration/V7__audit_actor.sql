-- Attributes an audit entry to whoever caused it.
--
-- Nullable, and left null for every row written before this migration: the
-- caller of those actions was never recorded, and inventing a value now
-- ("system", "unknown") would misrepresent history as more complete than it
-- is. Every row written from here on carries a real value — either an
-- authenticated username or the literal "system" for the unattended scheduled
-- scan, filled in by the application, never by a trigger (see the note on
-- V1__baseline.sql's sibling migrations: derived values belong in the service
-- layer, not in schema objects that would make the value untestable).

ALTER TABLE audit_log ADD COLUMN actor character varying(64);
