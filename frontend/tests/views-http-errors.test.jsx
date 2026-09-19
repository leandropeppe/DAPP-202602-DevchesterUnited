import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RegistrationPage from '../src/views/RegistrationPage.jsx'
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

describe('vistas React - errores HTTP visibles', () => {
  afterEach(() => vi.restoreAllMocks())

  it('representa un error 400 al registrar una cuenta', async () => {
    const user = userEvent.setup()
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'Los datos de registro no son válidos.' }, 400))

    render(<RegistrationPage onNavigate={vi.fn()} />)
    await user.type(screen.getByLabelText('Nombre de usuario'), 'ana123')
    await user.type(screen.getByLabelText(/Correo electr/), 'ana@example.com')
    await user.type(screen.getByLabelText(/Contrase/), 'ClaveSegura1')
    await user.click(screen.getByRole('button', { name: 'Registrarme' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Los datos de registro no son válidos.')
    expect(alert).not.toHaveTextContent(/stack|internal-secret|exception/i)
  })

  it('representa un error 401 al generar una API Key', async () => {
    const user = userEvent.setup()
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'La credencial no es válida.' }, 401))
    window.sessionStorage.setItem('devchester.jwt', 'header.eyJleHAiOjQxMDI0NDQ4MDB9.signature')

    withSession(CredentialsPage, { onNavigate: vi.fn() })
    await user.click(screen.getByRole('button', { name: 'Generar o rotar API Key' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('La credencial no es válida.')
    expect(alert).not.toHaveTextContent(/stack|internal-secret|exception/i)
  })

  it('representa un error 503 al cargar el catálogo', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'El servicio no está disponible temporalmente.' }, 503))

    render(<CatalogPage />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('El servicio no está disponible temporalmente.')
    expect(alert).not.toHaveTextContent(/stack|internal-secret|exception/i)
  })
})
