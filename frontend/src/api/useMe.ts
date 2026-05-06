import { useQuery } from '@tanstack/react-query'
import type { MeResponse } from '../types'

export function useMe() {
  return useQuery<MeResponse, Error>({
    queryKey: ['me'],
    queryFn: async () => {
      const res = await fetch('/api/me')
      if (res.status === 401) throw new Error('unauthenticated')
      if (!res.ok) throw new Error('Failed to fetch user')
      return res.json()
    },
    retry: false,
  })
}
