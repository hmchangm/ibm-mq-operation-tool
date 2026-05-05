import { useQuery } from '@tanstack/react-query'
import type { Topology } from '../types'

export function useTopology() {
  return useQuery<Topology, Error>({
    queryKey: ['topology'],
    queryFn: async () => {
      const res = await fetch('/api/topology')
      if (!res.ok) throw new Error('Failed to fetch topology')
      return res.json()
    },
  })
}
