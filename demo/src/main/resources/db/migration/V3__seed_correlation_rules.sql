-- Built-in correlation rules, shipped as data.
--
-- These three are the default detection content. They are rows, not Java
-- branches, so an operator can retune a threshold, widen a window or disable a
-- noisy rule against a running system. The engine only knows how to evaluate a
-- condition *type*; everything numeric in a detection lives here.

INSERT INTO correlation_rule (name, enabled, condition_json, threshold_count, window_seconds, severity, created_at)
VALUES (
    'Device flapping',
    true,
    '{"type":"flap"}',
    3,
    300,
    'MEDIUM',
    now()
);

INSERT INTO correlation_rule (name, enabled, condition_json, threshold_count, window_seconds, severity, created_at)
VALUES (
    'Simultaneous subnet outage',
    true,
    '{"type":"subnet_outage","downStatuses":["INACTIVE"],"prefixOctets":3}',
    3,
    120,
    'HIGH',
    now()
);

INSERT INTO correlation_rule (name, enabled, condition_json, threshold_count, window_seconds, severity, created_at)
VALUES (
    'Latency beyond device baseline',
    true,
    '{"type":"latency_anomaly"}',
    1,
    300,
    'MEDIUM',
    now()
);
