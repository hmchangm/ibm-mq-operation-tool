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
