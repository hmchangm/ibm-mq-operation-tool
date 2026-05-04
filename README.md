# IBM MQ Operation Tool

Internal Quarkus Kotlin UI for IBM MQ queue operations.

## Features

- OIDC-protected UI.
- Configured queue manager -> channel -> queue hierarchy.
- Optional MQ username/password per channel.
- Lazy MQ connection: startup and target selection do not connect to IBM MQ.
- Browse first configured limit of messages.
- Delete selected message.
- Put a plain text message.
- Clean queue by consuming available messages.
- Audit write operations to application logs.

## Run

```bash
mvn quarkus:dev
```

Configure OIDC and MQ topology in `src/main/resources/application.properties` or environment variables.

## Test

```bash
mvn test
```
