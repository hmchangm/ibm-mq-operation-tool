import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MessageTable } from './MessageTable'
import type { MessageRow } from '../types'

const row: MessageRow = {
  jmsMessageId: 'ID:abc123',
  correlationId: null,
  timestamp: null,
  expiration: null,
  priority: null,
  type: 'TextMessage',
  preview: 'Hello world',
}

describe('MessageTable', () => {
  it('shows empty state when there are no rows', () => {
    render(<MessageTable rows={[]} onDelete={vi.fn()} deleting={null} />)
    expect(screen.getByText(/No messages/)).toBeInTheDocument()
  })

  it('calls onDelete with the full MessageRow when Delete is clicked', async () => {
    const user = userEvent.setup()
    const onDelete = vi.fn()
    render(<MessageTable rows={[row]} onDelete={onDelete} deleting={null} />)
    await user.click(screen.getByRole('button', { name: /Delete message/ }))
    expect(onDelete).toHaveBeenCalledWith(row)
  })

  it('disables the button and updates aria-label when that row is deleting', () => {
    render(<MessageTable rows={[row]} onDelete={vi.fn()} deleting="ID:abc123" />)
    expect(screen.getByRole('button', { name: /Deleting message/ })).toBeDisabled()
  })
})
