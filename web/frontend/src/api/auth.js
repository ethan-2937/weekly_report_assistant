export function fetchCurrentUser(client) {
  return client.request('/api/auth/me')
}

export function loginWithPassword(client, credentials) {
  return client.request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
    skipAuth: true
  })
}

export function fetchDingTalkLoginUrl(client) {
  return client.request('/api/auth/dingtalk/login-url', { skipAuth: true })
}

export function logoutCurrentUser(client) {
  return client.request('/api/auth/logout', { method: 'POST' })
}

export function changeCurrentPassword(client, passwords) {
  return client.request('/api/auth/password', {
    method: 'POST',
    body: JSON.stringify(passwords)
  })
}
