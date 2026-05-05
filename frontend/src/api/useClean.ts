import { useMutation } from '@tanstack/react-query'

interface CleanParams {
  queueManager: string
  channel: string
  queue: string
}

interface CleanResult {
  removed: number
}

export function useClean(
  onSuccess: (result: CleanResult) => void,
  onError: (msg: string) => void
) {
  return useMutation<CleanResult, Error, CleanParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/clean', {
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
