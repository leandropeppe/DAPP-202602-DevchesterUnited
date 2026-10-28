import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MaradonaLoader, { remainingLoadingTime } from './MaradonaLoader.jsx'

describe('MaradonaLoader', () => {
  it('es accesible y anuncia el cálculo', () => {
    render(<MaradonaLoader />)
    expect(screen.getByRole('status')).toHaveTextContent('Calculando simulación')
  })

  it.each([[300, 1700], [1500, 500], [2500, 0]])('calcula espera restante para respuesta en %sms', (elapsed, expected) => {
    expect(remainingLoadingTime(0, elapsed)).toBe(expected)
  })

})
