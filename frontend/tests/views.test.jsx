import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RegistrationPage from '../src/views/RegistrationPage.jsx'
import LoginPage from '../src/views/LoginPage.jsx'
import CatalogPage from '../src/views/CatalogPage.jsx'
import { SessionProvider, useSession } from '../src/session/SessionContext.jsx'

function jsonResponse (body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body)
  }
}

function withSession (Component, props = {}) {
  function Wrapped () {
    const session = useSession()
    return <Component {...props} session={session} />
  }
  return render(<SessionProvider><Wrapped /></SessionProvider>)
}

function fill (label, value) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('vistas funcionales de US5', () => {
  afterEach(() => vi.restoreAllMocks())

  it('valida el registro antes de llamar al backend', async () => {
    global.fetch = vi.fn()
    render(<RegistrationPage onNavigate={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Registrarme' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Revisa los datos')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('registra un usuario y muestra el éxito', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'Registro correcto' }, 201))
    render(<RegistrationPage onNavigate={vi.fn()} />)
    fill('Nombre de usuario', 'ana123')
    fill('Correo electrónico', 'ana@example.com')
    fill('Contraseña', 'ClaveSegura1')

    fireEvent.click(screen.getByRole('button', { name: 'Registrarme' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Registro correcto')
  })

  it('hace login, guarda el JWT y navega al catálogo', async () => {
    const navigate = vi.fn()
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: { access_token: 'jwt-login' } }))
    withSession(LoginPage, { onNavigate: navigate })
    fill('Nombre de usuario', 'ana123')
    fill('Contraseña', 'ClaveSegura1')

    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/catalogo'))
    expect(window.sessionStorage.getItem('devchester.jwt')).toBe('jwt-login')
  })

  it('consulta el catálogo sin credenciales con filtros, paginación y valores null', async () => {
    const responses = [
      jsonResponse({ data: { players: [{ id: '1', name: 'Jugador Uno', league: 'LaLiga', team: 'Madrid', position: null, jersey_number: null }], pagination: { current_page: 1, total_pages: 2, total_records: 2 } } }),
      jsonResponse({ data: { players: [], pagination: { current_page: 2, total_pages: 2, total_records: 2 } } })
    ]
    global.fetch = vi.fn().mockImplementation(() => Promise.resolve(responses.shift()))
    withSession(CatalogPage, { onNavigate: vi.fn() })

    expect(await screen.findByText('Jugador Uno')).toBeInTheDocument()
    const playerCard = screen.getByRole('link', { name: /Ver estadísticas de Jugador Uno/ })
    expect(within(playerCard).getByText('--')).toBeInTheDocument()
    expect(within(playerCard).getByText('Sin información')).toBeInTheDocument()

    fill('Equipo', 'Madrid')
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/players?team=Madrid&page=1&limit=20', expect.anything()))
    const filterRequest = fetch.mock.calls[1][1]
    expect(filterRequest.headers.get('Authorization')).toBeNull()
    expect(filterRequest.headers.get('X-API-KEY')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/players?team=Madrid&page=2&limit=20', expect.anything()))
  })

  it('conserva una API Key opcional cuando falla la consulta pública', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'API Key revocada' }, 401))
    window.sessionStorage.setItem('devchester.api-key', 'dev_key_revoked')
    withSession(CatalogPage, { onNavigate: vi.fn() })

    expect(await screen.findByRole('alert')).toHaveTextContent('API Key revocada')
    expect(window.sessionStorage.getItem('devchester.api-key')).toBe('dev_key_revoked')
  })
})
