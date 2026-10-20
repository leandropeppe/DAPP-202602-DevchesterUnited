import { useState } from 'react'
import StatusMessage from '../components/StatusMessage.jsx'
import { login } from '../api/http.js'
import { errorMessage, validateUsername } from './formUtils.js'

function EyeIcon ({ visible }) {
  return visible ? (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 3l18 18M10.6 10.7a2 2 0 002.7 2.7M9.9 5.2A10.7 10.7 0 0112 5c5.5 0 9 7 9 7a17.6 17.6 0 01-2.4 3.3M6.6 6.6C4.4 8 3 12 3 12s3.5 7 9 7a9.8 9.8 0 004.1-.9" />
    </svg>
  ) : (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 12s3.5-7 9-7 9 7 9 7-3.5 7-9 7-9-7-9-7z" />
      <circle cx="12" cy="12" r="2.5" />
    </svg>
  )
}

export default function LoginPage ({ onNavigate, session }) {
  const { setJwt } = session
  const [form, setForm] = useState({ username: '', password: '' })
  const [state, setState] = useState({ status: 'idle', message: '' })
  const [showPassword, setShowPassword] = useState(false)

  function update (event) {
    setForm({ ...form, [event.target.name]: event.target.value })
  }

  async function submit (event) {
    event.preventDefault()

    if (!validateUsername(form.username) || form.password.length === 0) {
      setState({
        status: 'error',
        message: 'Introduce un nombre de usuario y una contraseña válidos.'
      })
      return
    }

    setState({ status: 'loading', message: '' })

    try {
      const response = await login(form)
      const token = response?.data?.access_token

      if (!token) {
        throw new Error('La respuesta de login no contiene un JWT válido.')
      }

      setJwt(token)
      setState({ status: 'success', message: 'Inicio de sesión correcto.' })
      onNavigate('/catalogo')
    } catch (error) {
      setState({ status: 'error', message: errorMessage(error) })
    }
  }

  return (
    <section className="auth-page" aria-labelledby="login-title">
      <div className="auth-card">
        <div className="auth-brand" aria-hidden="true">
          <img src="/images/devchester-logo-icon.png" alt="" />
          <div className="auth-brand__name">
            <strong><span>Dev</span>chester</strong>
            <small>UNITED</small>
          </div>
        </div>

        <div className="auth-divider" />

        <div className="page-heading auth-heading">
          <p className="eyebrow">Bienvenido de vuelta</p>
          <h1 id="login-title">Iniciar sesión</h1>
          <p>Accedé a tu cuenta para administrar tus credenciales.</p>
        </div>

        <form className="auth-form" onSubmit={submit} noValidate>
          <div className="field">
            <label htmlFor="login-username">Nombre de usuario</label>
            <div className="auth-input">
              <span className="auth-input__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <circle cx="12" cy="8" r="4" />
                  <path d="M4 21a8 8 0 0116 0" />
                </svg>
              </span>
              <input
                id="login-username"
                name="username"
                value={form.username}
                onChange={update}
                autoComplete="username"
                required
              />
            </div>
          </div>

          <div className="field">
            <label htmlFor="login-password">Contraseña</label>
            <div className="auth-input">
              <span className="auth-input__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <rect x="5" y="10" width="14" height="11" rx="2"/>
                  <path d="M8 10V7a4 4 0 018 0v3"/>
                </svg>
              </span>
              <input
                  id="login-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  value={form.password}
                  onChange={update}
                  autoComplete="current-password"
                  required
              />
              <button
                  className="auth-password-toggle"
                  type="button"
                  tabIndex={-1}
                  aria-label={
                    showPassword
                        ? 'Ocultar clave'
                        : 'Mostrar clave'
                  }
                  aria-pressed={showPassword}
                  onClick={() => setShowPassword((visible) => !visible)}
              >
                <EyeIcon visible={showPassword}/>
              </button>
            </div>
          </div>

          <StatusMessage type={state.status}>{state.message}</StatusMessage>

          <button
              className="button button--primary auth-submit"
              type="submit"
              disabled={state.status === 'loading'}
          >
            {state.status === 'loading' ? 'Ingresando…' : 'Iniciar sesión'}
            <span aria-hidden="true">→</span>
          </button>
        </form>

        <div className="auth-divider auth-divider--footer" />

        <p className="form-footer auth-footer">
          ¿Todavía no tenés cuenta?{' '}
          <button
            className="button button--quiet"
            type="button"
            onClick={() => onNavigate('/registro')}
          >
            Crear una cuenta
          </button>
        </p>
      </div>
    </section>
  )
}
