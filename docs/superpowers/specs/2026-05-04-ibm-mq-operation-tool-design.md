# IBM MQ Operation Tool Design

Date: 2026-05-04

## Goal

Build an internal IBM MQ operation tool UI for authenticated users to browse queue messages, delete a selected message, put a plain text message, and clean a queue.

The application will use Quarkus, Kotlin, Java 17, Qute, and Quarkus OIDC. IBM MQ access will use IBM MQ JMS APIs through `JmsFactoryFactory` and `WMQConstants`, not Quarkus JMS abstractions.

## Scope

Version 1 includes:

- OIDC login for all UI routes.
- No app-level role control. Any authenticated user can use all operations.
- Configured queue topology: queue manager -> channel -> allowed queues.
- Optional IBM MQ username/password per configured channel.
- Browse the first configured limit of messages from a selected queue.
- Show message metadata and a short `TextMessage` payload preview in the browse table.
- Delete a selected message directly from the browse table after confirmation.
- Put one plain text `TextMessage` to the selected queue.
- Clean a selected queue by consuming all currently available messages through JMS.
- Audit write operations to application logs.

Out of scope for version 1:

- Role-based authorization.
- User-entered MQ connection details or credentials.
- Payload preview for non-`TextMessage` body types.
- Full message detail view.
- Backup or quarantine queue behavior before delete.
- PCF/admin purge commands.
- Search across all messages beyond the configured browse limit.
- Separate frontend SPA or REST API for external clients.

## UX

After OIDC login, the user lands on a queue operations workspace.

The user selects a target in this order:

1. Queue manager.
2. Channel.
3. Queue.

The UI only presents configured combinations. Every submitted request revalidates the selected queue manager, channel, and queue against server-side configuration.

The selected queue workspace exposes three operations:

- Browse/Delete.
- Put Message.
- Clean Queue.

### Browse/Delete

The browse table loads the first configured limit of messages. Each row shows:

- JMS message ID.
- Correlation ID, if present.
- Timestamp, if present.
- Expiration, if present.
- Priority, if present.
- Message type.
- Short body preview for `TextMessage`.

Each row has a delete action. The user clicks delete, confirms the action in a modal, and the server consumes the exact selected message. If the message no longer exists when delete runs, the UI shows "message no longer available" and refreshes the table.

### Put Message

The put form contains a plain text input/editor for one message body. Submitting the form sends a new `TextMessage` to the selected queue.

Version 1 does not support custom JMS headers, properties, or file upload.

### Clean Queue

The clean operation removes all currently available messages from the selected queue by consuming them via JMS. Before the clean runs, the user must type the exact queue name.

The result shows how many messages were removed. If the operation fails partway through, the result shows the number removed before the failure when known.

## Architecture

The app is a single Quarkus service with server-rendered Qute pages and small server-rendered fragments for table/form updates.

Primary components:

- Qute UI resources.
- OIDC security configuration.
- MQ topology configuration.
- MQ connection factory builder.
- MQ operation service.
- Audit logger.

UI resource classes do not directly use IBM MQ JMS APIs. They validate request inputs, call application services, and render Qute templates/fragments.

The MQ operation service owns browse, delete, put, and clean behavior. IBM MQ connection setup is isolated behind a connection factory builder so that credentials, host, port, channel, queue manager, and transport settings remain centralized.

## Configuration Model

Configuration defines a fixed topology of queue managers, channels, and queues. A channel may omit MQ credentials if the queue manager allows unauthenticated connection for that channel.

Conceptual configuration:

```yaml
mq:
  browse-limit: 100
  receive-timeout-ms: 500
  queue-managers:
    QM1:
      host: mq-host-1
      port: 1414
      channels:
        APP.SVRCONN:
          username: ${MQ_QM1_APP_USER:}
          password: ${MQ_QM1_APP_PASSWORD:}
          allowed-queues:
            - DEV.QUEUE.1
            - DEV.QUEUE.2
        OPS.SVRCONN:
          allowed-queues:
            - OPS.QUEUE.1
```

The implementation may use Quarkus `@ConfigMapping` with Kotlin data classes. Secrets should be provided through environment variables or an external secret mechanism, not committed values.

## MQ Behavior

All MQ operations use IBM MQ JMS factory APIs. The application will create an IBM MQ connection factory for the selected queue manager and channel using `JmsFactoryFactory` and `WMQConstants`.

### Browse

Browse creates a JMS connection and session, opens a `QueueBrowser`, and returns at most `browse-limit` rows.

For `TextMessage`, the service reads text and creates a truncated preview. For non-text message types, the row still appears with metadata, but the preview is marked unsupported.

### Delete

Delete uses the identifier captured during browse, expected to be `JMSMessageID`, to consume only the selected message from the queue. The service uses a JMS selector for the selected message ID.

If no message is consumed, the application reports that the message is no longer available and refreshes the table.

### Put

Put creates a `TextMessage` from the submitted plain text body and sends it to the selected queue.

### Clean

Clean receives messages from the selected queue until no message is immediately available within the configured receive timeout. The operation returns the count of consumed messages.

Clean uses JMS consume behavior only. It does not call IBM MQ PCF/admin purge commands in version 1.

## Security

All UI routes require OIDC authentication. Any authenticated identity can browse, delete, put, and clean.

MQ credentials are optional per configured channel. If present, they are read from configuration and used by the server only. The UI never asks for or displays MQ credentials.

Server-side validation rejects any queue manager, channel, or queue not present in the configured hierarchy.

## Auditing

Write operations are logged to application logs. Audit entries include:

- Authenticated user identity.
- Operation: delete, put, or clean.
- Queue manager.
- Channel.
- Queue.
- JMS message ID for delete when available.
- Result: success, not found, or failure.
- Removed message count for clean.
- Timestamp.
- Error summary for failures.

Audit logs must not include message body content or MQ passwords.

## Error Handling

The UI shows actionable errors without exposing secrets.

Expected cases:

- Invalid target: reject request and show a validation error.
- MQ connection failure: show a connection error.
- Browse failure: keep the selected target and show the failure.
- Delete missing message: show "message no longer available" and refresh the table.
- Put failure: keep the submitted text so the user can retry.
- Clean failure: show removed count before failure when known.
- Non-`TextMessage`: show metadata and unsupported preview.
- Large `TextMessage`: truncate table preview.

## Testing Strategy

Testing should focus on service boundaries and safety controls.

Planned tests:

- Configuration validation for valid and invalid queue manager/channel/queue combinations.
- UI route tests for OIDC protection and invalid target rejection.
- MQ operation service tests for browse limit, text preview truncation, selector delete, plain text put, and clean consume loop.
- Audit logging tests for delete, put, and clean log fields.
- Optional integration profile for a real IBM MQ test queue manager when credentials are supplied.

The implementation should wrap direct JMS resource creation enough to test operation rules without requiring a live IBM MQ server for every test.

## Recommended Implementation Approach

Use Quarkus Qute server-rendered pages with small fragment updates for browse table, delete confirmation results, put results, and clean results.

This keeps the app simple to deploy, avoids a separate frontend build, keeps MQ operations server-controlled, and fits an internal operations tool. A separate SPA or public REST API is unnecessary for version 1.
