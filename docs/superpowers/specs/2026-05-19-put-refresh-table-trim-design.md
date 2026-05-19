# Put Refresh + Table Trim Design

**Date:** 2026-05-19

## Summary

Three small frontend improvements: auto-refresh the message list after a successful put, verify the delete confirm dialog is wired up (already done), and trim the message table to show only Message ID and Preview columns.

## Features

### 1. List refresh after put

**Problem:** After a successful put, the message list stays stale — the user must manually click Browse to see the new message.

**Solution:** Split `PutModal`'s single `onClose` prop into two: `onSuccess` (put succeeded) and `onClose` (cancel / dismiss). Pass `onSuccess` to `usePut` as its success callback. In `App.tsx`, wire `onSuccess` to close the modal and fire `browse.mutate({ queueManager, channel, queue })` — the same pattern used by the delete flow.

Files changed:
- `frontend/src/components/PutModal.tsx` — add `onSuccess` prop, pass to `usePut` instead of `onClose`
- `frontend/src/App.tsx` — pass `onSuccess` prop that closes modal and re-browses

### 2. Delete confirm dialog

No code changes required. Already fully implemented in recent commits:
- `DeleteConfirmModal` component renders a modal with queue name, message preview, and Cancel/Delete buttons
- `App.tsx` wires `pendingDelete` state → `DeleteConfirmModal` → `handleConfirmDelete` → `del.mutate` → re-browse on success

### 3. Table columns: preview + msgid only

**Problem:** The message table shows four data columns (Message ID, Correlation ID, Type, Preview) plus a Delete button. Correlation ID and Type add width without being commonly needed at a glance.

**Solution:** Remove the Correlation ID and Type columns from `MessageTable` — both `<th>` header cells and corresponding `<td>` data cells. Remaining columns: Message ID, Preview, Delete button.

Files changed:
- `frontend/src/components/MessageTable.tsx` — remove correlationId and type columns

## No API or backend changes

All three features are purely frontend. No new API endpoints, no changes to `MessageRow` type, no Kotlin changes.
