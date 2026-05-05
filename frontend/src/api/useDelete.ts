import { useMutation } from '@tanstack/react-query'

interface DeleteParams {
  queueManager: string
  channel: string
  queue: string
  jmsMessageId: string
}

export function useDelete(onSuccess: () => void, onError: (msg: string) => void) {
  return useMutation<void, Error, DeleteParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      })
      if (!res.ok)
        throw new Error(res.status === 410 ? 'Message already gone' : await res.text())
    },
    onSuccess,
    onError: (e) => onError(e.message),
  })
}
