export function prepareReportBlocks(parsedBlocks) {
  return parsedBlocks.map(normalizeReportBlock).filter(Boolean)
}

function normalizeReportBlock(block) {
  if (block.type === 'list' || block.type === 'ordered-list') {
    const items = block.items
      .filter(item => !/确认团队汇总完整性/.test(item))
      .map(sanitizeDisplayText)
      .filter(Boolean)
    return items.length ? { ...block, items } : null
  }

  if (block.type === 'table') {
    const headers = block.headers.map(sanitizeDisplayText)
    const rows = block.rows
      .filter(row => !row.some(cell => /确认团队汇总完整性/.test(cell)))
      .map(row => row.map(sanitizeDisplayText))
    return { ...block, headers, rows }
  }

  if (/确认团队汇总完整性/.test(block.text || '')) return null
  const text = sanitizeDisplayText(block.text || '')
  if (!text) return null
  if (block.type === 'heading' && /(?:需|需要)老板拍板|协调事项/.test(text)) {
    return { ...block, text: '本周重点' }
  }
  return { ...block, text }
}

function sanitizeDisplayText(value) {
  return `${value}`
    .replace(/负责人候选/g, '负责人')
    .replace(/(access[_-]?token|appsecret|password|密码)(\s*[:=]\s*)\S+/gi, '$1$2[已隐藏]')
    .replace(/Bearer\s+\S+/gi, 'Bearer [已隐藏]')
    .replace(/\beyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, '[JWT已隐藏]')
    .trim()
}

export function buildSections(reportBlocks, variant) {
  if (variant !== 'report') {
    return [{ id: 'content', title: '', kicker: '', blocks: reportBlocks, collapsible: false, focus: false }]
  }

  const grouped = []
  let current = null
  reportBlocks.forEach((block) => {
    if (block.type === 'heading' && block.level <= 2) {
      current = createSection(grouped.length, block.text)
      grouped.push(current)
    }
    if (!current) {
      current = createSection(grouped.length, '')
      grouped.push(current)
    }
    current.blocks.push(block)
  })

  grouped.forEach((section) => {
    section.focusType = sectionFocusType(section.title)
    section.focus = Boolean(section.focusType)
    section.collapsible = isCollapsibleTitle(section.title)
    section.kicker = /负责人/.test(section.title) ? 'LEADERSHIP' : 'DETAILS'
    if (section.collapsible && section.blocks[0]?.type === 'heading') {
      section.blocks = section.blocks.slice(1)
    }
    if (section.focus || /AI.{0,8}红黑榜/.test(section.title)) {
      section.blocks = orderAiHighlights(section.blocks)
    }
  })

  const focusSections = grouped
    .filter(section => section.focus)
    .sort((left, right) => focusRank(left.focusType) - focusRank(right.focusType))
  if (!focusSections.length) return grouped

  const firstFocusIndex = grouped.findIndex(section => section.focus)
  const remaining = grouped.filter(section => !section.focus)
  const insertionIndex = grouped.slice(0, firstFocusIndex).filter(section => !section.focus).length
  remaining.splice(insertionIndex, 0, {
    id: 'weekly-focus',
    title: '本周重点',
    kicker: 'FOCUS',
    blocks: focusSections.flatMap(section => section.blocks),
    collapsible: false,
    focus: true,
    focusType: 'focus'
  })
  return remaining
}

function createSection(index, title) {
  return {
    id: `report-section-${index}`,
    title,
    kicker: '',
    blocks: [],
    collapsible: false,
    focus: false,
    focusType: ''
  }
}

function isCollapsibleTitle(title) {
  return /员工(?:五维)?评价|员工评价总表|团队负责人(?:履职)?(?:检查|评价)/.test(title)
}

function sectionFocusType(title) {
  if (/红黑榜/.test(title)) return ''
  if (/可复用|AI.{0,8}(?:红榜|亮点)|(?:红榜|亮点).{0,8}AI/.test(title)) return 'red'
  if (/本周重点|老板.{0,8}拍板|协调事项/.test(title)) return 'focus'
  if (/AI.{0,8}黑榜|黑榜.{0,8}AI/.test(title)) return 'black'
  return ''
}

function focusRank(type) {
  return { red: 0, focus: 1, black: 2 }[type] ?? 3
}

function orderAiHighlights(sectionBlocks) {
  return sectionBlocks.map((block) => {
    if (block.type === 'list' || block.type === 'ordered-list') {
      return { ...block, items: stableSortByAiTone(block.items) }
    }
    if (block.type === 'table') {
      return { ...block, rows: stableSortByAiTone(block.rows, row => row.join(' ')) }
    }
    return block
  })
}

function stableSortByAiTone(items, textOf = item => item) {
  return items
    .map((item, index) => ({ item, index, rank: aiToneRank(textOf(item)) }))
    .sort((left, right) => left.rank - right.rank || left.index - right.index)
    .map(entry => entry.item)
}

function aiToneRank(text) {
  if (/红榜|可复用|AI亮点/.test(text)) return 0
  if (/黑榜/.test(text)) return 2
  return 1
}
