import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CredentialsPage from '../src/views/CredentialsPage.jsx'
import { SessionProvider, useSession } from '../src/session/SessionContext.jsx'
import { readSession } from '../src/session/storage.js'

function CredentialsPageWithSession () {
  const session = useSession()

  return <CredentialsPage onNavigate={vi.fn()} session={session} />
}

describe('CredentialsPage - JWT expirado', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('trata el JWT expirado como no autenticado y conserva la API Key', () => {
    const expiredJwt = 'header.eyJleHAiOjF9.signature'
    const syntheticApiKey = 'synthetic-existing-api-key'
    const fetchSpy = vi.fn()
    globalThis.fetch = fetchSpy
    window.sessionStorage.setItem('devchester.jwt', expiredJwt)
    window.sessionStorage.setItem('devchester.api-key', syntheticApiKey)

    render(
      <SessionProvider>
        <CredentialsPageWithSession />
      </SessionProvider>
    )

    expect(screen.getByRole('alert')).toHaveTextContent(/Inicia sesi.n para generar o rotar una API Key/)
    expect(screen.getByRole('button', { name: 'Generar o rotar API Key' })).toBeDisabled()
    expect(screen.getByText(new RegExp(`API Key conservada: ${syntheticApiKey.slice(0, 14)}`))).toBeInTheDocument()
    expect(readSession().jwt).toBeNull()
    expect(readSession().apiKey).toBe(syntheticApiKey)
    expect(window.sessionStorage.getItem('devchester.jwt')).toBeNull()
    expect(window.sessionStorage.getItem('devchester.api-key')).toBe(syntheticApiKey)
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
