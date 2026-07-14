export const PRIMARY_EVALUATION_DIMENSIONS = Object.freeze([
  Object.freeze({ key: 'outcomes', title: '虚实盘（本周成果）' }),
  Object.freeze({ key: 'timeAllocation', title: '时间分配健康度' }),
  Object.freeze({ key: 'aiUsage', title: 'AI使用红黑榜' }),
  Object.freeze({ key: 'nextWeekPlan', title: '下周计划合格性' })
])

export const DETAIL_EVALUATION_DIMENSION = Object.freeze({
  key: 'conclusion',
  title: '综合结论/需跟进'
})

export const ALL_EVALUATION_DIMENSIONS = Object.freeze([
  ...PRIMARY_EVALUATION_DIMENSIONS,
  DETAIL_EVALUATION_DIMENSION
])

export const WEEK_OVERVIEW_FIELDS = Object.freeze([
  'week',
  'expectedCount',
  'submittedCount',
  'missingCount',
  'leaderCandidateCount',
  'hasManagerReport',
  'generatedAt'
])

export const SUBMISSION_STATUS_FIELDS = Object.freeze({
  status: '提交状态',
  name: '姓名',
  userid: 'userid',
  department: '部门',
  leader: '是否负责人候选',
  title: '职务',
  reportDepartment: '周报部门',
  submittedAt: '提交时间',
  reportId: 'report_id',
  complianceRate: '模板填写正确率',
  complianceStatus: '模板合规状态',
  missingFields: '模板缺失项',
  presentFields: '模板命中项',
  complianceDetail: '模板检查说明'
})

export const WEEKLY_REPORT_DISPLAY_LABELS = Object.freeze({
  leader: '负责人',
  managementFocus: '本周重点'
})

const MANAGEMENT_FOCUS_HEADINGS = new Set([
  '需要老板拍板/协调事项',
  '需老板拍板/协调事项'
])

export function fetchWeeks(client) {
  return client.request('/api/weeks')
}

export function fetchWeekSummary(client, week) {
  return client.request(`/api/weeks/${encodeWeek(week)}/summary`)
}

export function fetchWeekAnalysis(client, week) {
  return client.request(`/api/weeks/${encodeWeek(week)}/analysis`)
}

export function fetchWeekSubmissionStatus(client, week) {
  return client.request(`/api/weeks/${encodeWeek(week)}/submission-status`)
}

export function evaluationDimensions({ includeDetail = false } = {}) {
  return includeDetail ? ALL_EVALUATION_DIMENSIONS : PRIMARY_EVALUATION_DIMENSIONS
}

export function displayReportHeading(heading) {
  const normalized = typeof heading === 'string' ? heading.trim() : ''
  return MANAGEMENT_FOCUS_HEADINGS.has(normalized)
    ? WEEKLY_REPORT_DISPLAY_LABELS.managementFocus
    : normalized
}

export function normalizeWeekOverview(value) {
  const source = asRecord(value)
  return {
    ...source,
    week: asText(source.week),
    expectedCount: asCount(source.expectedCount),
    submittedCount: asCount(source.submittedCount),
    missingCount: asCount(source.missingCount),
    leaderCandidateCount: asCount(source.leaderCandidateCount),
    hasManagerReport: source.hasManagerReport === true,
    generatedAt: asText(source.generatedAt)
  }
}

export function normalizeWeekSummary(value) {
  const source = asRecord(value)
  return {
    ...normalizeWeekOverview(source),
    submissionSummary: asText(source.submissionSummary),
    managerReport: asText(source.managerReport)
  }
}

export function normalizeWeekAnalysis(value) {
  const source = asRecord(value)
  return {
    ...source,
    week: asText(source.week),
    source: asText(source.source),
    content: asText(source.content),
    isManagerReport: source.isManagerReport === true
  }
}

export function normalizeSubmissionStatus(value) {
  if (!Array.isArray(value)) return []
  return value.map((item) => {
    const row = asRecord(item)
    return {
      ...row,
      [SUBMISSION_STATUS_FIELDS.missingFields]: asList(row[SUBMISSION_STATUS_FIELDS.missingFields]),
      [SUBMISSION_STATUS_FIELDS.presentFields]: asList(row[SUBMISSION_STATUS_FIELDS.presentFields])
    }
  })
}

function encodeWeek(week) {
  const normalized = asText(week).trim()
  if (!normalized) throw new TypeError('week is required')
  return encodeURIComponent(normalized)
}

function asRecord(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function asText(value) {
  return typeof value === 'string' ? value : ''
}

function asCount(value) {
  const count = Number(value)
  return Number.isFinite(count) && count >= 0 ? count : 0
}

function asList(value) {
  return Array.isArray(value) ? value : []
}
