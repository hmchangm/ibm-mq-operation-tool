import { useEffect, useState } from 'react'
import { useClean } from '../api/useClean'

interface CleanModalProps {
  queueManager: string
  channel: string
  queue: string
  onClose: () => void
  onError: (msg: string) => void
}

export function CleanModal({ queueManager, channel, queue, onClose, onError }: CleanModalProps) {
  const [confirm, setConfirm] = useState('')
  const [removed, setRemoved] = useState<number | null>(null)
  const clean = useClean((result) => setRemoved(result.removed), onError)
  const titleId = removed === null ? 'clean-modal-title' : 'clean-complete-modal-title'

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  if (removed !== null) {
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
            width: 400,
            boxShadow: '0 4px 24px rgba(0,0,0,0.2)',
          }}
        >
          <h3 id={titleId} style={{ margin: '0 0 12px' }}>
            Clean complete
          </h3>
          <p>
            Removed {removed} message{removed !== 1 ? 's' : ''} from <strong>{queue}</strong>.
          </p>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
            <button onClick={onClose}>Close</button>
          </div>
        </div>
      </div>
    )
  }

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
          width: 400,
          boxShadow: '0 4px 24px rgba(0,0,0,0.2)',
        }}
      >
        <h3 id={titleId} style={{ margin: '0 0 8px', color: '#d33f49' }}>
          Clean Queue
        </h3>
        <p style={{ margin: '0 0 12px', color: '#555' }}>
          This will remove all messages from <strong>{queue}</strong>. Type the queue name to
          confirm.
        </p>
        <input
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          placeholder={queue}
          aria-label="Confirm queue name"
          style={{
            width: '100%',
            padding: '6px 8px',
            border: '1px solid #f5c0c0',
            borderRadius: 4,
            boxSizing: 'border-box',
          }}
          autoFocus
        />
        <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'flex-end' }}>
          <button onClick={onClose}>Cancel</button>
          <button
            onClick={() => clean.mutate({ queueManager, channel, queue })}
            disabled={confirm !== queue || clean.isPending}
            style={{
              background: '#d33f49',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              padding: '6px 16px',
            }}
          >
            {clean.isPending ? 'Cleaning…' : 'Clean'}
          </button>
        </div>
      </div>
    </div>
  )
}
