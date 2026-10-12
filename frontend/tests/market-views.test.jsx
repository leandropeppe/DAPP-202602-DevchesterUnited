import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MarketStateMessage from '../src/components/MarketStateMessage.jsx'
import OperationStatus from '../src/components/OperationStatus.jsx'
import QuoteStatus from '../src/components/QuoteStatus.jsx'

describe('componentes de estado del mercado', () => {
  it('muestra el estado de carga como información accesible', () => {
    render(<MarketStateMessage status="loading" />)

    expect(screen.getByRole('status')).toHaveTextContent('Cargando información del mercado')
  })

  it('muestra errores y mercado no disponible como alertas', () => {
    const { rerender } = render(<MarketStateMessage status="error" />)

    expect(screen.getByRole('alert')).toHaveTextContent('No fue posible cargar')

    rerender(<MarketStateMessage status="unavailable" />)

    expect(screen.getByRole('alert')).toHaveTextContent('no está disponible temporalmente')
  })

  it('muestra una cotización disponible sin calcular el precio recibido', () => {
    render(<QuoteStatus status="available" price="1.00" />)

    expect(screen.getByRole('status')).toHaveTextContent('Cotización disponible')
    expect(screen.getByRole('status')).toHaveTextContent('Precio: 1.00')
  })

  it.each([
    ['unavailable', 'Cotización no disponible'],
    ['historical', 'Cotización histórica'],
    ['replaced', 'Cotización reemplazada'],
    ['invalidated', 'Cotización invalidada'],
    ['expired', 'Cotización expirada']
  ])('comunica que la cotización %s no es operable', (status, message) => {
    render(<QuoteStatus status={status} />)

    expect(screen.getByRole('status')).toHaveTextContent(message)
    expect(screen.getByRole('status')).toHaveTextContent('no está disponible para operar')
  })

  it.each([
    'unavailable',
    'historical',
    'replaced',
    'invalidated',
    'expired'
  ])('conserva la advertencia obligatoria con mensaje personalizado para %s', (status) => {
    render(<QuoteStatus status={status} message="Cotización del jugador" />)

    const statusMessage = screen.getByRole('status')
    expect(statusMessage).toHaveTextContent('Cotización del jugador')
    expect(statusMessage).toHaveTextContent('no está disponible para operar')
  })

  it('conserva los mensajes obligatorios para un estado desconocido personalizado', () => {
    render(<QuoteStatus status="future-status" message="Cotización del jugador" />)

    const statusMessage = screen.getByRole('status')
    expect(statusMessage).toHaveTextContent('Cotización del jugador')
    expect(statusMessage).toHaveTextContent('no se puede determinar')
    expect(statusMessage).toHaveTextContent('no está disponible para operar')
  })

  it('conserva el mensaje personalizado y el precio para una cotización disponible', () => {
    render(<QuoteStatus status="available" message="Cotización del jugador" price="1.00" />)

    const statusMessage = screen.getByRole('status')
    expect(statusMessage).toHaveTextContent('Cotización del jugador')
    expect(statusMessage).toHaveTextContent('Precio: 1.00')
    expect(statusMessage).not.toHaveTextContent('no está disponible para operar')
  })

  it('no muestra el precio cuando la cotización no es operable', () => {
    render(<QuoteStatus status="expired" price="999.99" />)

    expect(screen.getByRole('status')).not.toHaveTextContent('Precio: 999.99')
  })

  it.each([
    'unavailable',
    'historical',
    'replaced',
    'invalidated',
    'expired'
  ])('no duplica la advertencia predeterminada para %s', (status) => {
    render(<QuoteStatus status={status} />)

    const message = screen.getByRole('status').textContent
    expect(message.match(/no está disponible para operar/g)).toHaveLength(1)
  })

  it('trata un estado de cotización desconocido como no operable', () => {
    render(<QuoteStatus status="future-status" />)

    expect(screen.getByRole('status')).toHaveTextContent('no se puede determinar')
    expect(screen.getByRole('status')).not.toHaveClass('status-message--success')
  })

  it.each([
    ['pending', 'pendiente de confirmación', 'status'],
    ['confirmed', 'fue confirmada', 'status'],
    ['rejected', 'fue rechazada', 'alert'],
    ['uncertain', 'resultado de la operación es incierto', 'alert']
  ])('muestra la operación %s con una semántica clara', (status, message, role) => {
    render(<OperationStatus status={status} />)

    expect(screen.getByRole(role)).toHaveTextContent(message)
  })

  it('trata una operación desconocida como incierta y no confirmada', () => {
    render(<OperationStatus status="future-status" />)

    expect(screen.getByRole('alert')).toHaveTextContent('no se puede determinar')
    expect(screen.getByRole('alert')).not.toHaveTextContent('confirmada')
  })
})
