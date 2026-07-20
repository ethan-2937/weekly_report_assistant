<template>
  <article :class="['markdown-report', `markdown-report--${variant}`]">
    <div v-if="!content" class="report-empty">
      {{ emptyText }}
    </div>

    <template v-else>
      <section
        v-for="section in sections"
        :key="section.id"
        :class="['report-section-group', { 'report-section-group--focus': section.focus, 'is-collapsed': section.collapsible && !isOpen(section.id) }]"
        :aria-label="section.focus ? '本周重点' : undefined"
      >
        <div v-if="section.collapsible" class="report-section-toolbar">
          <button
            type="button"
            class="report-section-toggle"
            :aria-expanded="isOpen(section.id)"
            @click="toggleSection(section.id)"
          >
            <span>
              <small>{{ section.kicker }}</small>
              <strong>{{ section.title }}</strong>
            </span>
            <span class="report-section-toggle__action">
              <span class="report-section-toggle__arrow" aria-hidden="true"></span>
              {{ isOpen(section.id) ? '收起' : '展开全部' }}
            </span>
          </button>
          <BrowserDownloadHint v-if="canExport(section)">
            <button
              type="button"
              class="report-section-export"
              :aria-label="`导出${section.title}为 Excel`"
              @click="exportSection(section)"
            >
              <span aria-hidden="true">↓</span>
              导出 Excel
            </button>
          </BrowserDownloadHint>
        </div>

        <div
          :class="['report-section-content', { 'is-preview': section.collapsible && !isOpen(section.id) }]"
          :aria-label="section.collapsible && !isOpen(section.id) ? `${section.title}内容预览` : undefined"
        >
          <section
            v-for="(block, index) in section.blocks"
            :key="`${section.id}-${block.type}-${index}`"
            :class="['report-block', `report-block--${block.type}`, block.level ? `level-${block.level}` : '', blockTone(block)]"
          >
        <component v-if="block.type === 'heading'" :is="headingTag(block.level)" class="report-heading">
          <span class="heading-kicker">{{ headingLabel(block.level) }}</span>
          <span class="heading-text"><ReportInlineText :parts="inlineParts(block.text)" :people="people" @person-select="selectPerson" /></span>
        </component>

        <p v-else-if="block.type === 'paragraph'" class="report-paragraph">
          <ReportInlineText :parts="inlineParts(block.text)" :people="people" @person-select="selectPerson" />
        </p>

        <blockquote v-else-if="block.type === 'quote'" class="report-quote">
          <ReportInlineText :parts="inlineParts(block.text)" :people="people" @person-select="selectPerson" />
        </blockquote>

        <ul v-else-if="block.type === 'list'" class="report-list">
          <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
            <span class="list-dot"></span>
            <span><ReportInlineText :parts="inlineParts(item)" :people="people" @person-select="selectPerson" /></span>
          </li>
        </ul>

        <ol v-else-if="block.type === 'ordered-list'" class="report-ordered-list">
          <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
            <ReportInlineText :parts="inlineParts(item)" :people="people" @person-select="selectPerson" />
          </li>
        </ol>

        <div
          v-else-if="block.type === 'ai-ranking'"
          :class="['ai-ranking-card', `ai-ranking-card--${block.tone}`]"
        >
          <header class="ai-ranking-card__header">
            <button
              type="button"
              class="ai-ranking-toggle"
              :aria-expanded="isRankingOpen(block.id)"
              :aria-controls="`${block.id}-content`"
              @click="toggleRanking(block.id)"
            >
              <span class="ai-ranking-card__mark" aria-hidden="true">{{ block.tone === 'red' ? '★' : '!' }}</span>
              <span class="ai-ranking-card__heading">
                <strong>{{ block.text }}</strong>
                <small>{{ block.tone === 'red' ? '方法清楚、成效明确、具备复用价值' : '描述空泛、缺少证据或未说明实际效果' }}</small>
              </span>
              <span class="ai-ranking-card__count">{{ block.items.length }} 条</span>
              <span class="ai-ranking-card__action">
                {{ isRankingOpen(block.id) ? '收起' : '展开' }}
                <span class="ai-ranking-card__arrow" aria-hidden="true"></span>
              </span>
            </button>
          </header>
          <div
            v-show="isRankingOpen(block.id)"
            :id="`${block.id}-content`"
            class="ai-ranking-card__content"
          >
          <ol v-if="block.items.length" class="ai-ranking-list">
            <li
              v-for="(entry, itemIndex) in aiRankingEntries(block)"
              :key="itemIndex"
              :class="{ 'has-person': entry.name }"
            >
              <div v-if="entry.name" class="ai-ranking-person">
                <span class="ai-ranking-index">{{ `${itemIndex + 1}`.padStart(2, '0') }}</span>
                 <strong class="ai-ranking-name"><ReportInlineText :parts="inlineParts(entry.name)" :people="people" @person-select="selectPerson" /></strong>
                <span v-for="meta in entry.meta" :key="meta" class="ai-ranking-meta">
                   <ReportInlineText :parts="inlineParts(meta)" :people="people" @person-select="selectPerson" />
                </span>
              </div>
              <div class="ai-ranking-row">
                <span class="ai-ranking-label">{{ block.tone === 'red' ? '入选结论' : '问题结论' }}</span>
                 <span class="ai-ranking-copy"><ReportInlineText :parts="inlineParts(entry.conclusion)" :people="people" @person-select="selectPerson" /></span>
              </div>
              <div v-if="entry.detail" class="ai-ranking-row ai-ranking-row--detail">
                <span class="ai-ranking-label">具体内容</span>
                 <span class="ai-ranking-copy"><ReportInlineText :parts="inlineParts(entry.detail)" :people="people" @person-select="selectPerson" /></span>
              </div>
            </li>
          </ol>
          <p v-else class="ai-ranking-empty">{{ block.emptyText || (block.tone === 'red' ? '本周暂无 AI 红榜条目' : '本周暂无 AI 黑榜条目') }}</p>
          </div>
        </div>

        <div v-else-if="block.type === 'table'" class="report-table-wrap">
          <table class="report-table">
            <thead>
              <tr>
                <th v-for="(header, headerIndex) in block.headers" :key="headerIndex">
                  <ReportInlineText :parts="inlineParts(header)" :people="people" @person-select="selectPerson" />
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
                  <ReportInlineText :parts="inlineParts(row[cellIndex] || '')" :people="people" @person-select="selectPerson" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>

            <pre v-else-if="block.type === 'code'" class="report-code"><code>{{ block.text }}</code></pre>
          </section>
        </div>
      </section>
    </template>
  </article>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import BrowserDownloadHint from './BrowserDownloadHint.vue'
