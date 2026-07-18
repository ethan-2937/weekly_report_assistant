const TYPES = new Set(['SUNDAY_REMINDER', 'MONDAY_EVALUATION'])

export function sendNotificationTest(client, type, confirmRecipientName) {
  const normalizedType = typeof type === 'string' ? type.trim() : ''
  const normalizedName = typeof confirmRecipientName === 'string' ? confirmRecipientName.trim() : ''
  if (!TYPES.has(normalizedType)) throw new TypeError('notification test type is invalid')
  if (!normalizedName) throw new TypeError('notification test recipient is required')
  return client.request('/api/admin/notification-tests', {
    method: 'POST',
    body: JSON.stringify({ type: normalizedType, confirmRecipientName: normalizedName })
  })
}
