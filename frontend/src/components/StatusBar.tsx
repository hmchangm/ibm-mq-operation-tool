import { useEffect, useState } from 'react'

interface StatusBarProps {
  messageCount: number | null
  error: string | null
}

export function StatusBar({ messageCount, error }: StatusBarProps) {
  const [visibleError, setVisibleError] = useState<string | null>(null)

  useEffect(() => {
    if (!error) return
    setVisibleError(error)
    const timer = setTimeout(() => setVisibleError(null), 4000)
    return () => clearTimeout(timer)
  }, [error])

  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        padding: '4px 12px',
        borderTop: '1px solid #eee',
        background: '#fff',
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        fontSize: 13,
        color: '#666',
        minHeight: 28,
      }}
    >
      {messageCount !== null && (
        <span>
          {messageCount} message{messageCount !== 1 ? 's' : ''}
        </span>
      )}
      {visibleError && (
        <span role="alert" style={{ color: '#d33f49', fontWeight: 500 }}>
          {visibleError}
        </span>
      )}
    </div>
  )
}
