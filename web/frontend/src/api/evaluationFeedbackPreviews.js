const WEEK_PATTERN = /^\d{4}-W\d{2}$/

export async function fetchEvaluationFeedbackPreview(client, week) {
  const normalizedWeek = typeof week === 'string' ? week.trim() : ''
  if (!WEEK_PATTERN.test(normalizedWeek)) throw new TypeError('周次格式应为 YYYY-Www')
  const data = await client.request(
    `/api/admin/evaluation-feedback-previews/${encodeURIComponent(normalizedWeek)}`
  )
  return normalizeEvaluationFeedbackPreview(data)
}

export function normalizeEvaluationFeedbackPreview(data) {
  const source = data && typeof data === 'object' ? data : {}
  const notifications = Array.isArray(source.notifications)
    ? source.notifications.slice(0, 300).map(item => ({
        name: text(item?.name),
        department: text(item?.department),
        title: text(item?.title),
        markdown: text(item?.markdown)
      }))
    : []
  return {
    week: text(source.week),
    phase: text(source.phase),
    eligibleCount: count(source.eligibleCount),
    sentCount: count(source.sentCount),
    updatedAt: text(source.updatedAt),
    exactMatch: source.exactMatch === true,
    verificationMode: text(source.verificationMode),
    warning: text(source.warning),
    notifications
  }
}

function text(value) {
  return typeof value === 'string' ? value : ''
}

function count(value) {
  const number = Number(value)
  return Number.isInteger(number) && number >= 0 ? number : 0
}
