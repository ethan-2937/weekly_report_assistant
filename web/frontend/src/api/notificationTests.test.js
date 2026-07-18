import { describe, expect, it, vi } from 'vitest'
import { sendNotificationTest } from './notificationTests.js'

describe('notification test API', () => {
  it('sends only the selected type and explicit recipient confirmation', async () => {
    const client = { request: vi.fn().mockResolvedValue({ delivered: true }) }

    await sendNotificationTest(client, 'SUNDAY_REMINDER', '测试接收人')

    expect(client.request).toHaveBeenCalledWith('/api/admin/notification-tests', {
      method: 'POST',
      body: JSON.stringify({
        type: 'SUNDAY_REMINDER',
        confirmRecipientName: '测试接收人'
      })
    })
  })

  it('rejects unsupported types before making a request', () => {
    const client = { request: vi.fn() }

    expect(() => sendNotificationTest(client, 'ALL_USERS', '测试接收人')).toThrow(TypeError)
    expect(client.request).not.toHaveBeenCalled()
  })
})
