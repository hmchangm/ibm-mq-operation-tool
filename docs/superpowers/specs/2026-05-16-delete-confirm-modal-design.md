# Delete Confirm Modal

**Date:** 2026-05-16

## Problem

The per-row Delete button in the message table fires immediately on click with no confirmation. A misclick permanently removes a message from the queue.

## Goal

Show a confirmation modal before executing the single-message delete, displaying the queue name and message preview text so the user can verify they are deleting the right message.

## Architecture

No new API endpoints needed. The change is entirely in the frontend.

### New component: `DeleteConfirmModal`

`frontend/src/components/DeleteConfirmModal.tsx`

Props:
- `row: MessageRow` — the message to be deleted
- `queue: string` — queue name shown in the modal header
- `onConfirm: () => void` — called when the user clicks Delete
- `onCancel: () => void` — called when the user clicks Cancel
- `deleting: boolean` — disables the Delete button and shows "Deleting…" while the mutation is in flight

Renders with the same overlay + card style as `BulkDeleteModal` (fixed overlay, centered white card, z-index 10).

Content:
```
Delete message from: <queue name>

  "<message preview text>"

              [ Cancel ]  [ Delete ]
```

### Changes to `MessageTable`

`onDelete` prop type changes from `(jmsMessageId: string) => void` to `(row: MessageRow) => void` so the full row is available in `App` for the modal.

### Changes to `App`

- Add `pendingDelete: MessageRow | null` state (null = modal hidden).
- `handleDelete` changes to `(row: MessageRow) => setDeletingId(null); setPendingDelete(row)` — sets state only, does not fire the mutation.
- A new `handleConfirmDelete` fires `del.mutate({ queueManager, channel, queue, jmsMessageId: pendingDelete.jmsMessageId })` and sets `deletingId`.
- On mutation success/error, `pendingDelete` is cleared (modal closes); browse re-runs as before.
- `DeleteConfirmModal` is rendered conditionally when `pendingDelete !== null`, passing `deleting={del.isPending}`.

## Flow

1. User clicks Delete in a row → `setPendingDelete(row)` → modal appears
2. User reads queue name + preview, clicks Delete → `handleConfirmDelete()` → mutation fires, button shows "Deleting…"
3. Mutation resolves → `setPendingDelete(null)` → modal closes, browse refreshes
4. User clicks Cancel at any point → `setPendingDelete(null)` → modal closes, no mutation

## Error handling

On mutation error, `pendingDelete` is cleared (modal closes) and the existing `setError` path in `App` surfaces the message in `StatusBar` — same as the current behavior.

## Testing

- `DeleteConfirmModal` is a pure presentational component and can be unit-tested directly (render with props, assert content and button states).
- `MessageTable` tests update the `onDelete` mock to expect a `MessageRow` argument.
- No new API or service tests required.
