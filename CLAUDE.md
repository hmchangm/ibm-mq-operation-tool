# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Dev mode with live reload
mvn quarkus:dev

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=MqOperationServiceTest

# Build
mvn package
```

## Architecture

Quarkus 3 / Kotlin / Java 17 server-rendered operations UI. No REST API — all routes are Qute HTML pages with small HTML fragment updates. All routes require OIDC authentication.

**Layer flow:** Qute resource → `MqOperationService` → `MqGateway` → IBM MQ JMS

### Key packages

- `config/` — `MqTopologyConfig` (SmallRye `@ConfigMapping`) declares the static queue topology. `MqTopologyService` resolves a request's (queueManagerKey, channelKey, queueName) tuple into a fully-populated `MqTarget`, enforcing the server-side allowlist. `InvalidMqTargetException` is thrown for any unknown or disallowed combination.

- `mq/` — `MqGateway` interface (browse, delete, putText, clean). `IbmMqGateway` implements it using `JmsFactoryFactory`/`WMQConstants` directly — **not** Quarkus JMS abstractions. Every operation opens its own `JMSContext` and closes it with `use {}`. Connections are never held across requests.

- `service/` — `MqOperationService` wraps the gateway with audit logging. It distinguishes `InterruptedException` and `CancellationException` (rethrow silently) from operational failures (audit then rethrow).

- `audit/` — `StructuredAuditLogger` writes JSON lines to the `mqops.audit` logger. `AuditLineFormatter` escapes all field values as JSON strings and sanitizes the error type to a class-name-only pattern (`[A-Za-z0-9_.]*(Exception|Error)`) to prevent log injection and accidental leakage of message body or MQ credentials.

### MQ behavior notes

- **Delete** uses a JMS selector on `JMSMessageID`. Single quotes in the ID are escaped (`''`) before building the selector string.
- **Clean** loops `consumer.receive(timeoutMs)` until it returns null; returns removed count.
- **Browse** uses `QueueBrowser`; non-`TextMessage` types appear in the table with `[payload preview unsupported]`.

### Configuration

All topology is static config (`application.properties` / env). Queue manager `name` is optional and falls back to the map key. Channel `username`/`password` are optional; blank strings are normalized to null.

```
mq.browse-limit=100
mq.receive-timeout-ms=500
mq.queue-managers.<key>.host / .port / .name
mq.queue-managers.<key>.channels.<key>.name / .username / .password / .allowed-queues[n]
```

OIDC: `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`

MQ credentials: passed as env vars referenced by `${MQ_QM1_OPS_USER:}` style placeholders in properties.

## Testing approach

Service and config tests are plain JUnit5 — no `@QuarkusTest`, no mocking framework. Tests construct dependencies directly using fake/stub implementations defined in each test file (`FakeGateway`, `RecordingAuditLogger`, `TestMqTopologyConfig`). `MqTopologyService` has a secondary constructor that accepts `MqTopologyView` for this purpose.

Quarkus `@TestSecurity` is used in UI resource tests to bypass OIDC.
