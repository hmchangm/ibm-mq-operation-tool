# Bulk Delete by ID Feature Design

**Date:** 2026-05-11  
**Status:** Approved

## Summary

Add a "Delete by ID…" capability that lets users paste a list of JMS Message IDs (copied from the Excel export) into a textarea, review them, confirm, and delete them all in one server-side operation. Returns a summary: deleted count and already-gone count.

## Architecture

One slice across three layers — no changes to `MqGateway`.

```
Toolbar (Delete by ID… button)
  → BulkDeleteModal (input → confirm → result)
  → POST /api/bulk-delete
  → MqApiResource.bulkDelete()
  → MqOperationService.bulkDelete()  [loops gateway.delete() per ID]
  ← BulkDeleteResponse(deleted, alreadyGone)
  ← Modal result: "Deleted N of M. X were already gone."
```

## Backend

### New data classes (`MqApiRequest.kt`)

```kotlin
data class BulkDeleteRequest(
    val queueManager: String,
    val channel: String,
    val queue: String,
    val jmsMessageIds: List<String>
)
data class BulkDeleteResponse(val deleted: Int, val alreadyGone: Int)
```

### `MqOperationService.bulkDelete()`

Loops `jmsMessageIds`, calls `gateway.delete()` for each:
- Success → increment `deleted`, call `audit.delete(..., "success")`
- `MessageGoneException` → increment `alreadyGone`, call `audit.delete(..., "not_found")`
- Any other exception → audit failure, rethrow (partial results not reported)

Returns `BulkDeleteResponse(deleted, alreadyGone)`.

### `MqApiResource` — new endpoint

```
POST /api/bulk-delete
Consumes:  application/json
Produces:  application/json
Request:   BulkDeleteRequest
Response:  200 BulkDeleteResponse  |  400 for invalid target
```

Uses existing `withTarget` helper — invalid target throws `BadRequestException`.

## Frontend

### `BulkDeleteModal` (`frontend/src/components/BulkDeleteModal.tsx`)

Three internal states managed with `useState`:

- **`input`** — textarea (placeholder: `"Paste JMS Message IDs, one per line…"`); Review button disabled when textarea is empty; Close button.
- **`confirm`** — shows "About to delete **N** messages from `[queue]`. Proceed?"; Confirm button; Back button returns to `input`.
- **`result`** — shows `"Deleted N of M. X were already gone."` (or `"Deleted N of N."` when none were gone); Close button.

ID parsing: `text.trim().split('\n').map(s => s.trim()).filter(Boolean)` — handles extra whitespace and blank lines from Excel paste.

Props: `queueManager`, `channel`, `queue`, `onClose`, `onError`.

### `api/useBulkDelete.ts`

Mutation hook following the `useClean` pattern:
- `mutationFn`: `POST /api/bulk-delete` with JSON body
- `onSuccess(result)`: callback with `{deleted, alreadyGone}`
- `onError(msg)`: callback with error string

### `Toolbar` changes

- New prop: `onBulkDelete: () => void`
- New "Delete by ID…" button after Export, disabled when `!canAct`

### `App.tsx` changes

- Add `showBulkDelete` state (`boolean`)
- Pass `onBulkDelete={() => setShowBulkDelete(true)}` to `Toolbar`
- Render `<BulkDeleteModal>` when `showBulkDelete` is true, with `onClose={() => setShowBulkDelete(false)}` and `onError={setError}`

## Error Handling

- Empty textarea → Review button disabled; no request sent
- Invalid target → 400 → `onError` → `StatusBar`
- MQ failure mid-loop → service rethrows; modal surfaces via `onError` → `StatusBar`; partial counts not reported

## Testing

### `MqOperationServiceTest` additions

Extend `FakeGateway` with `missingIds: Set<String> = emptySet()` constructor param; `delete()` throws `MessageGoneException` when the ID is in `missingIds`.

Tests:
- All IDs deleted → `BulkDeleteResponse(deleted=N, alreadyGone=0)`
- Mix of deleted and already-gone → correct split counts
- All IDs already gone → `BulkDeleteResponse(deleted=0, alreadyGone=N)`
- Each successful delete is audited; each already-gone is audited as `not_found`

### `MqApiResourceTest` additions

- `POST /api/bulk-delete` with `@TestSecurity` returns 400 for unknown queue manager

## Out of Scope

- Progress indicator per-ID during deletion
- Per-ID result breakdown (summary only)
- Parsing IDs from an uploaded `.xlsx` file
