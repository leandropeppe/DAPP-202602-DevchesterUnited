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
  const contentType =
      response.headers.get('content-type') || ''

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
    response = await fetch(
        `${API_BASE_PATH}${path}`,
        {
          method,
          headers: buildHeaders({
            body,
            jwt,
            apiKey,
            headers
          }),
          body:
              body === undefined
                  ? undefined
                  : JSON.stringify(body)
        }
    )
  } catch {
    throw new HttpError(
        503,
        DEFAULT_ERROR_MESSAGES[503]
    )
  }

  const payload =
      await readResponseBody(response)

  if (!response.ok) {
    const message =
        payload?.message ||
        DEFAULT_ERROR_MESSAGES[response.status] ||
        'No fue posible completar la operación.'

    throw new HttpError(
        response.status,
        message,
        payload?.errors || null
    )
  }

  return payload
}

export const register = (data) =>
    request('/auth/register', {
      method: 'POST',
      body: data
    })

export const login = (data) =>
    request('/auth/login', {
      method: 'POST',
      body: data
    })

export const generateApiKey = (jwt) =>
    request('/auth/apikey', {
      method: 'POST',
      jwt
    })

export const getPlayers = ({
                             filters = {}
                           } = {}) => {
  const params =
      new URLSearchParams()

  for (
      const [name, value]
      of Object.entries(filters)
      ) {
    if (
        value !== undefined &&
        value !== null &&
        value !== ''
    ) {
      params.set(name, value)
    }
  }

  const query = params.toString()

  return request(
      '/players' +
      (query ? '?' + query : '')
  )
}

export const getPlayerDetail = (id) =>
    request(
        '/players/' +
        encodeURIComponent(id)
    )

export const getPlayerValuationHistory = (
    id,
    {
      page = 1,
      limit = 30,
      strategyId,
      strategyVersion
    } = {}
) => {
  const params =
      new URLSearchParams()

  params.set('page', String(page))
  params.set('limit', String(limit))

  if (strategyId) {
    params.set(
        'strategyId',
        strategyId
    )
  }

  if (
      strategyVersion !== undefined &&
      strategyVersion !== null
  ) {
    params.set(
        'strategyVersion',
        String(strategyVersion)
    )
  }

  return request(
      '/players/' +
      encodeURIComponent(id) +
      '/quotes?' +
      params.toString()
  )
}

function marketQuery (filters = {}, allowedNames = null) {
  const params = new URLSearchParams()

  for (const [name, value] of Object.entries(filters)) {
    if (allowedNames && !allowedNames.includes(name)) continue
    if (value !== undefined && value !== null && value !== '') {
      params.set(name, value)
    }
  }

  const query = params.toString()
  return query ? '?' + query : ''
}

export const getMarketBalance = (jwt) => request('/market/balance', { jwt })

export const getMarketPlayer = (playerId, jwt) => request(
  '/market/players/' + encodeURIComponent(playerId),
  { jwt }
)

export const purchaseMarketTokens = ({ body, idempotencyKey, jwt } = {}) => request(
  '/market/purchases',
  {
    method: 'POST',
    body,
    jwt,
    headers: { 'Idempotency-Key': idempotencyKey }
  }
)

export const sellMarketTokens = ({ body, idempotencyKey, jwt } = {}) => request(
  '/market/sales',
  {
    method: 'POST',
    body,
    jwt,
    headers: { 'Idempotency-Key': idempotencyKey }
  }
)

export const getMarketOperation = (idempotencyKey, jwt) => request(
  '/market/operations/' + encodeURIComponent(idempotencyKey),
  { jwt }
)

export const getMarketPortfolio = (jwt) => request('/market/portfolio', { jwt })

export const getMarketTransactions = ({ filters = {}, jwt } = {}) => request(
  '/market/transactions' + marketQuery(filters,
    ['playerId', 'type', 'from', 'to', 'page', 'limit']),
  { jwt }
)
