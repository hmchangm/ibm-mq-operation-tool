# IBM MQ Operation Tool

Internal Quarkus/Kotlin UI for IBM MQ queue operations. All routes require OIDC authentication. Connections to IBM MQ are opened per-request and never held across requests.

## Features

- **Browse** — view up to the configured limit of messages in a queue (non-destructive)
- **Put** — publish a plain-text message to a queue
- **Delete** — remove a single message by JMS Message ID
- **Clean** — consume and discard all available messages from a queue
- **Export** — download all messages in a queue as an `.xlsx` file
- **Delete by ID…** — paste JMS Message IDs copied from the Excel export and bulk-delete them in one operation

All write operations (delete, put, clean, bulk-delete) are audited to the `mqops.audit` logger as structured JSON.

## Quick Start

```bash
# Dev mode with live reload (OIDC disabled, hot frontend proxy on :5173)
mvn quarkus:dev
```

Open [http://localhost:8080](http://localhost:8080).

## Build & Deploy

```bash
# Run tests
mvn test

# Build production JAR
mvn package

# Run production JAR
java -jar target/quarkus-app/quarkus-run.jar
```

The frontend SPA is built separately and committed to `src/main/resources/META-INF/resources/`. To rebuild it:

```bash
cd frontend && npm run build
```

## Configuration

### Required environment variables

| Variable | Description |
|---|---|
| `OIDC_AUTH_SERVER_URL` | OIDC provider URL (e.g. `https://auth.example.com/realms/mqops`) |
| `OIDC_CLIENT_ID` | OIDC client ID |
| `OIDC_CLIENT_SECRET` | OIDC client secret |

### MQ topology (`application.properties`)

All queue topology is static configuration. No queues are discoverable at runtime — only queues declared under `allowed-queues` can be accessed.

```properties
# How many messages Browse loads (default: 100)
mq.browse-limit=100

# Timeout when consuming messages during Clean (default: 500ms)
mq.receive-timeout-ms=500

# Queue manager — key is used in API requests
mq.queue-managers.<key>.host=mq.example.com
mq.queue-managers.<key>.port=1414
mq.queue-managers.<key>.name=QM1          # optional, defaults to key

# Channel — key is used in API requests
mq.queue-managers.<key>.channels.<key>.name=APP.SVRCONN
mq.queue-managers.<key>.channels.<key>.username=${MQ_QM1_OPS_USER:}   # optional
mq.queue-managers.<key>.channels.<key>.password=${MQ_QM1_OPS_PASSWORD:} # optional
mq.queue-managers.<key>.channels.<key>.allowed-queues[0]=DEV.QUEUE.1
mq.queue-managers.<key>.channels.<key>.allowed-queues[1]=DEV.QUEUE.2
```

### Example: two queue managers

```properties
mq.browse-limit=100
mq.receive-timeout-ms=500

mq.queue-managers.QM1.host=mq1.example.com
mq.queue-managers.QM1.port=1414
mq.queue-managers.QM1.channels.APP_SVRCONN.name=APP.SVRCONN
mq.queue-managers.QM1.channels.APP_SVRCONN.allowed-queues[0]=DEV.QUEUE.1

mq.queue-managers.QM2.host=mq2.example.com
mq.queue-managers.QM2.port=1414
mq.queue-managers.QM2.channels.OPS_SVRCONN.name=OPS.SVRCONN
mq.queue-managers.QM2.channels.OPS_SVRCONN.username=${MQ_QM2_OPS_USER:}
mq.queue-managers.QM2.channels.OPS_SVRCONN.password=${MQ_QM2_OPS_PASSWORD:}
mq.queue-managers.QM2.channels.OPS_SVRCONN.allowed-queues[0]=PROD.QUEUE.1
mq.queue-managers.QM2.channels.OPS_SVRCONN.allowed-queues[1]=PROD.QUEUE.2
```

## Audit Log

Write operations emit a JSON line to the `mqops.audit` logger:

```json
{"event":"mq_operation","timestamp":"2026-05-11T10:00:00Z","user":"alice","operation":"delete","queueManager":"QM1","channel":"APP.SVRCONN","queue":"DEV.QUEUE.1","removedCount":0,"result":"success","errorType":"","messageId":"ID:414d51..."}
```

Operations: `delete`, `put`, `clean`. Result values: `success`, `not_found`, `failure`. Browse and export are not audited.

## Export → Bulk Delete Workflow

1. Select a queue manager, channel, and queue name in the toolbar.
2. Click **Export** — an `export.xlsx` file downloads with all messages.
3. Open the file, identify messages to remove, copy the **JMS Message ID** column cells.
4. Click **Delete by ID…**, paste the IDs (one per line), click **Review**, then **Confirm**.
5. The tool reports how many were deleted and how many were already gone.
