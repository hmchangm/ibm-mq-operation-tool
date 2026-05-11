# Excel Export Feature Design

**Date:** 2026-05-11  
**Status:** Approved

## Summary

Add a server-side "Export All" capability that fetches every message from a selected queue and returns them as a downloadable `.xlsx` file. Uses Apache POI on the JVM; no DuckDB dependency.

## Architecture

One thin slice across three layers — no new pages, no new modals, no changes to `MqGateway`.

```
Toolbar (Export button)
  → POST /api/export
  → MqApiResource.export()
  → MqOperationService.export()  [calls gateway.browse(target, Int.MAX_VALUE)]
  → ExcelExporter.export(rows)   [Apache POI → ByteArray]
  ← application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  ← browser file download
```

## Backend

### New dependency

`pom.xml`: add `org.apache.poi:poi-ooxml:5.3.0`.

### `ExcelExporter`

- Package: `com.acme.mqops.export`
- `@ApplicationScoped`
- Single method: `export(rows: List<MessageRow>): ByteArray`
- Creates one `XSSFWorkbook` with sheet named "Messages"
- Header row columns (in order): `JMS Message ID`, `Correlation ID`, `Timestamp`, `Expiration`, `Priority`, `Type`, `Preview`
- Timestamps (epoch ms longs) formatted as `yyyy-MM-dd HH:mm:ss UTC`; null fields written as empty cells
- Returns workbook serialised to `ByteArray` via `ByteArrayOutputStream`

### `MqOperationService.export()`

```kotlin
fun export(user: String, target: MqTarget): List<MessageRow> =
    gateway.browse(target, Int.MAX_VALUE)
```

No audit event — browse is not audited; mutating ops are.

### `MqApiResource` — new endpoint

```
POST /api/export
Consumes:  application/json
Produces:  application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

Request body: `ExportRequest(queueManager: String, channel: String, queue: String)`

Response:
- `200` with file bytes
- `Content-Disposition: attachment; filename="export.xlsx"`
- Error responses follow existing pattern: `400` for invalid target, `500` for MQ failures

## Frontend

### `Toolbar` changes

- New props: `onExport: () => void`, `exporting: boolean`
- New "Export" button, disabled when `!canAct || exporting`, positioned after "Clean"

### `api/export.ts`

New function `exportQueue(queueManager, channel, queue)`:
1. `POST /api/export` with JSON body
2. Receive response as `Blob`
3. Create temporary object URL → programmatically click a hidden `<a download="export.xlsx">` → revoke URL
4. On error, throw so the caller can surface it in `StatusBar`

### `App.tsx` changes

- Add `exporting` state (`boolean`)
- Add handler: sets `exporting = true`, calls `exportQueue()`, resets to `false` in finally, surfaces errors to `StatusBar`
- Pass `onExport` and `exporting` down to `Toolbar`

## Error Handling

- Invalid target → `BadRequestException` → 400 (existing `withTarget` helper)
- MQ failure → 500 (existing unhandled exception propagation)
- Frontend: errors displayed in existing `StatusBar`, same as browse/delete failures

## Testing

### `ExcelExporterTest` (plain JUnit5, no framework)

- Constructs `ExcelExporter` directly
- Asserts header row columns match spec
- Asserts cell values for a fully-populated `MessageRow`
- Asserts null fields (correlationId, timestamp, expiration) produce empty cells
- Asserts timestamp formatting (`yyyy-MM-dd HH:mm:ss UTC`)

### `MqApiResourceTest` additions

- `POST /api/export` with `@TestSecurity` → assert `200`, correct `Content-Type`, `Content-Disposition` contains `attachment`

### Not needed

No new MQ container integration test — export reuses `gateway.browse()`, already covered by `IbmMqGatewayTest`.

## Out of Scope

- Full (untruncated) message body in export — preview field (240 chars) is used as-is
- Configurable export row limit — `Int.MAX_VALUE` passed directly
- CSV or parquet alternative formats
