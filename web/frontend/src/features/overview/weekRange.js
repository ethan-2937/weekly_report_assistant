const ISO_WEEK_PATTERN = /^(\d{4})-W(\d{2})$/i

function isLeapYear(year) {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
}

function weeksInIsoYear(year) {
  const januaryFirst = new Date(Date.UTC(year, 0, 1)).getUTCDay()
  return januaryFirst === 4 || (januaryFirst === 3 && isLeapYear(year)) ? 53 : 52
}

function formatDate(date, includeYear = false) {
  const year = date.getUTCFullYear()
  const month = date.getUTCMonth() + 1
  const day = date.getUTCDate()
  return includeYear ? `${year}年${month}月${day}日` : `${month}月${day}日`
}

export function getIsoWeekRange(week) {
  const match = ISO_WEEK_PATTERN.exec(String(week || '').trim())
  if (!match) return null

  const year = Number(match[1])
  const weekNumber = Number(match[2])
  if (weekNumber < 1 || weekNumber > weeksInIsoYear(year)) return null

  const januaryFourth = new Date(Date.UTC(year, 0, 4))
  const mondayOffset = (januaryFourth.getUTCDay() + 6) % 7
  const monday = new Date(Date.UTC(year, 0, 4 - mondayOffset + (weekNumber - 1) * 7))
  const sunday = new Date(monday)
  sunday.setUTCDate(monday.getUTCDate() + 6)

  return { monday, sunday }
}

export function formatIsoWeekRange(week) {
  const range = getIsoWeekRange(week)
  if (!range) return '日期范围待确认'

  const crossesYear = range.monday.getUTCFullYear() !== range.sunday.getUTCFullYear()
  return `${formatDate(range.monday, true)} - ${formatDate(range.sunday, crossesYear)}`
}
