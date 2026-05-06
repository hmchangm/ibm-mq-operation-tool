import { useMutation } from '@tanstack/react-query'
import type { MessageRow } from '../types'

interface BrowseParams {
  queueManager: string
  channel: string
  queue: string
}

export function useBrowse(
  onSuccess: (rows: MessageRow[]) => void,
  onError: (msg: string) => void
) {
  return useMutation<MessageRow[], Error, BrowseParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/browse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      })
      if (!res.ok) throw new Error(await res.text())
      return res.json()
    },
    onSuccess,
    onError: (e) => onError(e.message),
  })
}
