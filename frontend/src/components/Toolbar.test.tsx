import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Toolbar } from './Toolbar'
import type { Topology } from '../types'

const topology: Topology = {
  queueManagers: {
    qm1: {
      name: 'QM1',
      channels: {
        ops: { name: 'OPS.SVRCONN' },
      },
    },
  },
}

function renderToolbar(overrides: Partial<ComponentProps<typeof Toolbar>> = {}) {
  const props = renderToolbarProps(overrides)
  render(<Toolbar {...props} />)
  return props
}

describe('Toolbar', () => {
  it('keeps queue actions disabled until a queue manager, channel, and queue name are selected', () => {
    const { rerender } = render(<Toolbar {...renderToolbarProps()} />)

    expect(screen.getByRole('button', { name: 'Browse' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Put…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Clean…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Export' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete by ID…' })).toBeDisabled()

    rerender(
      <Toolbar {...renderToolbarProps({ queueManager: 'qm1', channel: 'ops', queue: 'DEV.QUEUE.1' })} />
    )

    expect(screen.getByRole('button', { name: 'Browse' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Put…' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Clean…' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Export' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Delete by ID…' })).toBeEnabled()
  })

  it('notifies when the queue manager selection changes', async () => {
    const user = userEvent.setup()
    const props = renderToolbar()

    await user.selectOptions(screen.getByLabelText('Queue manager'), 'qm1')

    expect(props.onQueueManagerChange).toHaveBeenCalledWith('qm1')
  })
})

function renderToolbarProps(overrides: Partial<ComponentProps<typeof Toolbar>> = {}) {
  return {
    topology,
    queueManager: '',
    channel: '',
    queue: '',
    onQueueManagerChange: vi.fn(),
    onChannelChange: vi.fn(),
    onQueueChange: vi.fn(),
    onBrowse: vi.fn(),
    onPut: vi.fn(),
    onClean: vi.fn(),
    onExport: vi.fn(),
    onBulkDelete: vi.fn(),
    browsing: false,
    exporting: false,
    ...overrides,
  }
}
