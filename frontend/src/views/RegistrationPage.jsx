import { useState } from 'react'
import StatusMessage from '../components/StatusMessage.jsx'
import { register } from '../api/http.js'
import {
  errorMessage,
  fieldErrors,
  usernameValidationMessage,
  validateEmail,
  validateRegistrationPassword,
  validateUsername
} from './formUtils.js'

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

export default function RegistrationPage ({ onNavigate }) {
  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    passwordConfirmation: ''
  })
  const [state, setState] = useState({
    status: 'idle',
    message: '',
    details: []
  })
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordConfirmation, setShowPasswordConfirmation] = useState(false)

  function update (event) {
    setForm({ ...form, [event.target.name]: event.target.value })
  }

  function validate () {
    const details = []

    if (!validateUsername(form.username)) {
      details.push(usernameValidationMessage())
    }

    if (!validateEmail(form.email)) {
      details.push('Introduce un correo electrónico válido.')
    }

    if (!validateRegistrationPassword(form.password)) {
      details.push(
        'La contraseña debe tener entre 8 y 72 caracteres e incluir mayúscula, minúscula y número.'
      )
    }

    if (form.password !== form.passwordConfirmation) {
      details.push('Las contraseñas no coinciden.')
    }

    return details
  }

  async function submit (event) {
    event.preventDefault()
    const details = validate()

    if (details.length > 0) {
      setState({
        status: 'error',
        message: 'Revisa los datos del formulario.',
        details
      })
      return
    }

    setState({ status: 'loading', message: '', details: [] })

    try {
      const { passwordConfirmation, ...registrationData } = form
      const response = await register(registrationData)
      setState({
        status: 'success',
        message: response?.message || 'Usuario registrado correctamente.',
        details: []
      })
    } catch (error) {
      setState({
        status: 'error',
        message: errorMessage(error),
        details: fieldErrors(error)
      })
    }
  }

  return (
    <section className="auth-page" aria-labelledby="registro-title">
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
          <p className="eyebrow">Creá tu cuenta</p>
          <h1 id="registro-title">Registrarse</h1>
          <p>Registrate para empezar a gestionar tus jugadores y credenciales.</p>
        </div>

        <form className="auth-form" onSubmit={submit} noValidate>
          <div className="field">
            <label htmlFor="register-username">Nombre de usuario</label>
            <div className="auth-input">
              <span className="auth-input__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <circle cx="12" cy="8" r="4" />
                  <path d="M4 21a8 8 0 0116 0" />
                </svg>
              </span>
              <input
                id="register-username"
                name="username"
                value={form.username}
                onChange={update}
                autoComplete="username"
                required
              />
            </div>
          </div>

          <div className="field">
            <label htmlFor="register-email">Correo electrónico</label>
            <div className="auth-input">
              <span className="auth-input__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <rect x="3" y="5" width="18" height="14" rx="2" />
                  <path d="M4 7l8 6 8-6" />
                </svg>
              </span>
              <input
                id="register-email"
                name="email"
                type="email"
                value={form.email}
                onChange={update}
                autoComplete="email"
                required
              />
            </div>
          </div>

          <div className="field">
            <label htmlFor="register-password">Contraseña</label>
            <div className="auth-input">
              <span className="auth-input__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <rect x="5" y="10" width="14" height="11" rx="2" />
                  <path d="M8 10V7a4 4 0 018 0v3" />
                </svg>
              </span>
              <input
                id="register-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                value={form.password}
                onChange={update}
                autoComplete="new-password"
                required
              />
              <button
                className="auth-password-toggle"
                type="button"
                aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                aria-pressed={showPassword}
                onClick={() => setShowPassword((visible) => !visible)}
              >
                <EyeIcon visible={showPassword} />
              </button>
            </div>
          </div>

          <div className="field">
            <label htmlFor="register-password-confirmation">Confirmar contraseña</label>
            <div className="auth-input">
              <span className="auth-input__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <rect x="5" y="10" width="14" height="11" rx="2" />
                  <path d="M8 10V7a4 4 0 018 0v3" />
                </svg>
              </span>
              <input
                id="register-password-confirmation"
                name="passwordConfirmation"
                type={showPasswordConfirmation ? 'text' : 'password'}
                value={form.passwordConfirmation}
                onChange={update}
                autoComplete="new-password"
                required
              />
              <button
                className="auth-password-toggle"
                type="button"
                aria-label={showPasswordConfirmation ? 'Ocultar confirmación' : 'Mostrar confirmación'}
                aria-pressed={showPasswordConfirmation}
                onClick={() => setShowPasswordConfirmation((visible) => !visible)}
              >
                <EyeIcon visible={showPasswordConfirmation} />
              </button>
            </div>
          </div>

          {state.status === 'error' ? (
            <div
              className="status-message status-message--error"
              role="alert"
              aria-live="polite"
            >
              {state.message}
              <ul>
                {state.details.map((detail) => (
                  <li key={typeof detail === 'string' ? detail : `${detail.field}:${detail.issue}`}>
                    {typeof detail === 'string' ? detail : detail.message}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          {state.status === 'success' ? (
            <StatusMessage type="success">{state.message}</StatusMessage>
          ) : null}

          <button
            className="button button--primary auth-submit"
            type="submit"
            disabled={state.status === 'loading'}
          >
            {state.status === 'loading' ? 'Registrando…' : 'Crear cuenta'}
            <span aria-hidden="true">→</span>
          </button>
        </form>

        <div className="auth-divider auth-divider--footer" />

        <p className="form-footer auth-footer">
          ¿Ya tenés una cuenta?{' '}
          <button
            className="button button--quiet"
            type="button"
            onClick={() => onNavigate('/login')}
          >
            Iniciar sesión
          </button>
        </p>
      </div>
    </section>
  )
}
