import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CredentialsPage from '../src/views/CredentialsPage.jsx'
import { SessionProvider, useSession } from '../src/session/SessionContext.jsx'
import { readSession } from '../src/session/storage.js'

function CredentialsPageWithSession () {
  const session = useSession()

  return <CredentialsPage onNavigate={vi.fn()} session={session} />
}

describe('CredentialsPage - ingreso manual de API Key', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('guarda una API Key sintetica recortada sin hacer llamadas HTTP', async () => {
    const user = userEvent.setup()
    const fetchSpy = vi.fn()
    globalThis.fetch = fetchSpy

    render(
      <SessionProvider>
        <CredentialsPageWithSession />
      </SessionProvider>
    )

    const syntheticApiKey = 'synthetic-existing-api-key'
    const input = screen.getByLabelText('Ingresar una API Key guardada')

    await user.type(input, `  ${syntheticApiKey}  `)
    await user.click(screen.getByRole('button', { name: 'Guardar API Key' }))

    await waitFor(() => {
      expect(screen.getByText(/API Key guardada para esta/)).toBeInTheDocument()
    })

    expect(input).toHaveValue('')
    expect(window.sessionStorage.getItem('devchester.api-key')).toBe(syntheticApiKey)
    expect(readSession().apiKey).toBe(syntheticApiKey)
    expect(screen.getByText(new RegExp(`API Key conservada: ${syntheticApiKey.slice(0, 14)}`))).toBeInTheDocument()
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
