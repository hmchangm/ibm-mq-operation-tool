import { useEffect, useState } from 'react'
import { exportQueue } from './api/exportQueue'
import { BulkDeleteModal } from './components/BulkDeleteModal'
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
      browse.mutate({ queueManager, channel, queue })
    },
    (msg) => {
      setDeletingId(null)
      setError(msg)
    }
  )

  const handleDelete = (jmsMessageId: string) => {
    setDeletingId(jmsMessageId)
    del.mutate({ queueManager, channel, queue, jmsMessageId })
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
    </div>
  )
}
