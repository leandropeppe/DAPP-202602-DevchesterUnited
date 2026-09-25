import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RegistrationPage from '../src/views/RegistrationPage.jsx'
import LoginPage from '../src/views/LoginPage.jsx'
import CredentialsPage from '../src/views/CredentialsPage.jsx'
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

  return render(
    <SessionProvider>
      <Wrapped />
    </SessionProvider>
  )
}

describe('accesibilidad implementada del frontend', () => {
  afterEach(() => vi.restoreAllMocks())

  it('mantiene un orden de foco lógico y permite enviar Login con Enter', async () => {
    const user = userEvent.setup()
    const navigate = vi.fn()
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: { access_token: 'synthetic-jwt' } }))

    withSession(LoginPage, { onNavigate: navigate })

    await user.tab()
    expect(screen.getByLabelText('Nombre de usuario')).toHaveFocus()
    await user.type(screen.getByLabelText('Nombre de usuario'), 'ana123')
    await user.tab()
    expect(screen.getByLabelText(/Contrase/)).toHaveFocus()
    await user.type(screen.getByLabelText(/Contrase/), 'ClaveSegura1')
    await user.tab()

    const submit = screen.getByRole('button', { name: /Iniciar sesi/ })
    expect(submit).toHaveFocus()
    await user.keyboard('{Enter}')

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/catalogo'))
  })

  it('expone labels representativos mediante nombres accesibles en las cuatro vistas', () => {
    const registration = render(<RegistrationPage onNavigate={vi.fn()} />)
    expect(screen.getByLabelText('Nombre de usuario')).toBeInTheDocument()
    registration.unmount()

    const login = withSession(LoginPage, { onNavigate: vi.fn() })
    expect(screen.getByLabelText(/Contrase/)).toBeInTheDocument()
    login.unmount()

    const credentials = withSession(CredentialsPage, { onNavigate: vi.fn() })
    expect(screen.getByLabelText('Ingresar una API Key guardada')).toBeInTheDocument()
    credentials.unmount()

    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: { players: [], pagination: {} } }))
    const catalog = render(<CatalogPage />)
    expect(screen.getByRole('form', { name: /Filtros del cat/ })).toBeInTheDocument()
    expect(screen.getByLabelText('Equipo')).toBeInTheDocument()
    catalog.unmount()
  })

  it('permite recorrer filtros con Tab y conserva regiones accesibles mientras carga el catálogo', async () => {
    const user = userEvent.setup()
    let resolveRequest
    let resolved = false
    const pendingRequest = new Promise((resolve) => { resolveRequest = resolve })
    globalThis.fetch = vi.fn().mockReturnValue(pendingRequest)

    render(<CatalogPage />)
    const response = jsonResponse({
      data: {
        players: [{ id: 'player-1', name: 'Jugador Sintético', league: 'LaLiga', team: 'Madrid', position: 'Forward', jersey_number: null }],
        pagination: { current_page: 1, total_pages: 1, total_records: 1 }
      }
    })

    try {
      const league = screen.getByLabelText('Liga')
      const team = screen.getByLabelText('Equipo')
      const position = screen.getByLabelText(/Posici/)
      const apply = screen.getByRole('button', { name: 'Aplicar filtros' })

      await user.tab()
      expect(league).toHaveFocus()
      await user.tab()
      expect(team).toHaveFocus()
      await user.tab()
      expect(position).toHaveFocus()
      expect(apply).toBeDisabled()
      expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite')

      resolveRequest(response)
      resolved = true
      const table = await screen.findByRole('table')
      expect(table.querySelector('caption')).toHaveTextContent(/Jugadores del cat/)
      expect(within(table).getAllByRole('columnheader')).toHaveLength(5)
      expect(screen.getByLabelText('Liga')).toBeInTheDocument()
      expect(screen.getByLabelText('Equipo')).toBeInTheDocument()
      expect(screen.getByLabelText(/Posici/)).toBeInTheDocument()
    } finally {
      if (!resolved) resolveRequest(response)
    }
  })
})
