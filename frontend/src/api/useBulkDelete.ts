import { useMutation } from '@tanstack/react-query'

interface BulkDeleteParams {
  queueManager: string
  channel: string
  queue: string
  jmsMessageIds: string[]
}

interface BulkDeleteResult {
  deleted: number
  alreadyGone: number
}

export function useBulkDelete(
  onSuccess: (result: BulkDeleteResult) => void,
  onError: (msg: string) => void
) {
  return useMutation<BulkDeleteResult, Error, BulkDeleteParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/bulk-delete', {
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
