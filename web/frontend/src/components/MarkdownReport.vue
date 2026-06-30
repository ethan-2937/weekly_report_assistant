<template>
  <article :class="['markdown-report', `markdown-report--${variant}`]">
    <div v-if="!content" class="report-empty">
      {{ emptyText }}
    </div>

    <template v-else>
      <section
        v-for="(block, index) in blocks"
        :key="`${block.type}-${index}`"
        :class="['report-block', `report-block--${block.type}`, block.level ? `level-${block.level}` : '', blockTone(block)]"
      >
        <component v-if="block.type === 'heading'" :is="headingTag(block.level)" class="report-heading">
          <span class="heading-kicker">{{ headingLabel(block.level) }}</span>
          <span class="heading-text"><InlineText :parts="inlineParts(block.text)" /></span>
        </component>

        <p v-else-if="block.type === 'paragraph'" class="report-paragraph">
          <InlineText :parts="inlineParts(block.text)" />
        </p>

        <blockquote v-else-if="block.type === 'quote'" class="report-quote">
          <InlineText :parts="inlineParts(block.text)" />
        </blockquote>

        <ul v-else-if="block.type === 'list'" class="report-list">
          <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
            <span class="list-dot"></span>
            <span><InlineText :parts="inlineParts(item)" /></span>
          </li>
        </ul>

        <ol v-else-if="block.type === 'ordered-list'" class="report-ordered-list">
          <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
            <InlineText :parts="inlineParts(item)" />
          </li>
        </ol>

        <div v-else-if="block.type === 'table'" class="report-table-wrap">
          <table class="report-table">
            <thead>
              <tr>
                <th v-for="(header, headerIndex) in block.headers" :key="headerIndex">
                  <InlineText :parts="inlineParts(header)" />
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                <td
                  v-for="(cell, cellIndex) in block.headers"
                  :key="cellIndex"
                  :class="cellClass(row[cellIndex] || '', cell)"
                >
                  <InlineText :parts="inlineParts(row[cellIndex] || '')" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <pre v-else-if="block.type === 'code'" class="report-code"><code>{{ block.text }}</code></pre>
      </section>
    </template>
  </article>
</template>

<script setup>
import { computed, defineComponent, h } from 'vue'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  emptyText: {
    type: String,
    default: '暂无内容。'
  },
  variant: {
    type: String,
    default: 'default'
  }
})

const InlineText = defineComponent({
  name: 'InlineText',
  props: {
    parts: {
      type: Array,
      required: true
    }
  },
  setup(componentProps) {
    return () => componentProps.parts.map((part, index) => {
      if (part.type === 'strong') return h('strong', { key: index }, part.text)
      if (part.type === 'code') return h('code', { key: index }, part.text)
      if (part.type === 'link') {
        return h('a', { key: index, href: part.href, target: '_blank', rel: 'noreferrer' }, part.text)
      }
      return h('span', { key: index }, part.text)
    })
  }
})

const blocks = computed(() => parseMarkdown(props.content))

function headingTag(level) {
  return level <= 2 ? 'h2' : 'h3'
}

function headingLabel(level) {
  if (level <= 1) return 'REPORT'
  if (level === 2) return 'SECTION'
  return 'DETAIL'
}

function blockTone(block) {
  const text = `${block.text || ''} ${(block.items || []).join(' ')}`
  if (/老板|拍板|协调事项|需要支持|需支持|求助|卡点/.test(text)) return 'tone-boss'
  if (/五维|虚实盘|真干活|时间分配|健康度|红黑榜|下周计划合格|综合结论/.test(text)) return 'tone-dimension'
  if (/AI|可复用|红榜|黑榜/.test(text)) return 'tone-ai'
  if (/风险|异常|失败|未提交|缺失|阻塞|延期|严重|离职|淘汰/.test(text)) return 'tone-danger'
  if (/下一步|待启动|待确认|需要|建议|关注|观察|复核|人工确认/.test(text)) return 'tone-warning'
  if (/已完成|已提交|成功|达成|通过|正式评价|完成/.test(text)) return 'tone-success'
  if (/进行中|生成|Codex|分析|汇总/.test(text)) return 'tone-info'
  return ''
}

function cellClass(value, header) {
  const text = `${value}`
  const headerText = `${header}`
  const classes = ['report-cell']
  if (/状态|进度|结果|是否|风险|备注|负责人|候选|提交|虚实盘|健康度|红黑榜|计划|结论|跟进/.test(headerText)) {
    classes.push('is-key-field')
  }
  if (/老板|拍板|协调|支持|求助/.test(headerText + text)) {
    classes.push('is-boss')
  } else if (/虚实盘|本周成果|产出/.test(headerText)) {
    classes.push('is-output')
  } else if (/时间分配|健康度|工时/.test(headerText)) {
    classes.push('is-time-health')
  } else if (/AI|红黑榜/.test(headerText)) {
    classes.push('is-ai')
  } else if (/下周计划|计划合格/.test(headerText)) {
    classes.push('is-plan')
  }

  const tone = statusTone(text)
  if (tone) {
    classes.push(`is-${tone}`)
  } else if (/负责人|责任人|owner/i.test(headerText)) {
    classes.push('is-owner')
  }
  return classes
}

function statusTone(value) {
  const text = `${value}`.replace(/\s+/g, '')
  if (!text) return ''
  if (/无明显(风险|异常|阻塞|问题)|未见(明显)?(风险|异常|阻塞|问题)|风险可控|时间分配健康|计划清晰/.test(text)) {
    return 'success'
  }
  if (/不合格|未完成|未达成|无产出|无交付|只有动作|未提交|异常|失败|缺失|严重|延期|阻塞|黑榜|未使用|无AI|无明确产出|无具体产出/.test(text)) {
    return 'danger'
  }
  if (/需改进|需要改进|基本完成|待完善|待确认|待启动|观察中|关注|复核|候选|偏高|偏低|不清晰|不明确|不充分|较弱|一般|模糊|继续/.test(text)) {
    return 'warning'
  }
  if (/进行中|处理中|已生成|生成中/.test(text)) {
    return 'progress'
  }
  if (/完成较好|完成|合格|优秀|较好|已提交|已完成|成功|达成|通过|正常|健康|清晰|红榜|可复用/.test(text)) {
    return 'success'
  }
  return ''
}

function parseMarkdown(content) {
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
    if (paragraph.length) {
      result.push({ type: 'paragraph', text: paragraph.join(' ') })
    } else {
      index += 1
    }
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

function inlineParts(text) {
  const parts = []
  const pattern = /(\[[^\]]+\]\([^)]+\)|`[^`]+`|\*\*[^*]+\*\*)/g
  let lastIndex = 0
  let match

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ type: 'text', text: text.slice(lastIndex, match.index) })
    }
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

  if (lastIndex < text.length) {
    parts.push({ type: 'text', text: text.slice(lastIndex) })
  }
  return parts.length ? parts : [{ type: 'text', text }]
}
</script>
