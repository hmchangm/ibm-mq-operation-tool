# SPA Redesign Design

**Date:** 2026-05-05  
**Goal:** Replace server-rendered Qute pages with a React + TypeScript SPA served from Quarkus, backed by a JSON REST API.

---

## Decisions

| Decision | Choice |
|----------|--------|
| Frontend framework | React + TypeScript (Vite) |
| Build integration | Separate `frontend/` project; Vite dev proxy to Quarkus |
| Auth | BFF — Quarkus OIDC session cookie, SPA calls `/api/me` to check auth |
| State management | React Query (no Redux) |
| Layout | Toolbar + full-width table; Put and Clean open modal dialogs |
| Queue allowlist | Removed — user types any queue name freely |
| Integration testing | IBM MQ Testcontainers via `QuarkusTestResourceLifecycleManager` |

---

## Architecture

### Backend

**Remove entirely:**
- `src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt`
- `src/main/resources/templates/QueueOpsResource/` (all three Qute templates)
- `src/main/resources/META-INF/resources/styles.css`
- `allowed-queues` config from `application.properties`, `ChannelView`, `MqTopologyConfig`, `MqTopologyService`

**Add:**
- `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt` — `@Path("/api") @Authenticated` REST resource returning JSON
- `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt` — request body data classes
- `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt` — replaces `QueueOpsResourceTest`
- `src/test/kotlin/com/acme/mqops/mq/IbmMqGatewayTest.kt` — Testcontainers integration test
- `src/test/kotlin/com/acme/mqops/mq/MqContainerResource.kt` — `QuarkusTestResourceLifecycleManager`

**Modify:**
- `src/main/kotlin/com/acme/mqops/config/MqTopologyConfig.kt` — remove `allowedQueues()` from `ChannelView`; remove `MqChannelTopology.allowedQueues`
- `src/main/kotlin/com/acme/mqops/config/MqTopologyService.kt` — remove queue allowlist validation; `resolve()` accepts any queue name
- `src/main/resources/application.properties` — remove all `allowed-queues[n]` lines; add `quarkus.http.cors=true` for dev, add SPA static file fallback
- `pom.xml` — add `quarkus-rest-jackson`, `quarkus-smallrye-openapi` (optional), testcontainers dependency

### Frontend

```
frontend/
  src/
    api/              # React Query hooks
      useMe.ts
      useTopology.ts
      useBrowse.ts
      useDelete.ts
      usePut.ts
      useClean.ts
    components/
      Toolbar.tsx       # queue manager select, channel select, queue text input, Browse / Put… / Clean… buttons
      MessageTable.tsx  # full-width results table, Delete button per row
      PutModal.tsx      # textarea body input, submit → usePut, closes on success
      CleanModal.tsx    # confirmation text input, submit → useClean, closes on success
      StatusBar.tsx     # message count line, error toast
    App.tsx             # layout shell — calls useMe on mount, redirects to /login on 401
    main.tsx
    types.ts            # MessageRow, Topology, MeResponse
  vite.config.ts        # proxy /api/* and /login → http://localhost:8080
  index.html
  package.json
  tsconfig.json
```

---

## REST API

All endpoints require OIDC session cookie (`@Authenticated`). All request and response bodies are JSON.

| Method | Path | Request body | Success response |
|--------|------|-------------|-----------------|
| `GET` | `/api/me` | — | `{ "user": "alice" }` |
| `GET` | `/api/topology` | — | `{ "queueManagers": { "QM1": { "name": "QM1", "channels": { "APP_SVRCONN": { "name": "APP.SVRCONN" } } } } }` |
| `POST` | `/api/browse` | `{ "queueManager", "channel", "queue" }` | `MessageRow[]` |
| `POST` | `/api/delete` | `{ "queueManager", "channel", "queue", "jmsMessageId" }` | `204 No Content` or `410 Gone` |
| `POST` | `/api/put` | `{ "queueManager", "channel", "queue", "body" }` | `204 No Content` |
| `POST` | `/api/clean` | `{ "queueManager", "channel", "queue" }` | `{ "removed": 7 }` |

`InvalidMqTargetException` (unknown queue manager or channel key) → `400 Bad Request`.  
`MessageGoneException` → `410 Gone`.

---

## Queue Allowlist Removal

`ChannelView.allowedQueues()` is removed. `MqTopologyService.resolve()` no longer checks the queue name — it builds an `MqTarget` from `(queueManagerKey, channelKey, queueName)` with no filtering. `application.properties` removes all `allowed-queues[n]` entries. `MqTopologyServiceTest` drops the allowlist test cases.

---

## Auth Flow (BFF)

1. `App.tsx` calls `GET /api/me` on mount.
2. If `401` → `window.location.href = '/login'` (Quarkus handles OIDC redirect).
3. After OIDC login Quarkus redirects back; session cookie is set.
4. Subsequent API calls include the cookie automatically (same-origin in production; Vite proxy in dev).
5. `DevHttpAuthMechanism` (dev profile) continues to work — `/api/me` returns `{ "user": "dev" }`.

---

## UI Layout

**Toolbar (top bar):**  
Queue manager `<select>` · Channel `<select>` · Queue `<input>` (free text) · `Browse` button · `Put…` button · `Clean…` button

**Message table (full width):**  
Columns: Message ID · Correlation ID · Type · Preview · `Delete` button per row. Empty state shown when no browse has been performed or results are empty.

**Put modal:**  
`<textarea>` for message body. Submit calls `POST /api/put`. Closes on success, shows error inline on failure.

**Clean modal:**  
Text input to confirm queue name. Submit calls `POST /api/clean`. Shows removed count on success. Closes on success.

**Status bar (bottom):**  
Shows message count after browse. Error toasts for failed operations auto-dismiss after 4 seconds.

---

## Testing

### Unit tests (unchanged)
- `MqOperationServiceTest` — fake gateway, no containers
- `MqTopologyServiceTest` — drops allowlist test cases, keeps all others
- `AuditLineFormatterTest` — unchanged

### API tests (`@QuarkusTest`)
- `MqApiResourceTest` — replaces `QueueOpsResourceTest`; tests JSON responses, auth enforcement (401 without `@TestSecurity`), topology endpoint, and error cases (unknown queue manager → 400)

### Testcontainers integration
- `MqContainerResource` — `QuarkusTestResourceLifecycleManager` that starts `icr.io/ibm-messaging/mq:latest`, sets `LICENSE=accept` and `MQ_QMGR_NAME=QM1`, injects mapped host/port into Quarkus config
- `IbmMqGatewayTest` — `@QuarkusTest @QuarkusTestResource(MqContainerResource::class)`; tests `browse` (empty queue returns empty list), `putText` then `browse` (message appears), `delete` (message gone), `clean` (returns removed count)

---

## Build

**Development:**
```bash
# Terminal 1
mvn quarkus:dev          # Quarkus on :8080

# Terminal 2
cd frontend && npm run dev   # Vite on :5173, proxies /api/* → :8080
```

**Production build:**
```bash
cd frontend && npm run build   # outputs to frontend/dist/
cp -r frontend/dist/* src/main/resources/META-INF/resources/
mvn package
```

Quarkus serves `index.html` as the SPA entry point. All unmatched paths return `index.html` for client-side routing (configured via Quarkus `quarkus.http.non-application-root-path` or a catch-all route).
