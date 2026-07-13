import { describe, expect, it, vi } from 'vitest'
import { loginWithPassword } from './auth.js'
import { createApiClient } from './client.js'

describe('API client authentication boundary', () => {
  it('adds the Authorization header when a token exists', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    const client = createApiClient({ getToken: () => 'fictional-token-001', fetchImpl })

    await client.request('/api/example')

    const headers = fetchImpl.mock.calls[0][1].headers
    expect(headers.get('Authorization')).toBe('Bearer fictional-token-001')
  })

  it('does not add the Authorization header without a token', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    const client = createApiClient({ getToken: () => '', fetchImpl })

    await client.request('/api/example')

    const headers = fetchImpl.mock.calls[0][1].headers
    expect(headers.has('Authorization')).toBe(false)
  })

  it('uses one authentication invalidation handler for a 401 response', async () => {
    const onUnauthorized = vi.fn()
    const client = createApiClient({
      getToken: () => 'fictional-token-002',
      onUnauthorized,
      fetchImpl: vi.fn().mockResolvedValue(jsonResponse({ error: 'raw unauthorized response' }, 401))
    })

    await expect(client.request('/api/example')).rejects.toMatchObject({
      message: '登录已过期，请重新登录',
      status: 401
    })
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not append tokens or a complete sensitive response to API errors', async () => {
    const token = 'fictional-token-003'
    const password = 'fictional-password-003'
    const client = createApiClient({
      getToken: () => token,
      fetchImpl: vi.fn().mockResolvedValue(jsonResponse({
        error: `请求失败 ${token}`,
        details: '虚构周报正文和内部配置不应进入错误消息'
      }, 400))
    })

    const error = await client.request('/api/example', {
      method: 'POST',
      body: JSON.stringify({ password })
    }).catch(value => value)

    expect(error.message).toContain('请求失败')
    expect(error.message).not.toContain(token)
    expect(error.message).not.toContain(password)
    expect(error.message).not.toContain('虚构周报正文')
    expect(error.message).not.toContain('内部配置')
  })

  it('does not retain a failed login password in logs or exception text', async () => {
    const password = 'fictional-password-004'
    const consoleLog = vi.spyOn(console, 'log').mockImplementation(() => {})
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const client = createApiClient({
      fetchImpl: vi.fn().mockResolvedValue(jsonResponse({
        error: `登录失败：${password}`
      }, 401))
    })

    const error = await loginWithPassword(client, {
      username: 'test-user-004',
      password
    }).catch(value => value)

    expect(error.message).not.toContain(password)
    expect(consoleLog).not.toHaveBeenCalled()
    expect(consoleError).not.toHaveBeenCalled()
    consoleLog.mockRestore()
    consoleError.mockRestore()
  })
})

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}
