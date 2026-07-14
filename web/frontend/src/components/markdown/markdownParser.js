export function parseMarkdown(content) {
  const lines = content.replace(/\r\n/g, '\n').split('\n')
  const result = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    if (!line.trim()) {
      index += 1
      continue
    }

    if (line.trim().startsWith('```')) {
      const code = []
      index += 1
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        code.push(lines[index])
        index += 1
      }
      if (index < lines.length) index += 1
      result.push({ type: 'code', text: code.join('\n') })
      continue
    }

    if (isTableStart(lines, index)) {
      const tableLines = []
      while (index < lines.length && /^\s*\|/.test(lines[index])) {
        tableLines.push(lines[index])
        index += 1
      }
      const parsed = parseTable(tableLines)
      if (parsed) result.push(parsed)
      continue
    }

    const headingMatch = line.match(/^(#{1,4})\s+(.+)$/)
    if (headingMatch) {
      result.push({ type: 'heading', level: headingMatch[1].length, text: cleanText(headingMatch[2]) })
      index += 1
      continue
    }

    if (/^\s*[-*]\s+/.test(line)) {
      const items = []
      while (index < lines.length && /^\s*[-*]\s+/.test(lines[index])) {
        items.push(cleanText(lines[index].replace(/^\s*[-*]\s+/, '')))
        index += 1
      }
      result.push({ type: 'list', items })
      continue
    }

    if (/^\s*\d+\.\s+/.test(line)) {
      const items = []
      while (index < lines.length && /^\s*\d+\.\s+/.test(lines[index])) {
        items.push(cleanText(lines[index].replace(/^\s*\d+\.\s+/, '')))
        index += 1
      }
      result.push({ type: 'ordered-list', items })
      continue
    }

    if (/^>\s+/.test(line)) {
      const quote = []
      while (index < lines.length && /^>\s+/.test(lines[index])) {
        quote.push(cleanText(lines[index].replace(/^>\s+/, '')))
        index += 1
      }
      result.push({ type: 'quote', text: quote.join(' ') })
      continue
    }

    const paragraph = []
    while (index < lines.length && lines[index].trim() && !isSpecialBlock(lines, index)) {
      paragraph.push(cleanText(lines[index]))
      index += 1
    }
    if (paragraph.length) result.push({ type: 'paragraph', text: paragraph.join(' ') })
    else index += 1
  }

  return result
}

function isSpecialBlock(lines, index) {
  const line = lines[index]
  return line.trim().startsWith('```')
    || /^(#{1,4})\s+/.test(line)
    || /^\s*[-*]\s+/.test(line)
    || /^\s*\d+\.\s+/.test(line)
    || /^>\s+/.test(line)
    || isTableStart(lines, index)
}

function isTableStart(lines, index) {
  return /^\s*\|/.test(lines[index] || '') && /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(lines[index + 1] || '')
}

function parseTable(tableLines) {
  if (tableLines.length < 2) return null
  const headers = splitTableRow(tableLines[0])
  const rows = tableLines.slice(2).map(splitTableRow).filter(row => row.some(Boolean))
  return { type: 'table', headers, rows }
}

function splitTableRow(line) {
  return line.trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map(cell => cleanText(cell.trim()))
}

function cleanText(text) {
  return text.replace(/`([^`]+)`/g, '`$1`').trim()
}

export function inlineParts(text) {
  const parts = []
  const pattern = /(\[[^\]]+\]\([^)]+\)|`[^`]+`|\*\*[^*]+\*\*)/g
  let lastIndex = 0
  let match

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) parts.push({ type: 'text', text: text.slice(lastIndex, match.index) })
    const token = match[0]
    if (token.startsWith('**')) {
      parts.push({ type: 'strong', text: token.slice(2, -2) })
    } else if (token.startsWith('`')) {
      parts.push({ type: 'code', text: token.slice(1, -1) })
    } else {
      const linkMatch = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
      parts.push({ type: 'link', text: linkMatch?.[1] || token, href: linkMatch?.[2] || '#' })
    }
    lastIndex = pattern.lastIndex
  }

  if (lastIndex < text.length) parts.push({ type: 'text', text: text.slice(lastIndex) })
  return parts.length ? parts : [{ type: 'text', text }]
}