import ReportInlineText from './ReportInlineText.vue'
import { inlineParts, parseMarkdown } from './markdown/markdownParser.js'
import { buildSections, prepareReportBlocks } from './markdown/reportSections.js'
import { downloadXlsx, sectionBlocksToRows } from './markdown/xlsxExport.js'

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
  },
  downloadPrefix: {
    type: String,
    default: '周报评价'
  },
  people: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['person-select'])

const blocks = computed(() => prepareReportBlocks(parseMarkdown(props.content)))
const sections = computed(() => buildSections(blocks.value, props.variant, props.people))
const openSections = ref(new Set())
const collapsedRankings = ref(new Set())

watch(() => props.content, () => {
  openSections.value = new Set()
  collapsedRankings.value = new Set()
})

function isOpen(sectionId) {
  return openSections.value.has(sectionId)
}

function toggleSection(sectionId) {
  const next = new Set(openSections.value)
  if (next.has(sectionId)) next.delete(sectionId)
  else next.add(sectionId)
  openSections.value = next
}

function canExport(section) {
  return section.blocks.some(block => ['table', 'list', 'ordered-list', 'paragraph', 'quote'].includes(block.type))
}

function exportSection(section) {
  downloadXlsx({
    filename: `${props.downloadPrefix}-${section.title}`,
    sheetName: section.title,
    rows: sectionBlocksToRows(section.blocks)
  })
}

function selectPerson(candidates) {
  emit('person-select', candidates)
}

function headingTag(level) {
  return level <= 2 ? 'h2' : 'h3'
}

function headingLabel(level) {
  if (level <= 1) return 'REPORT'
  if (level === 2) return 'SECTION'
  return 'DETAIL'
}
function isRankingOpen(rankingId) {
  return !collapsedRankings.value.has(rankingId)
}

function toggleRanking(rankingId) {
  const next = new Set(collapsedRankings.value)
  if (next.has(rankingId)) next.delete(rankingId)
  else next.add(rankingId)
  collapsedRankings.value = next
}

function aiRankingEntries(block) {
  return block.items.map(item => parseAiRankingEntry(item, block.tone))
}

function parseAiRankingEntry(item, tone) {
  const source = `${item}`.trim()
  const detailMatch = source.match(/^(.*?)[；;]\s*具体内容\s*[:：]\s*(.+)$/)
  const summarySource = (detailMatch?.[1] || source).trim()
  const detail = (detailMatch?.[2] || '').trim()
  const identityMatch = summarySource.match(/^([^:：]+(?:（[^）]+）)?)\s*[:：]\s*(.+)$/)
  const identityCandidate = (identityMatch?.[1] || '').trim()
  const hasIdentity = identityMatch && !/^(?:AI\s*)?(?:红榜|黑榜|亮点)$/.test(identityCandidate)
  const identity = hasIdentity ? identityCandidate : ''
  const conclusionSource = hasIdentity ? identityMatch[2] : summarySource
  const conclusion = conclusionSource
    .replace(new RegExp(`^(?:AI\\s*)?${tone === 'red' ? '(?:红榜|亮点)' : '黑榜'}\\s*[:：]\\s*`), '')
    .trim()
  const profileMatch = identity.match(/^(.*?)(?:（(.+)）)?$/)
  return {
    name: (profileMatch?.[1] || identity).trim(),
    meta: (profileMatch?.[2] || '').split('｜').map(value => value.trim()).filter(Boolean),
    conclusion: conclusion || summarySource,
    detail
  }
}

function blockTone(block) {
  const text = `${block.text || ''} ${(block.items || []).join(' ')}`
  if (/老板|拍板|协调事项|需要支持|需支持|求助|卡点/.test(text)) return 'tone-boss'
  if (/五维|虚实盘|真干活|时间分配|健康度|红黑榜|下周计划合格|综合结论/.test(text)) return 'tone-dimension'
  if (/黑榜/.test(text)) return 'tone-ai-black'
  if (/AI|可复用|红榜/.test(text)) return 'tone-ai'
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
  if (/不合格\s*[（(]下属未提交[）)]/.test(text)) {
    classes.push('is-subordinate-missing')
  }
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
    if (/红榜|可复用/.test(text)) classes.push('is-ai-red')
    if (/黑榜|未使用|无AI/.test(text)) classes.push('is-ai-black')
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

</script>

<style scoped src="./markdown/MarkdownReport.css"></style>
<style scoped src="./markdown/AiRanking.css"></style>
