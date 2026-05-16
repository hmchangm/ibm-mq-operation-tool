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
