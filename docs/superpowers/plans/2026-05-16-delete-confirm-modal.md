# Delete Confirm Modal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a confirmation modal to the per-row Delete button in the message table, showing the queue name and message preview before firing the delete mutation.

**Architecture:** New `DeleteConfirmModal` pure component renders on top of `App` when `pendingDelete` state is set. Clicking Delete in a row sets that state; the modal's confirm button fires the mutation. `MessageTable.onDelete` prop changes to pass the full `MessageRow` so `App` has the preview text.

**Tech Stack:** React 18, TypeScript, Vitest, @testing-library/react, @testing-library/user-event

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `frontend/src/components/DeleteConfirmModal.tsx` | Modal UI — queue name, preview, Cancel/Delete buttons |
| Create | `frontend/src/components/DeleteConfirmModal.test.tsx` | Unit tests for the modal |
| Create | `frontend/src/components/MessageTable.test.tsx` | Tests verifying `onDelete` passes full `MessageRow` |
| Modify | `frontend/src/components/MessageTable.tsx` | Change `onDelete` prop from `(id: string)` to `(row: MessageRow)` |
| Modify | `frontend/src/App.tsx` | Add `pendingDelete` state, wire confirm flow, render modal |

---

### Task 1: DeleteConfirmModal component (TDD)

**Files:**
- Create: `frontend/src/components/DeleteConfirmModal.test.tsx`
- Create: `frontend/src/components/DeleteConfirmModal.tsx`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/components/DeleteConfirmModal.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DeleteConfirmModal } from './DeleteConfirmModal'
import type { MessageRow } from '../types'

const row: MessageRow = {
  jmsMessageId: 'ID:abc123',
  correlationId: null,
  timestamp: null,
  expiration: null,
  priority: null,
  type: 'TextMessage',
  preview: 'Hello world',
}

