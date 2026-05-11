import type { Topology } from '../types'

interface ToolbarProps {
  topology: Topology
  queueManager: string
  channel: string
  queue: string
  onQueueManagerChange: (v: string) => void
  onChannelChange: (v: string) => void
  onQueueChange: (v: string) => void
  onBrowse: () => void
  onPut: () => void
  onClean: () => void
  onExport: () => void
  onBulkDelete: () => void
  browsing: boolean
  exporting: boolean
}

export function Toolbar({
  topology,
  queueManager,
  channel,
  queue,
  onQueueManagerChange,
  onChannelChange,
  onQueueChange,
  onBrowse,
  onPut,
  onClean,
  onExport,
  onBulkDelete,
  browsing,
  exporting,
}: ToolbarProps) {
  const qmKeys = Object.keys(topology.queueManagers)
  const channels = queueManager
    ? (topology.queueManagers[queueManager]?.channels ?? {})
    : {}
  const channelKeys = Object.keys(channels)
  const canAct = Boolean(queueManager && channel && channels[channel] && queue)

  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        padding: '8px 12px',
        background: '#fff',
        borderBottom: '1px solid #ddd',
        alignItems: 'center',
        flexWrap: 'wrap',
      }}
    >
      <select
        value={queueManager}
        onChange={(e) => onQueueManagerChange(e.target.value)}
        aria-label="Queue manager"
      >
        <option value="">Queue Manager…</option>
        {qmKeys.map((k) => (
          <option key={k} value={k}>
            {topology.queueManagers[k].name}
          </option>
        ))}
      </select>
      <select
        value={channel}
        onChange={(e) => onChannelChange(e.target.value)}
        aria-label="Channel"
      >
        <option value="">Channel…</option>
        {channelKeys.map((k) => (
          <option key={k} value={k}>
            {channels[k].name}
          </option>
        ))}
      </select>
      <input
        value={queue}
        onChange={(e) => onQueueChange(e.target.value)}
        placeholder="Queue name…"
        aria-label="Queue name"
        style={{ flex: 1, minWidth: 140 }}
      />
      <button onClick={onBrowse} disabled={!canAct || browsing}>
        {browsing ? 'Browsing…' : 'Browse'}
      </button>
      <button onClick={onPut} disabled={!canAct}>
        Put…
      </button>
      <button onClick={onClean} disabled={!canAct} style={{ color: '#d33f49' }}>
        Clean…
      </button>
      <button onClick={onExport} disabled={!canAct || exporting}>
        {exporting ? 'Exporting…' : 'Export'}
      </button>
      <button onClick={onBulkDelete} disabled={!canAct}>
        Delete by ID…
      </button>
    </div>
  )
}
