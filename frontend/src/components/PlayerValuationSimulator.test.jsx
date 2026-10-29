import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PlayerValuationSimulator from './PlayerValuationSimulator.jsx'
import * as http from '../api/http.js'

const config = {
  playerId: 'ply-1', resolvedPosition: 'Forward', strategyId: 'POSITIONAL', strategyVersion: 2,
  horizons: [3, 5, 8, 10], currentApps: 10, currentMinutes: 900, currentQuote: 100, blockingReasons: [],
  statistics: [
    { metric: 'GOALS', label: 'Goles', kind: 'ACCUMULATIVE', currentValue: 11, editable: true, min: 0, max: null },
    { metric: 'SHOTS', label: 'Tiros por partido', kind: 'AVERAGE', currentValue: 5.2, editable: true, min: 0, max: null },
    { metric: 'DRIBBLES', label: 'Regates', kind: 'AVERAGE', currentValue: 3.4, editable: false, min: 0, max: null }
  ]
}

const result = {
  currentQuote: 100,
  simulatedQuote: 110,
  score: 0.8,
  monetaryDifference: 10,
  percentageDifference: 10,
  projectedStatistics: { goals: 13 }
}

afterEach(() => vi.restoreAllMocks())

async function renderReady () {
  vi.spyOn(http, 'getValuationSimulatorConfig').mockResolvedValue({ data: config })
  render(<PlayerValuationSimulator playerId="ply-1" onClose={vi.fn()} />)
  await screen.findByRole('dialog')
}

describe('PlayerValuationSimulator', () => {
  it('renderiza solo editables y los datos compactos del escenario', async () => {
    await renderReady()
    expect(screen.getByLabelText('Cantidad de tokens')).toHaveValue(1)
    expect(screen.getByLabelText('Goles predicción')).toHaveValue(0)
    expect(screen.getByLabelText('Tiros por partido valor proyectado')).toHaveValue(5.2)
    expect(screen.queryByLabelText(/Regates/)).not.toBeInTheDocument()
    expect(screen.getByText('Partidos:')).toHaveTextContent('10 → 13')
    expect(screen.getByRole('columnheader', { name: 'Actual' })).toBeInTheDocument()
  })

  it('envía solo horizonte y modificaciones, calcula impacto de tokens y permite volver a editar', async () => {
    const simulate = vi.spyOn(http, 'simulatePlayerValuation').mockResolvedValue({ data: result })
    await renderReady()
    fireEvent.change(screen.getByLabelText('Cantidad de tokens'), { target: { value: '2.5' } })
    fireEvent.change(screen.getByLabelText('Goles predicción'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))

    await waitFor(() => expect(simulate).toHaveBeenCalledWith('ply-1', { horizonMatches: 3, modifications: { GOALS: 2 } }))
    expect(simulate.mock.calls[0][1]).not.toHaveProperty('tokens')
    expect(await screen.findByRole('heading', { name: 'Cotización proyectada' }, { timeout: 3000 })).toBeInTheDocument()
    expect(screen.getByText('250')).toBeInTheDocument()
    expect(screen.getByText('275')).toBeInTheDocument()
    expect(screen.getByText('+25')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Modificar escenario' }))
    expect(screen.getByLabelText('Cantidad de tokens')).toHaveValue(2.5)
    expect(screen.getByLabelText('Goles predicción')).toHaveValue(2)
  })

  it('restablece tokens, escenario y resultado', async () => {
    vi.spyOn(http, 'simulatePlayerValuation').mockResolvedValue({ data: result })
    await renderReady()
    fireEvent.change(screen.getByLabelText('Cantidad de tokens'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('Goles predicción'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    await screen.findByRole('heading', { name: 'Cotización proyectada' }, { timeout: 5000 })
    fireEvent.click(screen.getByRole('button', { name: 'Restablecer' }))
    expect(screen.getByLabelText('Cantidad de tokens')).toHaveValue(1)
    expect(screen.getByLabelText('Goles predicción')).toHaveValue(0)
    expect(screen.queryByRole('heading', { name: 'Cotización proyectada' })).not.toBeInTheDocument()
  })

  it('rechaza tokens no positivos sin llamar al backend', async () => {
    const simulate = vi.spyOn(http, 'simulatePlayerValuation')
    await renderReady()
    fireEvent.change(screen.getByLabelText('Cantidad de tokens'), { target: { value: '0' } })
    expect(screen.getByRole('button', { name: 'Simular' })).toBeDisabled()
    expect(simulate).not.toHaveBeenCalled()
  })

  it('muestra blockingReasons sin habilitar el POST', async () => {
    vi.spyOn(http, 'getValuationSimulatorConfig').mockResolvedValue({ data: { ...config, blockingReasons: ['POSICION_AMBIGUA'] } })
    const simulate = vi.spyOn(http, 'simulatePlayerValuation')
    render(<PlayerValuationSimulator playerId="ply-1" onClose={vi.fn()} />)
    expect(await screen.findByText(/posición del jugador es ambigua/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Simular' })).toBeDisabled()
    expect(simulate).not.toHaveBeenCalled()
  })

  it('conserva el escenario cuando el POST falla', async () => {
    vi.spyOn(http, 'simulatePlayerValuation').mockRejectedValue(new Error('fallo'))
    await renderReady()
    fireEvent.change(screen.getByLabelText('Cantidad de tokens'), { target: { value: '2.5' } })
    fireEvent.change(screen.getByLabelText('Goles predicción'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad de tokens')).toHaveValue(2.5)
    expect(screen.getByLabelText('Goles predicción')).toHaveValue(2)
  })
})
