import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RegistrationPage from '../src/views/RegistrationPage.jsx'
import CatalogPage from '../src/views/CatalogPage.jsx'

function jsonResponse (body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body)
  }
}

describe('vistas React - errores HTTP visibles', () => {
  afterEach(() => vi.restoreAllMocks())

  it('representa un error 400 al registrar una cuenta', async () => {
    const user = userEvent.setup()

    globalThis.fetch = vi.fn().mockResolvedValue(
        jsonResponse(
            {
              message: 'Los datos de registro no son válidos.'
            },
            400
        )
    )

    render(
        <RegistrationPage onNavigate={vi.fn()} />
    )

    await user.type(
        screen.getByLabelText(
            'Nombre de usuario',
            { exact: true }
        ),
        'ana123'
    )

    await user.type(
        screen.getByLabelText(
            'Correo electrónico',
            { exact: true }
        ),
        'ana@example.com'
    )

    await user.type(
        screen.getByLabelText(
            'Contraseña',
            { exact: true }
        ),
        'ClaveSegura1'
    )

    await user.type(
        screen.getByLabelText(
            'Confirmar contraseña',
            { exact: true }
        ),
        'ClaveSegura1'
    )

    await user.click(
        screen.getByRole(
            'button',
            { name: 'Registrarme' }
        )
    )

    const alert = await screen.findByRole('alert')

    expect(alert).toHaveTextContent(
        'Los datos de registro no son válidos.'
    )

    expect(alert).not.toHaveTextContent(
        /stack|internal-secret|exception/i
    )
  })

  it('representa un error 503 al cargar el catálogo', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
        jsonResponse(
            {
              message: 'El servicio no está disponible temporalmente.'
            },
            503
        )
    )

    render(<CatalogPage />)

    const alert = await screen.findByRole('alert')

    expect(alert).toHaveTextContent(
        'El servicio no está disponible temporalmente.'
    )

    expect(alert).not.toHaveTextContent(
        /stack|internal-secret|exception/i
    )
  })
})