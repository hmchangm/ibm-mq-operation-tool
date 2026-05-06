export interface MessageRow {
  jmsMessageId: string
  correlationId: string | null
  timestamp: number | null
  expiration: number | null
  priority: number | null
  type: string
  preview: string
}

export interface ChannelTopology {
  name: string
}

export interface QueueManagerTopology {
  name: string
  channels: Record<string, ChannelTopology>
}

export interface Topology {
  queueManagers: Record<string, QueueManagerTopology>
}

export interface MeResponse {
  user: string
}
