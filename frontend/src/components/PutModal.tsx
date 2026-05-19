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
