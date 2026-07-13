import { describe, expect, it, vi } from 'vitest'
import { consumeOAuthCallback, TOKEN_KEY, useAuth } from './useAuth.js'

describe('authentication session', () => {
  it('stores an OAuth token and removes authentication parameters from the URL', () => {
    const storage = memoryStorage()
    const history = { replaceState: vi.fn() }

    const result = consumeOAuthCallback({
      href: 'https://weekly.example.test/callback?token=fictional-oauth-token&keep=1&login=dingtalk#done',
      history,
      storage
    })

    expect(result.token).toBe('fictional-oauth-token')
    expect(storage.getItem(TOKEN_KEY)).toBe('fictional-oauth-token')
    expect(history.replaceState).toHaveBeenCalledWith({}, '', '/callback?keep=1#done')
    expect(history.replaceState.mock.calls[0][2]).not.toContain('token=')
  })

  it('does not expose the OAuth token through an authentication error', () => {
    const storage = memoryStorage()
    const history = { replaceState: vi.fn() }

    const result = consumeOAuthCallback({
      href: 'https://weekly.example.test/callback?token=fictional-oauth-token&auth_error=failed%20fictional-oauth-token',
      history,
      storage
    })

    expect(result.authError).toContain('failed')
    expect(result.authError).not.toContain('fictional-oauth-token')
  })

  it('clears the stored token when the API client receives a 401 response', async () => {
    const storage = memoryStorage({ [TOKEN_KEY]: 'fictional-token-005' })
    const invalidated = vi.fn()
    const auth = useAuth({
      storage,
      location: { href: 'https://weekly.example.test/' },
      history: { replaceState: vi.fn() },
      onAuthInvalidated: invalidated,
      fetchImpl: vi.fn().mockResolvedValue(new Response('{}', {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      }))
    })

    await expect(auth.request('/api/weeks')).rejects.toMatchObject({ status: 401 })

    expect(auth.token.value).toBe('')
    expect(storage.getItem(TOKEN_KEY)).toBeNull()
    expect(invalidated).toHaveBeenCalledOnce()
  })
})

function memoryStorage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  }
}
