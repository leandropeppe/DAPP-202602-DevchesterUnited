export function validateUsername (value) {
  const policy = (import.meta.env.VITE_USERNAME_POLICY || 'strict').toLowerCase()
  const min = Number(import.meta.env.VITE_USERNAME_MIN_LENGTH || 3)
  const max = Number(import.meta.env.VITE_USERNAME_MAX_LENGTH || 30)
  if (value.length < min || value.length > max || /[\s\u0000-\u001F\u007F-\u009F]/u.test(value)) return false
  return policy !== 'strict' || /^[A-Za-z0-9]+$/.test(value)
}

export function usernameValidationMessage () {
  const policy = (import.meta.env.VITE_USERNAME_POLICY || 'strict').toLowerCase()
  const min = Number(import.meta.env.VITE_USERNAME_MIN_LENGTH || 3)
  const max = Number(import.meta.env.VITE_USERNAME_MAX_LENGTH || 30)
  return policy === 'strict'
    ? `El nombre de usuario debe tener entre ${min} y ${max} caracteres alfanuméricos.`
    : `El nombre de usuario debe tener entre ${min} y ${max} caracteres y no contener espacios.`
}

export function validateEmail (value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) && value.length <= 254
}

export function validateRegistrationPassword (value) {
  const bytes = new TextEncoder().encode(value).length
  return value.length >= 8 && value.length <= 72 && bytes <= 72 &&
    /[a-z]/.test(value) && /[A-Z]/.test(value) && /[0-9]/.test(value) &&
    !/^\s|\s$/.test(value)
}

export function errorMessage (error) {
  if (!error) return 'No fue posible completar la operación.'
  if (error.status === 400) return error.message || 'Revisa los datos enviados.'
  if (error.status === 401) return error.message || 'La sesión o la credencial no es válida.'
  if (error.status === 409) return error.message || 'La operación entra en conflicto con otra solicitud.'
  if (error.status === 503) return error.message || 'El servicio no está disponible temporalmente.'
  return error.message || 'No fue posible completar la operación.'
}

export function fieldErrors (error) {
  return Array.isArray(error?.details) ? error.details : []
}
