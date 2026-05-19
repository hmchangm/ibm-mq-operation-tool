# Put Refresh + Table Trim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-refresh the message list after a successful put, and trim the message table to show only Message ID and Preview columns.

**Architecture:** Two isolated frontend changes — `PutModal` gets an `onSuccess` prop split from `onClose`, and `MessageTable` drops the Correlation ID and Type columns. `App.tsx` wires `onSuccess` to close the modal and fire `browse.mutate`.

**Tech Stack:** React, TypeScript, Vitest, @testing-library/react, @tanstack/react-query

---

### Task 1: Trim MessageTable to Message ID + Preview

**Files:**
- Modify: `frontend/src/components/MessageTable.tsx`
- Modify: `frontend/src/components/MessageTable.test.tsx`

- [ ] **Step 1: Write a failing test asserting the removed columns are absent**

Add to `frontend/src/components/MessageTable.test.tsx` inside the `describe('MessageTable', ...)` block:

```tsx
it('does not show Correlation ID or Type columns', () => {
  render(<MessageTable rows={[row]} onDelete={vi.fn()} deleting={null} />)
  expect(screen.queryByText('Correlation ID')).not.toBeInTheDocument()
  expect(screen.queryByText('Type')).not.toBeInTheDocument()
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd frontend && npx vitest run src/components/MessageTable.test.tsx
```

Expected: FAIL — `Correlation ID` is found in the document.

- [ ] **Step 3: Remove Correlation ID and Type columns from MessageTable**

Replace `frontend/src/components/MessageTable.tsx` with:

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

- [ ] **Step 4: Run tests to verify all pass**

```bash
cd frontend && npx vitest run src/components/MessageTable.test.tsx
```

Expected: all 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/MessageTable.tsx frontend/src/components/MessageTable.test.tsx
git commit -m "feat: trim message table to msgid and preview columns"
```

---

### Task 2: Refresh list after successful put

**Files:**
- Modify: `frontend/src/components/PutModal.tsx`
- Create: `frontend/src/components/PutModal.test.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Write failing tests for PutModal's split onSuccess / onClose**

Create `frontend/src/components/PutModal.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PutModal } from './PutModal'
import { usePut } from '../api/usePut'

vi.mock('../api/usePut')

const mockUsePut = vi.mocked(usePut)

function renderPutModal(overrides: { onSuccess?: () => void; onClose?: () => void } = {}) {
  const onSuccess = overrides.onSuccess ?? vi.fn()
  const onClose = overrides.onClose ?? vi.fn()
  const onError = vi.fn()
  mockUsePut.mockReturnValue({ mutate: vi.fn(), isPending: false } as any)
  render(
    <PutModal
      queueManager="qm1"
      channel="ch1"
      queue="Q1"
      onSuccess={onSuccess}
      onClose={onClose}
      onError={onError}
    />
  )
  return { onSuccess, onClose, onError }
}

describe('PutModal', () => {
  it('calls onClose when Cancel is clicked, not onSuccess', async () => {
    const user = userEvent.setup()
    const { onClose, onSuccess } = renderPutModal()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onClose).toHaveBeenCalledOnce()
    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('passes onSuccess to usePut, not onClose', () => {
    const onSuccess = vi.fn()
    const onClose = vi.fn()
    let capturedSuccess: (() => void) | undefined
    mockUsePut.mockImplementation((successCb) => {
      capturedSuccess = successCb
      return { mutate: vi.fn(), isPending: false } as any
    })
    render(
      <PutModal
        queueManager="qm1"
        channel="ch1"
        queue="Q1"
        onSuccess={onSuccess}
        onClose={onClose}
        onError={vi.fn()}
      />
    )
    capturedSuccess!()
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(onClose).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npx vitest run src/components/PutModal.test.tsx
```

Expected: FAIL — `PutModal` has no `onSuccess` prop yet.

- [ ] **Step 3: Update PutModal to accept onSuccess separate from onClose**

Replace `frontend/src/components/PutModal.tsx` with:

```tsx
import { useEffect, useState } from 'react'
import { usePut } from '../api/usePut'

interface PutModalProps {
  queueManager: string
  channel: string
  queue: string
  onSuccess: () => void
  onClose: () => void
  onError: (msg: string) => void
}

export function PutModal({ queueManager, channel, queue, onSuccess, onClose, onError }: PutModalProps) {
  const [body, setBody] = useState('')
  const put = usePut(onSuccess, onError)
  const titleId = 'put-modal-title'

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 100,
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        style={{
          background: '#fff',
          borderRadius: 8,
          padding: 24,
          width: 480,
          boxShadow: '0 4px 24px rgba(0,0,0,0.2)',
        }}
      >
        <h3 id={titleId} style={{ margin: '0 0 12px' }}>
          Put Message — {queue}
        </h3>
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={8}
          aria-label="Message body"
          style={{
            width: '100%',
            fontFamily: 'monospace',
            fontSize: 13,
            boxSizing: 'border-box',
            padding: 8,
            border: '1px solid #ccc',
            borderRadius: 4,
            resize: 'vertical',
          }}
          placeholder="Message body…"
          autoFocus
        />
        <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'flex-end' }}>
          <button onClick={onClose}>Cancel</button>
          <button
            onClick={() => put.mutate({ queueManager, channel, queue, body })}
            disabled={!body.trim() || put.isPending}
            style={{
              background: '#255f85',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              padding: '6px 16px',
            }}
          >
            {put.isPending ? 'Putting…' : 'Put'}
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run PutModal tests to verify they pass**

```bash
cd frontend && npx vitest run src/components/PutModal.test.tsx
```

Expected: all 2 tests PASS.

- [ ] **Step 5: Wire onSuccess in App.tsx**

In `frontend/src/App.tsx`, replace the `PutModal` usage:

```tsx
{showPut && (
  <PutModal
    queueManager={queueManager}
    channel={channel}
    queue={queue}
    onSuccess={() => {
      setShowPut(false)
      browse.mutate({ queueManager, channel, queue })
    }}
    onClose={() => setShowPut(false)}
    onError={setError}
  />
)}
```

- [ ] **Step 6: Run all frontend tests**

```bash
cd frontend && npx vitest run
```

Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/PutModal.tsx frontend/src/components/PutModal.test.tsx frontend/src/App.tsx
git commit -m "feat: refresh list after successful put"
```
