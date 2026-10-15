import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import CatalogPage from './CatalogPage.jsx'

describe('regresion del catalogo', () => {
  it('mantiene la navegacion accesible del catalogo', () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, headers: new Headers({ 'content-type': 'application/json' }), json: vi.fn().mockResolvedValue({ data: { players: [], pagination: { totalPages: 0, totalRecords: 0 } } }) })
    render(<CatalogPage onNavigate={vi.fn()} />)
    expect(screen.getByRole('region', { name: 'Catálogo de jugadores' })).toBeInTheDocument()
  })
})
