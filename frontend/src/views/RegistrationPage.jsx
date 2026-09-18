import { useState } from 'react'
import { register } from '../api/http.js'
import { errorMessage, fieldErrors, usernameValidationMessage, validateEmail, validateRegistrationPassword, validateUsername } from './formUtils.js'
import StatusMessage from '../components/StatusMessage.jsx'

export default function RegistrationPage ({ onNavigate }) {
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [state, setState] = useState({ status: 'idle', message: '', details: [] })
  function update (event) { setForm({ ...form, [event.target.name]: event.target.value }) }
  function validate () {
    const details = []
    if (!validateUsername(form.username)) details.push(usernameValidationMessage())
    if (!validateEmail(form.email)) details.push('Introduce un correo electrónico válido.')
    if (!validateRegistrationPassword(form.password)) details.push('La contraseña debe tener entre 8 y 72 caracteres e incluir mayúscula, minúscula y número.')
    return details
  }
  async function submit (event) {
    event.preventDefault(); const details = validate()
    if (details.length > 0) { setState({ status: 'error', message: 'Revisa los datos del formulario.', details }); return }
    setState({ status: 'loading', message: '', details: [] })
    try { const response = await register(form); setState({ status: 'success', message: response?.message || 'Usuario registrado correctamente.', details: [] }) } catch (error) { setState({ status: 'error', message: errorMessage(error), details: fieldErrors(error) }) }
  }
  return (
    <section className="card form-card" aria-labelledby="registro-title">
      <div className="page-heading"><p className="eyebrow">Sumate al equipo</p><h1 id="registro-title">Crear cuenta</h1><p>Registrate para gestionar tus credenciales de acceso.</p></div>
      <form onSubmit={submit} noValidate>
        <div className="field"><label htmlFor="register-username">Nombre de usuario</label><input id="register-username" name="username" value={form.username} onChange={update} autoComplete="username" required /></div>
        <div className="field"><label htmlFor="register-email">Correo electrónico</label><input id="register-email" name="email" type="email" value={form.email} onChange={update} autoComplete="email" required /></div>
        <div className="field"><label htmlFor="register-password">Contraseña</label><input id="register-password" name="password" type="password" value={form.password} onChange={update} autoComplete="new-password" required /></div>
        {state.status === 'error' ? <div className="status-message status-message--error" role="alert" aria-live="polite">{state.message}<ul>{state.details.map((detail, index) => <li key={index}>{typeof detail === 'string' ? detail : detail.message}</li>)}</ul></div> : null}
        {state.status === 'success' ? <StatusMessage type="success">{state.message}</StatusMessage> : null}
        <button className="button button--primary" type="submit" disabled={state.status === 'loading'}>{state.status === 'loading' ? 'Registrando…' : 'Registrarme'}</button>
      </form>
      <p className="form-footer">¿Ya tenés una cuenta? <button className="button button--quiet" type="button" onClick={() => onNavigate('/login')}>Iniciar sesión</button></p>
    </section>
  )
}
