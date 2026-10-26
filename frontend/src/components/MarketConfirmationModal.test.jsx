import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import MarketConfirmationModal from './MarketConfirmationModal.jsx'

const defaultProps = {
  open: true,
  title: 'Confirmar compra',
  message: <>Comprar <strong>2 tokens</strong> de Jugador</>,
  pricePerToken: 10,
  quantity: 2,
  total: 20,
  confirmLabel: 'Confirmar compra',
  onConfirm: vi.fn(),
  onCancel: vi.fn(),
  variant: 'buy'
}

describe('MarketConfirmationModal', () => {
  it('no renderiza cuando está cerrado', () => {
    render(<MarketConfirmationModal {...defaultProps} open={false} />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('renderiza título, mensaje, cantidad y total', () => {
    render(<MarketConfirmationModal {...defaultProps} />)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Confirmar compra' })).toBeInTheDocument()
    expect(screen.getByText(/Comprar/)).toBeInTheDocument()
    expect(screen.getByText('10,00')).toBeInTheDocument()
    expect(screen.getByText('2 tokens')).toBeInTheDocument()
    expect(screen.getByText('20,00 créditos')).toBeInTheDocument()
  })

  it('cancela desde el botón o el overlay, pero no desde el diálogo', () => {
    const onCancel = vi.fn()
    render(<MarketConfirmationModal {...defaultProps} onCancel={onCancel} />)
    const dialog = screen.getByRole('dialog')

    fireEvent.click(dialog)
    expect(onCancel).not.toHaveBeenCalled()

    fireEvent.click(dialog.parentElement)
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCancel).toHaveBeenCalledTimes(2)
  })

  it('confirma únicamente al presionar el botón de confirmación', () => {
    const onConfirm = vi.fn()
    render(<MarketConfirmationModal {...defaultProps} onConfirm={onConfirm} />)

    expect(onConfirm).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })
})