describe('DeleteConfirmModal', () => {
  it('shows queue name and message preview', () => {
    render(
      <DeleteConfirmModal
        row={row}
        queue="DEV.QUEUE.1"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        deleting={false}
      />
    )
    expect(screen.getByText(/DEV\.QUEUE\.1/)).toBeInTheDocument()
    expect(screen.getByText(/Hello world/)).toBeInTheDocument()
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    render(
      <DeleteConfirmModal
        row={row}
        queue="DEV.QUEUE.1"
        onConfirm={vi.fn()}
        onCancel={onCancel}
        deleting={false}
      />
    )
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('calls onConfirm when Delete is clicked', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(
      <DeleteConfirmModal
        row={row}
        queue="DEV.QUEUE.1"
        onConfirm={onConfirm}
        onCancel={vi.fn()}
        deleting={false}
      />
    )
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('disables Delete button and shows "Deleting…" when deleting is true', () => {
    render(
      <DeleteConfirmModal
        row={row}
        queue="DEV.QUEUE.1"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        deleting={true}
      />
    )
    expect(screen.getByRole('button', { name: 'Deleting…' })).toBeDisabled()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm test -- DeleteConfirmModal
```

Expected: 4 failures — `DeleteConfirmModal` not found.

- [ ] **Step 3: Implement DeleteConfirmModal**

Create `frontend/src/components/DeleteConfirmModal.tsx`:

```tsx
import type { CSSProperties } from 'react'
import type { MessageRow } from '../types'

interface DeleteConfirmModalProps {
  row: MessageRow
  queue: string
  onConfirm: () => void
  onCancel: () => void
  deleting: boolean
}

export function DeleteConfirmModal({ row, queue, onConfirm, onCancel, deleting }: DeleteConfirmModalProps) {
  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <h3 style={{ margin: '0 0 12px' }}>Delete message</h3>
        <p style={{ margin: '0 0 8px', fontSize: 13 }}>
          From queue: <code>{queue}</code>
        </p>
        <p
          style={{
            margin: '0 0 16px',
            fontFamily: 'monospace',
            fontSize: 12,
            background: '#f5f5f5',
            padding: 8,
            borderRadius: 3,
            wordBreak: 'break-all',
          }}
        >
          {row.preview}
        </p>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button onClick={onCancel}>Cancel</button>
          <button onClick={onConfirm} disabled={deleting} style={{ color: '#d33f49' }}>
            {deleting ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </div>
    </div>
  )
}

const overlayStyle: CSSProperties = {
  position: 'fixed',
  inset: 0,
  background: 'rgba(0,0,0,0.4)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  zIndex: 10,
}

const modalStyle: CSSProperties = {
  background: '#fff',
  borderRadius: 6,
  padding: 24,
  minWidth: 420,
  maxWidth: 560,
  width: '100%',
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm test -- DeleteConfirmModal
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/DeleteConfirmModal.tsx frontend/src/components/DeleteConfirmModal.test.tsx
git commit -m "feat: add DeleteConfirmModal component"
```

---

### Task 2: Update MessageTable to pass full MessageRow to onDelete

**Files:**
- Modify: `frontend/src/components/MessageTable.tsx`
- Create: `frontend/src/components/MessageTable.test.tsx`

- [ ] **Step 1: Write failing test for updated onDelete signature**

Create `frontend/src/components/MessageTable.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MessageTable } from './MessageTable'
import type { MessageRow } from '../types'

const row: MessageRow = {
  jmsMessageId: 'ID:abc123',
  correlationId: null,
  timestamp: null,
  expiration: null,
  priority: null,
  type: 'TextMessage',
  preview: 'Hello world',
}

describe('MessageTable', () => {
  it('shows empty state when there are no rows', () => {
    render(<MessageTable rows={[]} onDelete={vi.fn()} deleting={null} />)
    expect(screen.getByText(/No messages/)).toBeInTheDocument()
  })

  it('calls onDelete with the full MessageRow when Delete is clicked', async () => {
    const user = userEvent.setup()
    const onDelete = vi.fn()
    render(<MessageTable rows={[row]} onDelete={onDelete} deleting={null} />)
    await user.click(screen.getByRole('button', { name: /Delete message/ }))
    expect(onDelete).toHaveBeenCalledWith(row)
  })

  it('disables the button and updates aria-label when that row is deleting', () => {
    render(<MessageTable rows={[row]} onDelete={vi.fn()} deleting="ID:abc123" />)
    expect(screen.getByRole('button', { name: /Deleting message/ })).toBeDisabled()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm test -- MessageTable
```

Expected: the `onDelete` called-with test fails — currently passes a string, not the full row.

- [ ] **Step 3: Update MessageTable**

In `frontend/src/components/MessageTable.tsx`, make these changes:

Add `MessageRow` import at the top:
```tsx
import type { MessageRow } from '../types'
```

Change the props interface:
```tsx
interface MessageTableProps {
  rows: MessageRow[]
  onDelete: (row: MessageRow) => void
  deleting: string | null
}
```

Change the button's `onClick` in the row map (was `onDelete(row.jmsMessageId)`):
```tsx
onClick={() => onDelete(row)}
```

The complete updated file `frontend/src/components/MessageTable.tsx`:

```tsx
import type { MessageRow } from '../types'

interface MessageTableProps {
  rows: MessageRow[]
  onDelete: (row: MessageRow) => void
  deleting: string | null
}

export function MessageTable({ rows, onDelete, deleting }: MessageTableProps) {
  if (rows.length === 0) {
    return (
      <div style={{ padding: 32, color: '#888', textAlign: 'center' }}>
        No messages. Use Browse to load queue contents.
      </div>
    )
  }

  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
      <thead>
        <tr style={{ background: '#f0f4f8' }}>
          <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>
            Message ID
          </th>
          <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>
            Correlation ID
          </th>
          <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>
            Type
          </th>
          <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>
            Preview
          </th>
          <th style={{ padding: '6px 8px', borderBottom: '1px solid #ddd' }} />
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.jmsMessageId} style={{ borderBottom: '1px solid #f0f0f0' }}>
            <td
              style={{
                padding: '6px 8px',
                fontFamily: 'monospace',
                fontSize: 11,
                whiteSpace: 'nowrap',
              }}
            >
              {row.jmsMessageId}
            </td>
            <td style={{ padding: '6px 8px', fontFamily: 'monospace', fontSize: 11 }}>
              {row.correlationId ?? '—'}
            </td>
            <td style={{ padding: '6px 8px' }}>{row.type}</td>
            <td
              style={{
                padding: '6px 8px',
                maxWidth: 400,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {row.preview}
            </td>
            <td style={{ padding: '6px 8px' }}>
              <button
                onClick={() => onDelete(row)}
                disabled={deleting === row.jmsMessageId}
                aria-label={
                  deleting === row.jmsMessageId
                    ? `Deleting message ${row.jmsMessageId}`
                    : `Delete message ${row.jmsMessageId}`
                }
                style={{
                  background: '#d33f49',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 3,
                  padding: '2px 8px',
                  cursor: 'pointer',
                }}
              >
                {deleting === row.jmsMessageId ? 'Deleting' : 'Delete'}
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm test -- MessageTable
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/MessageTable.tsx frontend/src/components/MessageTable.test.tsx
git commit -m "feat: pass full MessageRow to onDelete in MessageTable"
```

---

### Task 3: Wire App to use pendingDelete state and render modal

**Files:**
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Update App.tsx**

The complete updated `frontend/src/App.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { exportQueue } from './api/exportQueue'
import { BulkDeleteModal } from './components/BulkDeleteModal'
import { DeleteConfirmModal } from './components/DeleteConfirmModal'
import { useMe } from './api/useMe'
import { useTopology } from './api/useTopology'
import { useBrowse } from './api/useBrowse'
import { useDelete } from './api/useDelete'
import { Toolbar } from './components/Toolbar'
import { MessageTable } from './components/MessageTable'
import { PutModal } from './components/PutModal'
import { CleanModal } from './components/CleanModal'
import { StatusBar } from './components/StatusBar'
import type { MessageRow } from './types'

export default function App() {
  const me = useMe()
  const topology = useTopology()

  const [queueManager, setQueueManager] = useState('')
  const [channel, setChannel] = useState('')
  const [queue, setQueue] = useState('')
  const [rows, setRows] = useState<MessageRow[]>([])
  const [browseCount, setBrowseCount] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showPut, setShowPut] = useState(false)
  const [showClean, setShowClean] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<MessageRow | null>(null)
  const [exporting, setExporting] = useState(false)
  const [showBulkDelete, setShowBulkDelete] = useState(false)

  useEffect(() => {
    if (me.error?.message === 'unauthenticated') {
      window.location.href = '/login'
    }
  }, [me.error])

  const browse = useBrowse(
    (result) => {
      setRows(result)
      setBrowseCount(result.length)
      setError(null)
    },
    setError
  )

  const del = useDelete(
    () => {
      setDeletingId(null)
      setPendingDelete(null)
      browse.mutate({ queueManager, channel, queue })
    },
    (msg) => {
      setDeletingId(null)
      setPendingDelete(null)
      setError(msg)
    }
  )

  const handleDelete = (row: MessageRow) => {
    setPendingDelete(row)
  }

  const handleConfirmDelete = () => {
    if (!pendingDelete) return
    setDeletingId(pendingDelete.jmsMessageId)
    del.mutate({ queueManager, channel, queue, jmsMessageId: pendingDelete.jmsMessageId })
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      await exportQueue({ queueManager, channel, queue })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Export failed')
    } finally {
      setExporting(false)
    }
  }

  const handleQueueManagerChange = (v: string) => {
    setQueueManager(v)
    setChannel('')
    setRows([])
    setBrowseCount(null)
  }

  if (me.isLoading || topology.isLoading) {
    return <div style={{ padding: 24 }}>Loading...</div>
  }

  if (!topology.data) {
    return <div style={{ padding: 24, color: '#d33f49' }}>Failed to load topology.</div>
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}
    >
      <div
        style={{
          background: '#255f85',
          color: '#fff',
          padding: '8px 12px',
          fontWeight: 700,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}
      >
        IBM MQ Operation Tool
        {me.data && (
          <span style={{ fontWeight: 400, opacity: 0.7, fontSize: 14 }}>{me.data.user}</span>
        )}
      </div>
      <Toolbar
        topology={topology.data}
        queueManager={queueManager}
        channel={channel}
        queue={queue}
        onQueueManagerChange={handleQueueManagerChange}
        onChannelChange={setChannel}
        onQueueChange={setQueue}
        onBrowse={() => browse.mutate({ queueManager, channel, queue })}
        onPut={() => setShowPut(true)}
        onClean={() => setShowClean(true)}
        onExport={handleExport}
        onBulkDelete={() => setShowBulkDelete(true)}
        browsing={browse.isPending}
        exporting={exporting}
      />
      <div style={{ flex: 1, overflow: 'auto' }}>
        <MessageTable rows={rows} onDelete={handleDelete} deleting={deletingId} />
      </div>
      <StatusBar messageCount={browseCount} error={error} />
      {showPut && (
        <PutModal
          queueManager={queueManager}
          channel={channel}
          queue={queue}
          onClose={() => setShowPut(false)}
          onError={setError}
        />
      )}
      {showClean && (
        <CleanModal
          queueManager={queueManager}
          channel={channel}
          queue={queue}
          onClose={() => setShowClean(false)}
          onError={setError}
        />
      )}
      {showBulkDelete && (
        <BulkDeleteModal
          queueManager={queueManager}
          channel={channel}
          queue={queue}
          onClose={() => setShowBulkDelete(false)}
          onError={setError}
        />
      )}
      {pendingDelete && (
        <DeleteConfirmModal
          row={pendingDelete}
          queue={queue}
          onConfirm={handleConfirmDelete}
          onCancel={() => setPendingDelete(null)}
          deleting={del.isPending}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 2: Run the full test suite**

```bash
cd frontend && npm test
```

Expected: all tests pass (Toolbar, MessageTable, DeleteConfirmModal).

- [ ] **Step 3: TypeScript check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/App.tsx
git commit -m "feat: wire delete confirm modal into App"
```

---

### Task 4: Manual smoke test

- [ ] Start the dev server (requires backend running or mock):

```bash
cd frontend && npm run dev
```

- [ ] Browse a queue, click Delete on any row — confirm the modal appears showing the queue name and message preview text.
- [ ] Click Cancel — confirm the modal closes and no delete fires.
- [ ] Click Delete again, then confirm — verify the button shows "Deleting…" while in flight, modal closes on success, and the table refreshes.
