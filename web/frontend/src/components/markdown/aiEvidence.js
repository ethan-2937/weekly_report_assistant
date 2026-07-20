const AI_TEXT = /AI|人工智能|红榜|黑榜|可复用|未使用|无AI|工具|模型|场景|自动化|使用效果/i
const EMPLOYEE_SECTION = /员工(?:[四五]维)?评价|员工评价总表|授权范围内员工评价|每人工作总结|效果评价|虚实盘/
const AI_BLACK_TONE = /黑榜|未使用|无AI|不合格|需(?:改进|补充|明确)|建议(?:补充|明确)|未(?:说明|写明|体现|提供).*(?:工具|模型|场景|效果|产出)|(?:工具|模型|场景|效果|产出).*(?:缺少|不明|不清晰|不具体)/

export function collectAiEvidence(sections, tone, personDetails = new Map(), people = []) {
  const evidence = []
  const coveredPeople = new Set()
  const matchesTone = tone === 'red'
    ? text => /红榜|可复用|AI亮点/.test(text)
    : text => AI_BLACK_TONE.test(text)

  for (const section of sections.filter(item => !item.focus)) {
    for (const block of section.blocks) {
      if (block.type !== 'table') continue
      const aiColumn = block.headers.findIndex(header => /AI.*(?:红黑榜|使用|应用)/i.test(header))
      if (aiColumn < 0) continue
      const personColumn = block.headers.findIndex(header => /姓名|人员|员工|负责人/.test(header))
      const departmentColumn = block.headers.findIndex(header => /部门|团队|管理团队/.test(header))
      const titleColumn = block.headers.findIndex(header => /职务|职位|岗位|title/i.test(header))
      for (const row of block.rows) {
        const conclusion = cleanAiLabel(row[aiColumn] || '')
        if (!matchesTone(conclusion)) continue
        const person = personColumn >= 0 ? row[personColumn] : ''
        const profile = [row[departmentColumn] || '', row[titleColumn] || ''].filter(Boolean)
        evidence.push(formatEvidence(person, profile, conclusion, personDetails, people))
        if (person) coveredPeople.add(normalizeName(person))
      }
    }
  }

  for (const section of sections.filter(item => EMPLOYEE_SECTION.test(item.title))) {
    let person = ''
    for (const block of section.blocks) {
      if (block.type === 'heading' && block.level >= 3) {
        person = `${block.text || ''}`.trim()
        continue
      }
      if (!person || coveredPeople.has(normalizeName(person))) continue
      const conclusion = aiTextParts(block).map(cleanAiLabel).find(matchesTone)
      if (!conclusion) continue
      evidence.push(formatEvidence(person, [], conclusion, personDetails, people))
      coveredPeople.add(normalizeName(person))
    }
  }
  return [...new Set(evidence.filter(Boolean))]
}

export function collectPersonDetails(sections) {
  const details = new Map()
  for (const section of sections.filter(item => EMPLOYEE_SECTION.test(item.title))) {
    let person = ''
    for (const block of section.blocks) {
      if (block.type === 'heading' && block.level >= 3) {
        person = `${block.text || ''}`.trim()
        continue
      }
      if (!person) continue
      for (const text of aiTextParts(block)) {
        const previous = details.get(person) || ''
        details.set(person, (previous ? `${previous}；${text}` : text).slice(0, 240))
      }
    }
  }
  return details
}

function formatEvidence(person, explicitProfile, conclusion, details, people) {
  const identity = resolveIdentity(person, explicitProfile, people)
  const detail = person ? findPersonDetail(details, person) : ''
  const detailSuffix = detail && !detail.includes(conclusion) ? `；具体内容：${detail}` : ''
  return identity ? `${identity}：${conclusion}${detailSuffix}` : conclusion
}

function resolveIdentity(value, explicitProfile, people) {
  const source = `${value || ''}`.trim()
  if (!source) return ''
  const explicitMatch = source.match(/^([^（(｜|]+)(?:[（(](.+)[）)])?(?:[｜|](.+))?$/)
  const name = (explicitMatch?.[1] || source).trim()
  const embedded = [explicitMatch?.[2], explicitMatch?.[3]].filter(Boolean).join('｜')
  const candidates = people.filter(person => `${person?.name || ''}`.trim() === name)
  const fallback = candidates.length === 1
    ? [candidates[0].department, candidates[0].title].filter(Boolean).join('｜')
    : ''
  const profile = explicitProfile.filter(Boolean).join('｜') || embedded || fallback
  return profile ? `${name}（${profile}）` : name
}

function aiTextParts(block) {
  const values = ['list', 'ordered-list'].includes(block.type)
    ? block.items || []
    : ['paragraph', 'quote'].includes(block.type) ? [block.text || ''] : []
  return values.map(value => `${value}`.replace(/\s+/g, ' ').trim()).filter(value => AI_TEXT.test(value))
}

function cleanAiLabel(value) {
  return `${value}`.replace(/^AI\s*(?:使用|应用)?(?:及效果|红黑榜)?\s*[:：]\s*/i, '').trim()
}

function normalizeName(value) {
  return `${value}`.trim().replace(/[（(｜|].*$/, '').trim()
}

function findPersonDetail(details, person) {
  const normalized = normalizeName(person)
  for (const [name, detail] of details.entries()) {
    if (normalizeName(name) === normalized) return detail
  }
  return ''
}
