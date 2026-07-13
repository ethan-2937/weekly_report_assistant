const DEFAULT_HTTP_ERROR = '请求失败，请稍后重试'
const NETWORK_ERROR = '网络请求失败，请稍后重试'
const AUTH_EXPIRED_ERROR = '登录已过期，请重新登录'
const SENSITIVE_KEY = /(password|secret|token)/i

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function createApiClient({ getToken = () => '', onUnauthorized = () => {}, fetchImpl = globalThis.fetch } = {}) {
  if (typeof fetchImpl !== 'function') {
    throw new TypeError('A fetch implementation is required')
  }

  async function request(path, options = {}) {
    const { skipAuth = false, responseType = 'auto', ...fetchOptions } = options
    const headers = new Headers(fetchOptions.headers || {})
    const token = skipAuth ? '' : String(getToken() || '')
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
    if (fetchOptions.body && !(fetchOptions.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }

    let response
    try {
      response = await fetchImpl(path, { ...fetchOptions, headers })
    } catch {
      throw new ApiError(NETWORK_ERROR)
    }

    if (response.status === 401 && !skipAuth) {
      onUnauthorized()
      throw new ApiError(AUTH_EXPIRED_ERROR, response.status)
    }

    if (!response.ok) {
      const data = await readJson(response)
      const secrets = [token, ...sensitiveBodyValues(fetchOptions.body)]
      throw new ApiError(safeErrorMessage(data?.error, response.status, secrets), response.status)
    }

    if (response.status === 204) return null
    if (responseType === 'blob') return response.blob()
    if (responseType === 'text') return response.text()
    const contentType = response.headers.get('content-type') || ''
    if (responseType === 'json' || contentType.includes('application/json')) {
      return readJson(response)
    }
    return response.text()
  }

  return { request }
}

async function readJson(response) {
  return response.json().catch(() => ({}))
}

function sensitiveBodyValues(body) {
  if (typeof body !== 'string') return []
  try {
    const parsed = JSON.parse(body)
    return Object.entries(parsed)
      .filter(([key, value]) => SENSITIVE_KEY.test(key) && typeof value === 'string' && value)
      .map(([, value]) => value)
  } catch {
    return []
  }
}

function safeErrorMessage(value, status, secrets) {
  if (typeof value !== 'string' || !value.trim()) {
    return `${DEFAULT_HTTP_ERROR} (HTTP ${status})`
  }
  let message = value.trim().slice(0, 200)
  for (const secret of secrets.filter(Boolean)) {
    message = message.replaceAll(secret, '[redacted]')
  }
  message = message
    .replace(/Bearer\s+[^\s,;]+/gi, 'Bearer [redacted]')
    .replace(/[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}/g, '[redacted-token]')
  return message || `${DEFAULT_HTTP_ERROR} (HTTP ${status})`
}
