import { ref } from 'vue'
import { createApiClient } from '../api/client.js'
import {
  fetchCurrentUser,
  fetchDingTalkLoginUrl,
  loginWithPassword,
  logoutCurrentUser
} from '../api/auth.js'

export const TOKEN_KEY = 'weekly_report_jwt'

export function consumeOAuthCallback({ href, history, storage }) {
  const url = new URL(href)
  const queryToken = url.searchParams.get('token') || ''
  const authError = safeAuthError(url.searchParams.get('auth_error') || '', queryToken)
  if (queryToken) {
    writeToken(storage, queryToken)
  }
  if (queryToken || authError || url.searchParams.has('login')) {
    url.searchParams.delete('token')
    url.searchParams.delete('auth_error')
    url.searchParams.delete('login')
    history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
  }
  return { token: queryToken, authError }
}

export function useAuth({
  fetchImpl = globalThis.fetch,
  storage = globalThis.localStorage,
  location = globalThis.location,
  history = globalThis.history,
  onAuthInvalidated = () => {}
} = {}) {
  const authLoading = ref(true)
  const loginBusy = ref(false)
  const dingtalkBusy = ref(false)
  const loginError = ref('')
  const token = ref(readToken(storage))
  const currentUser = ref(null)

  const client = createApiClient({
    fetchImpl,
    getToken: () => token.value,
    onUnauthorized: () => clearAuth()
  })

  function applyLogin(data) {
    token.value = data.token
    currentUser.value = data.user
    writeToken(storage, data.token)
  }

  function clearAuth(notify = true) {
    token.value = ''
    currentUser.value = null
    removeToken(storage)
    if (notify) onAuthInvalidated()
  }

  function readAuthQuery() {
    const result = consumeOAuthCallback({ href: location.href, history, storage })
    if (result.token) token.value = result.token
    if (result.authError) loginError.value = result.authError
    return result
  }

  async function restoreSession(onAuthenticated) {
    authLoading.value = true
    loginError.value = ''
    readAuthQuery()
    try {
      if (token.value) {
        currentUser.value = await fetchCurrentUser(client)
        await onAuthenticated?.()
      }
    } catch (error) {
      clearAuth(false)
      loginError.value = error.message
    } finally {
      authLoading.value = false
    }
  }

  async function signIn(credentials) {
    if (!credentials.username || !credentials.password) {
      loginError.value = '请输入用户名和密码'
      return null
    }
    try {
      loginBusy.value = true
      loginError.value = ''
      const data = await loginWithPassword(client, credentials)
      applyLogin(data)
      return data
    } catch (error) {
      loginError.value = error.message
      return null
    } finally {
      loginBusy.value = false
    }
  }

  async function startDingTalkLogin() {
    try {
      dingtalkBusy.value = true
      loginError.value = ''
      const data = await fetchDingTalkLoginUrl(client)
      if (!data.enabled || !data.loginUrl) {
        loginError.value = data.message || '钉钉登录暂未启用'
        return false
      }
      location.href = data.loginUrl
      return true
    } catch (error) {
      loginError.value = error.message
      return false
    } finally {
      dingtalkBusy.value = false
    }
  }

  async function signOut() {
    try {
      await logoutCurrentUser(client)
    } catch {
      // JWT is stateless, so local login state can always be cleared.
    }
    clearAuth()
  }

  return {
    authLoading,
    loginBusy,
    dingtalkBusy,
    loginError,
    token,
    currentUser,
    request: client.request,
    restoreSession,
    signIn,
    startDingTalkLogin,
    signOut,
    clearAuth
  }
}

function readToken(storage) {
  try {
    return storage?.getItem(TOKEN_KEY) || ''
  } catch {
    return ''
  }
}

function writeToken(storage, token) {
  try {
    storage?.setItem(TOKEN_KEY, token)
  } catch {
    // The in-memory session remains usable when persistent storage is unavailable.
  }
}

function removeToken(storage) {
  try {
    storage?.removeItem(TOKEN_KEY)
  } catch {
    // Clearing the in-memory token is the security boundary.
  }
}

function safeAuthError(value, queryToken) {
  let message = String(value || '').trim().slice(0, 200)
  if (queryToken) message = message.replaceAll(queryToken, '[redacted]')
  return message
    .replace(/Bearer\s+[^\s,;]+/gi, 'Bearer [redacted]')
    .replace(/[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}/g, '[redacted-token]')
}
