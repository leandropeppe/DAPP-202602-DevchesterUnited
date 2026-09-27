import { useState } from 'react'
import StatusMessage from '../components/StatusMessage.jsx'
import { login } from '../api/http.js'
import { errorMessage, validateUsername } from './formUtils.js'

export default function LoginPage ({ onNavigate, session }) {
  const { setJwt } = session
  const [form, setForm] = useState({ username: '', password: '' })
  const [state, setState] = useState({ status: 'idle', message: '' })

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
    <section className="card form-card" aria-labelledby="login-title">
      <div className="page-heading">
        <p className="eyebrow">Bienvenido de vuelta</p>
        <h1 id="login-title">Iniciar sesión</h1>
        <p>Accedé a tu cuenta para administrar tus credenciales.</p>
      </div>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="login-username">Nombre de usuario</label>
          <input
            id="login-username"
            name="username"
            value={form.username}
            onChange={update}
            autoComplete="username"
            required
          />
        </div>
        <div className="field">
          <label htmlFor="login-password">Contraseña</label>
          <input
            id="login-password"
            name="password"
            type="password"
            value={form.password}
            onChange={update}
            autoComplete="current-password"
            required
          />
        </div>
        <StatusMessage type={state.status}>{state.message}</StatusMessage>
        <button
          className="button button--primary"
          type="submit"
          disabled={state.status === 'loading'}
        >
          {state.status === 'loading' ? 'Ingresando…' : 'Iniciar sesión'}
        </button>
      </form>
      <p className="form-footer">
        ¿Todavía no tenés cuenta?{' '}
        <button
          className="button button--quiet"
          type="button"
          onClick={() => onNavigate('/registro')}
        >
          Crear una cuenta
        </button>
      </p>
    </section>
  )
}
