interface ExportParams {
  queueManager: string
  channel: string
  queue: string
}

export async function exportQueue(params: ExportParams): Promise<void> {
  const res = await fetch('/api/export', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
  if (!res.ok) throw new Error(await res.text())
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'export.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}
