import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import MarketP2POfferCard from './MarketP2POfferCard.jsx'

const STATUS_LABELS = {
  PURCHASABLE: 'Disponible para comprar',
  SUSPENDED: 'Suspendida',
  FILLED: 'Completada',
  CANCELLED: 'Cancelada'
}

function offer (overrides = {}) {
  return {
    offer_id: 'offer-1',
    playerId: 'player-1',
    playerName: 'Jugador 1',
    sellerAlias: 'Alias publico',
    mine: false,
    remainingQuantity: 3,
    status: 'PURCHASABLE',
    quote: { unitPrice: 12.5, version: 2 },
    ...overrides
  }
}

describe('MarketP2POfferCard', () => {
  it('muestra jugador, vendedor, cantidad disponible y cotizacion oficial', () => {
    render(<MarketP2POfferCard offer={offer()} onAction={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'Jugador 1' })).toBeInTheDocument()
    expect(screen.getByText('Alias publico')).toBeInTheDocument()
    expect(screen.getByText('3,00')).toBeInTheDocument()
    expect(screen.getByText('12,50')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it.each([
    ['PURCHASABLE', 'Comprar oferta', true],
    ['SUSPENDED', 'Operación no disponible', false],
    ['FILLED', 'Operación no disponible', false],
    ['CANCELLED', 'Operación no disponible', false],
    ['ACTIVE', 'Operación no disponible', false],
    ['COMPLETED', 'Operación no disponible', false]
  ])('representa el estado %s sin habilitar una accion invalida', (status, label, enabled) => {
    render(<MarketP2POfferCard offer={offer({ status })} onAction={vi.fn()} />)

    expect(screen.getByText(STATUS_LABELS[status] || status)).toBeInTheDocument()
    if (enabled) expect(screen.getByRole('button', { name: label })).toBeEnabled()
    else expect(screen.getByRole('button', { name: label })).toBeDisabled()
  })

  it.each(['OPEN', 'PARTIALLY_FILLED', 'PURCHASABLE', 'SUSPENDED'])('permite cancelar una oferta propia en estado %s', async (status) => {
    const onAction = vi.fn()
    render(<MarketP2POfferCard offer={offer({ status, mine: true })} onAction={onAction} />)

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar oferta' }))
    expect(onAction).toHaveBeenCalledWith(expect.objectContaining({ status, mine: true }))
  })

  it.each(['FILLED', 'CANCELLED'])('mantiene deshabilitada la cancelacion de una oferta propia en estado %s', (status) => {
    render(<MarketP2POfferCard offer={offer({ status, mine: true })} onAction={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Cancelar oferta' })).toBeDisabled()
  })

  it('diferencia una oferta propia y conserva datos opcionales ausentes', () => {
    render(<MarketP2POfferCard offer={offer({ mine: true, sellerAlias: null, quote: null, remainingQuantity: null })} onAction={vi.fn()} />)

    expect(screen.getByText('Tu oferta')).toBeInTheDocument()
    expect(screen.getAllByText('No disponible')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Cancelar oferta' })).toBeEnabled()
    expect(screen.queryByText(/correo|email|saldo/i)).not.toBeInTheDocument()
  })
})
