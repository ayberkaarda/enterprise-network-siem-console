# 0002 — WebSocket/STOMP for real-time updates

Status: Accepted, implementation incomplete

## Context

The original frontend polled the backend on a timer to refresh device status.
A monitoring console needs to reflect device state changes, new incidents, and
live metrics with low latency, and polling does not scale well as the device
count or event rate grows.

## Decision

The backend exposes a WebSocket endpoint over STOMP. The frontend connects
using `@stomp/stompjs` with `sockjs-client` as the transport fallback. Distinct
topics are used per concern (device state, incidents, metrics) so a client can
subscribe only to what it needs, and each topic carries a well-defined payload
shape rather than a raw string or a full entity graph. If the WebSocket
connection cannot be established after repeated attempts with backoff, the
client falls back to polling rather than failing silently.

## Consequences

- The frontend needs explicit connection-state handling: connected,
  reconnecting, and a polling fallback state — this is more complex than a
  single polling loop.
- Backend and frontend must agree on topic names and payload shapes as a
  contract; changing a payload shape is a breaking change for any connected
  client.
- As of this writing, the backend publishes only a single topic with an
  inconsistent payload (sometimes a full entity, sometimes a raw string), and
  the frontend reacts to messages by re-fetching over REST rather than reading
  the pushed payload directly. This decision is accepted, but the
  implementation has not yet caught up to it.
