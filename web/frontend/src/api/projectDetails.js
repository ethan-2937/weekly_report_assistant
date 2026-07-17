function encodeWeek(week) {
  const normalized = typeof week === 'string' ? week.trim() : ''
  if (!normalized) throw new TypeError('week is required')
  return encodeURIComponent(normalized)
}

export function fetchProjectDetails(client, week) {
  return client.request(`/api/weeks/${encodeWeek(week)}/project-details`)
}

export function normalizeProjectDetails(value) {
  if (!Array.isArray(value)) return []
  return value.map((item, index) => {
    const row = item && typeof item === 'object' && !Array.isArray(item) ? item : {}
    const sequence = Number(row.sequence)
    return {
      sequence: Number.isInteger(sequence) && sequence > 0 ? sequence : index + 1,
      productLine: text(row.productLine),
      customerName: text(row.customerName),
      projectName: text(row.projectName),
      investedDays: text(row.investedDays),
      travelExpense: text(row.travelExpense),
      hospitalityExpense: text(row.hospitalityExpense)
    }
  })
}

function text(value) {
  return typeof value === 'string' ? value : ''
}
