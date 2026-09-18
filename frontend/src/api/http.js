const API_BASE_PATH = '/api/v1'

const DEFAULT_ERROR_MESSAGES = {
  400: 'La solicitud no es válida.',
  401: 'La sesión o la credencial no es válida.',
  409: 'La operación entra en conflicto con otra solicitud.',
  503: 'El servicio no está disponible temporalmente.'
}

export class HttpError extends Error {
  constructor (status, message, details = null) {
    super(message)
    this.name = 'HttpError'
    this.status = status
    this.details = details
  }
}

function buildHeaders ({ body, jwt, apiKey, headers = {} }) {
  const requestHeaders = new Headers(headers)
  requestHeaders.set('Accept', 'application/json')

  if (body !== undefined) {
    requestHeaders.set('Content-Type', 'application/json')
  }

  if (jwt) {
    requestHeaders.set('Authorization', `Bearer ${jwt}`)
  }

  if (apiKey) {
    requestHeaders.set('X-API-KEY', apiKey)
  }

  return requestHeaders
}

async function readResponseBody (response) {
  const contentType = response.headers.get('content-type') || ''

  if (!contentType.includes('application/json')) {
    return null
  }

  try {
    return await response.json()
  } catch {
    return null
  }
}

export async function request (path, {
  method = 'GET',
  body,
  jwt,
  apiKey,
  headers
} = {}) {
  let response
  try {
    response = await fetch(`${API_BASE_PATH}${path}`, {
      method,
      headers: buildHeaders({ body, jwt, apiKey, headers }),
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch {
    throw new HttpError(503, DEFAULT_ERROR_MESSAGES[503])
  }
  const payload = await readResponseBody(response)

  if (!response.ok) {
    const message = payload?.message || DEFAULT_ERROR_MESSAGES[response.status] || 'No fue posible completar la operación.'
    throw new HttpError(response.status, message, payload?.errors || null)
  }

  return payload
}

export const register = (data) => request('/auth/register', {
  method: 'POST',
  body: data
})

export const login = (data) => request('/auth/login', {
  method: 'POST',
  body: data
})

export const generateApiKey = (jwt) => request('/auth/apikey', {
  method: 'POST',
  jwt
})

export const getPlayers = ({ filters = {} } = {}) => {
  const params = new URLSearchParams()

  for (const [name, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(name, value)
    }
  }

  const query = params.toString()
  return request(`/players${query ? `?${query}` : ''}`)
}
