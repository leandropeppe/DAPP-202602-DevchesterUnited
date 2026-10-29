import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MaradonaLoader, { remainingLoadingTime } from './MaradonaLoader.jsx'

describe('MaradonaLoader', () => {
  it('es accesible y anuncia el cálculo', () => {
    render(<MaradonaLoader />)
    expect(screen.getByRole('status')).toHaveTextContent('Calculando cotización...')
  })

  it.each([[0, 3000], [300, 2700], [1500, 1500], [3000, 0], [4500, 0]])('calcula espera restante para elapsed=%sms', (elapsed, expected) => {
    expect(remainingLoadingTime(elapsed)).toBe(expected)
  })

})
