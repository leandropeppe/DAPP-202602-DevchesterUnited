import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import MarketP2POfferCard from './MarketP2POfferCard.jsx'

describe('MarketP2POfferCard nombre de jugador', () => {
  it('muestra el nombre y no el UUID como etiqueta principal', () => {
    render(<MarketP2POfferCard
      offer={{
        playerId: 'd6fab6ad-b380-4124-b3fa-c0622cc0ba63',
        playerName: 'Federico Valverde',
        status: 'SUSPENDED',
        mine: false,
        remainingQuantity: 1,
        quote: null
      }}
      onAction={vi.fn()}
    />)

    expect(screen.getByRole('heading', { name: 'Federico Valverde' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /d6fab6ad-b380-4124-b3fa-c0622cc0ba63/ })).not.toBeInTheDocument()
  })
})
