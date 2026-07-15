export function matchPeople(text, people) {
  const source = `${text ?? ''}`
  const groups = groupPeople(people)
  if (!source || groups.size === 0) return [{ type: 'text', text: source }]

  const names = [...groups.keys()].sort((left, right) => right.length - left.length)
  const pattern = new RegExp(names.map(escapeRegExp).join('|'), 'g')
  const matches = []
  let cursor = 0
  let match
  while ((match = pattern.exec(source)) !== null) {
    if (match.index > cursor) {
      matches.push({ type: 'text', text: source.slice(cursor, match.index) })
    }
    matches.push({
      type: 'person',
      text: match[0],
      candidates: groups.get(match[0])
    })
    cursor = match.index + match[0].length
  }
  if (cursor < source.length) matches.push({ type: 'text', text: source.slice(cursor) })
  return matches.length ? matches : [{ type: 'text', text: source }]
}

function groupPeople(people) {
  const groups = new Map()
  for (const person of Array.isArray(people) ? people : []) {
    const name = typeof person?.name === 'string' ? person.name.trim() : ''
    const userId = typeof person?.userId === 'string' ? person.userId.trim() : ''
    if (!name || !userId || name.length > 64) continue
    const candidates = groups.get(name) || []
    if (!candidates.some(candidate => candidate.userId === userId)) {
      candidates.push({ ...person, name, userId })
    }
    groups.set(name, candidates)
  }
  return groups
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
