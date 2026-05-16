import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DeleteConfirmModal } from './DeleteConfirmModal'
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

describe('DeleteConfirmModal', () => {
  it('shows queue name and message preview', () => {
    render(
      <DeleteConfirmModal
        row={row}
        queue="DEV.QUEUE.1"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        deleting={false}
      />
    )
    expect(screen.getByText(/DEV\.QUEUE\.1/)).toBeInTheDocument()
    expect(screen.getByText(/Hello world/)).toBeInTheDocument()
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    render(
      <DeleteConfirmModal
        row={row}
        queue="DEV.QUEUE.1"
        onConfirm={vi.fn()}
        onCancel={onCancel}
        deleting={false}
      />
    )
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('calls onConfirm when Delete is clicked', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(
      <DeleteConfirmModal
        row={row}
        queue="DEV.QUEUE.1"
        onConfirm={onConfirm}
        onCancel={vi.fn()}
        deleting={false}
      />
    )
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('disables Delete button and shows "Deleting…" when deleting is true', () => {
    render(
      <DeleteConfirmModal
        row={row}
        queue="DEV.QUEUE.1"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        deleting={true}
      />
    )
    expect(screen.getByRole('button', { name: 'Deleting…' })).toBeDisabled()
  })
})
