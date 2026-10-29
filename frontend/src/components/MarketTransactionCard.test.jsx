import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MarketTransactionCard from './MarketTransactionCard.jsx'

const transaction = {
  player_id: 'ply-1',
  player_name_snapshot: 'Jugador de prueba',
  confirmed_at: '2026-10-29T12:00:00Z',
  quantity: 2,
  unit_price: 10,
  total_amount: 20,
  quote_version: 3,
  realized_pnl: 1.5
}

describe('MarketTransactionCard', () => {
  it('aplica la variante de compra y renderiza los datos principales', () => {
    render(
      <MarketTransactionCard
        transaction={{ ...transaction, operation_type: 'PURCHASE' }}
      />
    )

    const card = screen.getByRole('article')
    expect(card).toHaveClass('market-transaction-card--purchase')
    expect(card).toHaveTextContent('Compra')
    expect(card).toHaveTextContent('Jugador de prueba')
    expect(card).toHaveTextContent('2')
    expect(card).toHaveTextContent('20,00')
  })

  it('aplica la variante de venta', () => {
    render(
      <MarketTransactionCard
        transaction={{ ...transaction, operation_type: 'SALE' }}
      />
    )

    expect(screen.getByRole('article')).toHaveClass('market-transaction-card--sale')
    expect(screen.getByRole('article')).toHaveTextContent('Venta')
  })
})
