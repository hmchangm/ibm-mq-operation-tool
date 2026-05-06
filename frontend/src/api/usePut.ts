import { useMutation } from '@tanstack/react-query'

interface PutParams {
  queueManager: string
  channel: string
  queue: string
  body: string
}

export function usePut(onSuccess: () => void, onError: (msg: string) => void) {
  return useMutation<void, Error, PutParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/put', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      })
      if (!res.ok) throw new Error(await res.text())
    },
    onSuccess,
    onError: (e) => onError(e.message),
  })
}
