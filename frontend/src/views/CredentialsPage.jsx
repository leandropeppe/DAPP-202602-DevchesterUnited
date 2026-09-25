import { useState } from 'react'
import StatusMessage from '../components/StatusMessage.jsx'
import { generateApiKey } from '../api/http.js'
import { errorMessage } from './formUtils.js'

export default function CredentialsPage ({ onNavigate, session }) {
  const { jwt, apiKey, setApiKey, isJwtExpired } = session
  const [savedKey, setSavedKey] = useState('')
  const [newKey, setNewKey] = useState(null)
  const [state, setState] = useState({ status: 'idle', message: '' })

  function saveExistingKey (event) {
    event.preventDefault()

    if (!savedKey.trim()) {
      setState({
        status: 'error',
        message: 'Introduce una API Key para guardarla en esta pestaña.'
      })
      return
    }

    setApiKey(savedKey.trim())
    setSavedKey('')
    setState({
      status: 'success',
      message: 'API Key guardada para esta pestaña.'
    })
  }

  async function rotateKey () {
    if (!jwt || isJwtExpired) {
      setState({
        status: 'error',
        message: 'Necesitas iniciar sesión nuevamente para generar una API Key.'
      })
      return
    }

    setState({ status: 'loading', message: '' })
    setNewKey(null)

    try {
      const response = await generateApiKey(jwt)
      const generated = response?.data?.api_key

      if (!generated) {
        throw new Error('La respuesta no contiene una API Key válida.')
      }

      setApiKey(generated)
      setNewKey(generated)
      setState({
        status: 'success',
        message: response?.message || 'API Key generada correctamente.'
      })
    } catch (error) {
      setState({ status: 'error', message: errorMessage(error) })
    }
  }

  return (
    <section aria-labelledby="credentials-title">
      <div className="page-heading">
        <p className="eyebrow">Administración</p>
        <h1 id="credentials-title">Credenciales</h1>
        <p>
          Gestioná la clave para integraciones administrativas. No es necesaria
          para consultar el catálogo.
        </p>
      </div>
      <div className="card" style={{ padding: 'clamp(22px, 4vw, 36px)' }}>
        {!jwt || isJwtExpired ? (
          <StatusMessage type="error">
            Inicia sesión para generar o rotar una API Key.
          </StatusMessage>
        ) : null}
        {apiKey ? (
          <output className="status-message status-message--info">
            API Key conservada: {apiKey.slice(0, 14)}…
          </output>
        ) : (
          <p className="muted">
            No hay una API Key guardada en esta pestaña.
          </p>
        )}
        <p>
          <strong>Importante:</strong> generar una nueva API Key revoca la
          anterior.
        </p>
        <button
          className="button button--primary"
          type="button"
          onClick={rotateKey}
          disabled={!jwt || isJwtExpired || state.status === 'loading'}
        >
          {state.status === 'loading'
            ? 'Generando…'
            : 'Generar o rotar API Key'}
        </button>
        {newKey ? (
          <div
            className="status-message status-message--success"
            role="alert"
            aria-live="polite"
          >
            <p>Guardá esta clave ahora; no volverá a mostrarse completa.</p>
            <code>{newKey}</code>
          </div>
        ) : null}
        <form
          onSubmit={saveExistingKey}
          style={{
            display: 'grid',
            gap: '12px',
            maxWidth: '560px',
            marginTop: '28px'
          }}
        >
          <div className="field">
            <label htmlFor="saved-api-key">Ingresar una API Key guardada</label>
            <input
              id="saved-api-key"
              value={savedKey}
              onChange={(event) => setSavedKey(event.target.value)}
              autoComplete="off"
            />
          </div>
          <button className="button button--secondary" type="submit">
            Guardar API Key
          </button>
        </form>
        <StatusMessage type={state.status}>{state.message}</StatusMessage>
      </div>
    </section>
  )
}
