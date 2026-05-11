import { useState } from 'react'
import type { CSSProperties } from 'react'
import { useBulkDelete } from '../api/useBulkDelete'

interface BulkDeleteModalProps {
  queueManager: string
  channel: string
  queue: string
  onClose: () => void
  onError: (msg: string) => void
}

type Phase =
  | { name: 'input' }
  | { name: 'confirm'; ids: string[] }
  | { name: 'result'; deleted: number; alreadyGone: number }

function parseIds(raw: string): string[] {
  return raw
    .trim()
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
}

export function BulkDeleteModal({
  queueManager,
  channel,
  queue,
  onClose,
  onError,
}: BulkDeleteModalProps) {
  const [text, setText] = useState('')
  const [phase, setPhase] = useState<Phase>({ name: 'input' })

  const bulkDelete = useBulkDelete(
    (result) =>
      setPhase({ name: 'result', deleted: result.deleted, alreadyGone: result.alreadyGone }),
    (msg) => {
      onError(msg)
      onClose()
    }
  )

  if (phase.name === 'input') {
    const ids = parseIds(text)
    return (
      <div style={overlayStyle}>
        <div style={modalStyle}>
          <h3 style={{ margin: '0 0 12px' }}>Delete by ID</h3>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Paste JMS Message IDs, one per line…"
            rows={10}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 12, boxSizing: 'border-box' }}
          />
          <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'flex-end' }}>
            <button onClick={onClose}>Cancel</button>
            <button
              onClick={() => setPhase({ name: 'confirm', ids })}
              disabled={ids.length === 0}
              style={{ color: '#d33f49' }}
            >
              Review ({ids.length})
            </button>
          </div>
        </div>
      </div>
    )
  }

  if (phase.name === 'confirm') {
    const { ids } = phase
    return (
      <div style={overlayStyle}>
        <div style={modalStyle}>
          <h3 style={{ margin: '0 0 12px' }}>Confirm Deletion</h3>
          <p style={{ margin: '0 0 12px' }}>
            About to delete <strong>{ids.length}</strong> message
            {ids.length !== 1 ? 's' : ''} from <code>{queue}</code>. Proceed?
          </p>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button onClick={() => setPhase({ name: 'input' })}>Back</button>
            <button
              onClick={() =>
                bulkDelete.mutate({ queueManager, channel, queue, jmsMessageIds: ids })
              }
              disabled={bulkDelete.isPending}
              style={{ color: '#d33f49' }}
            >
              {bulkDelete.isPending ? 'Deleting…' : `Delete ${ids.length}`}
            </button>
          </div>
        </div>
      </div>
    )
  }

  const { deleted, alreadyGone } = phase
  const total = deleted + alreadyGone
  const summary =
    alreadyGone > 0
      ? `Deleted ${deleted} of ${total}. ${alreadyGone} were already gone.`
      : `Deleted ${deleted} of ${total}.`

  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <h3 style={{ margin: '0 0 12px' }}>Done</h3>
        <p style={{ margin: '0 0 12px' }}>{summary}</p>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button onClick={onClose}>Close</button>
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
