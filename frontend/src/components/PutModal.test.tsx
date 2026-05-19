import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PutModal } from './PutModal'
import { usePut } from '../api/usePut'

vi.mock('../api/usePut')

const mockUsePut = vi.mocked(usePut)

function renderPutModal(overrides: { onSuccess?: () => void; onClose?: () => void } = {}) {
  const onSuccess = overrides.onSuccess ?? vi.fn()
  const onClose = overrides.onClose ?? vi.fn()
  const onError = vi.fn()
  mockUsePut.mockReturnValue({ mutate: vi.fn(), isPending: false } as any)
  render(
    <PutModal
      queueManager="qm1"
      channel="ch1"
      queue="Q1"
      onSuccess={onSuccess}
      onClose={onClose}
      onError={onError}
    />
  )
  return { onSuccess, onClose, onError }
}

describe('PutModal', () => {
  it('calls onClose when Cancel is clicked, not onSuccess', async () => {
    const user = userEvent.setup()
    const { onClose, onSuccess } = renderPutModal()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onClose).toHaveBeenCalledOnce()
    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('passes onSuccess to usePut, not onClose', () => {
    const onSuccess = vi.fn()
    const onClose = vi.fn()
    let capturedSuccess: (() => void) | undefined
    mockUsePut.mockImplementation((successCb) => {
      capturedSuccess = successCb
      return { mutate: vi.fn(), isPending: false } as any
    })
    render(
      <PutModal
        queueManager="qm1"
        channel="ch1"
        queue="Q1"
        onSuccess={onSuccess}
        onClose={onClose}
        onError={vi.fn()}
      />
    )
    capturedSuccess!()
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(onClose).not.toHaveBeenCalled()
  })
})
