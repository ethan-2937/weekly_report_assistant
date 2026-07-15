function encodeRequired(value, label) {
  const normalized = typeof value === 'string' ? value.trim() : ''
  if (!normalized) throw new TypeError(`${label} is required`)
  return encodeURIComponent(normalized)
}

export function fetchWeeklyReportDetail(client, week, userId) {
  return client.request(
    `/api/weeks/${encodeRequired(week, 'week')}/reports/${encodeRequired(userId, 'userId')}`
  )
}

export function normalizeWeeklyReportDetail(value) {
  const source = value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  const fields = Array.isArray(source.fields)
    ? source.fields
      .filter(field => field && typeof field === 'object' && !Array.isArray(field))
      .map(field => ({ label: text(field.label), value: text(field.value) }))
      .filter(field => field.label)
    : []
  return {
    week: text(source.week),
    name: text(source.name),
    department: text(source.department),
    title: text(source.title),
    status: text(source.status),
    submittedAt: text(source.submittedAt),
    available: source.available === true,
    message: text(source.message),
    fields
  }
}

function text(value) {
  return typeof value === 'string' ? value : ''
}
