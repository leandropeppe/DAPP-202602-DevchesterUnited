import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import MarketP2PConfirmationModal from './MarketP2PConfirmationModal.jsx'

const quote = { quoteId: 'quote-1', quoteVersion: 3, unitPrice: 12.5 }

function props (overrides = {}) {
  return {
    open: true,
    mode: 'purchase',
    subject: 'Jugador 1',
    quote,
    quantity: '2',
    maxQuantity: 5,
    state: 'idle',
    message: '',
    onQuantityChange: vi.fn(),
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
    ...overrides
  }
}

describe('MarketP2PConfirmationModal', () => {
  it('no renderiza cuando esta cerrado y abre el resumen de compra', () => {
    const { rerender } = render(<MarketP2PConfirmationModal {...props()} open={false} />)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    rerender(<MarketP2PConfirmationModal {...props()} />)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Jugador 1')).toBeInTheDocument()
    expect(screen.getByText('12,50')).toBeInTheDocument()
    expect(screen.getByText('25,00 creditos')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton')).toHaveValue(2)
  })

  it('confirma, cancela y propaga el cambio de cantidad', async () => {
    const user = userEvent.setup()
    const callbacks = { onQuantityChange: vi.fn(), onConfirm: vi.fn(), onCancel: vi.fn() }
    render(<MarketP2PConfirmationModal {...props(callbacks)} />)

    await user.clear(screen.getByRole('spinbutton'))
    await user.type(screen.getByRole('spinbutton'), '4')
    await user.click(screen.getByRole('button', { name: 'Comprar tokens' }))
    await user.click(screen.getByRole('button', { name: 'Volver' }))

    expect(callbacks.onQuantityChange).toHaveBeenCalled()
    expect(callbacks.onConfirm).toHaveBeenCalledTimes(1)
    expect(callbacks.onCancel).toHaveBeenCalledTimes(1)
  })

  it('valida cantidad, cotizacion disponible y previene doble envio durante carga', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    const { rerender } = render(<MarketP2PConfirmationModal {...props({ quantity: '6', onConfirm })} />)
    expect(screen.getByRole('button', { name: 'Comprar tokens' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Comprar tokens' }))
    expect(onConfirm).not.toHaveBeenCalled()

    rerender(<MarketP2PConfirmationModal {...props({ state: 'loading', onConfirm })} />)
    expect(screen.getByRole('button', { name: 'Enviando...' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Volver' })).toBeDisabled()
  })

  it('mantiene la compra bloqueada sin cotizacion y muestra errores seguros', () => {
    render(<MarketP2PConfirmationModal {...props({ quote: null, message: 'La oferta esta suspendida.' })} />)

    expect(screen.getByText('La oferta esta suspendida.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Comprar tokens' })).toBeDisabled()
    expect(screen.queryByText(/email|correo|token secreto|authorization/i)).not.toBeInTheDocument()
  })

  it.each([
    ['publish', 'Confirmar publicación', 'Publicar oferta'],
    ['cancel', 'Confirmar cancelación', 'Cancelar oferta']
  ])('muestra el flujo de %s sin precio manual', (mode, title, action) => {
    render(<MarketP2PConfirmationModal {...props({ mode, subject: 'Jugador 1' })} />)

    expect(screen.getByRole('heading', { name: title })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: action })).toBeEnabled()
    expect(screen.queryByLabelText(/precio/i)).not.toBeInTheDocument()
  })
})
