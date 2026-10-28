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

afterEach(() => vi.restoreAllMocks())

describe('PlayerValuationSimulator', () => {
  it('renderiza solo editables y diferencia acumulativa de valor final', async () => {
    vi.spyOn(http, 'getValuationSimulatorConfig').mockResolvedValue({ data: config })
    vi.spyOn(http, 'simulatePlayerValuation').mockResolvedValue({ data: { currentQuote: 100, simulatedQuote: 110, monetaryDifference: 10, percentageDifference: 10 } })
    render(<PlayerValuationSimulator playerId="ply-1" onClose={vi.fn()} />)
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText('Goles predicción')).toHaveValue(0)
    expect(screen.getByLabelText('Tiros por partido valor proyectado')).toHaveValue(5.2)
    expect(screen.queryByLabelText(/Regates/)).not.toBeInTheDocument()
    expect(screen.getByText('Partidos:')).toHaveTextContent('10 → 13')
  })

  it('envía cambios, muestra resultado, permite recalcular y restablecer', async () => {
    vi.spyOn(http, 'getValuationSimulatorConfig').mockResolvedValue({ data: config })
    const simulate = vi.spyOn(http, 'simulatePlayerValuation').mockResolvedValue({ data: { currentQuote: 100, simulatedQuote: 110, monetaryDifference: 10, percentageDifference: 10 } })
    render(<PlayerValuationSimulator playerId="ply-1" onClose={vi.fn()} />)
    await screen.findByRole('dialog')
    fireEvent.change(screen.getByLabelText('Goles predicción'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    await waitFor(() => expect(simulate).toHaveBeenCalledWith('ply-1', { horizonMatches: 3, modifications: { GOALS: 2 } }))
    expect(await screen.findByText('Cotización simulada', {}, { timeout: 3000 })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Restablecer' }))
    expect(screen.getByLabelText('Goles predicción')).toHaveValue(0)
  })

  it('muestra blockingReasons sin habilitar el POST', async () => {
    vi.spyOn(http, 'getValuationSimulatorConfig').mockResolvedValue({ data: { ...config, blockingReasons: ['POSICION_AMBIGUA'] } })
    const simulate = vi.spyOn(http, 'simulatePlayerValuation')
    render(<PlayerValuationSimulator playerId="ply-1" onClose={vi.fn()} />)
    expect(await screen.findByText(/posición del jugador es ambigua/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Simular' })).toBeDisabled()
    expect(simulate).not.toHaveBeenCalled()
  })
})
