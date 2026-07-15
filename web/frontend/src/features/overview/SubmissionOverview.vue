<template>
  <section class="submission-overview">
    <nav class="week-selector" aria-label="选择周次">
      <button
        v-for="item in weeks"
        :key="item.week"
        type="button"
        :class="{ active: selectedWeek === item.week }"
        :aria-current="selectedWeek === item.week ? 'true' : undefined"
        :title="weekRangeLabelFor(item.week)"
        :aria-label="`${item.week}，${weekRangeLabelFor(item.week)}`"
        @click="emit('select-week', item.week)"
      >
        <span class="week-selector__week">{{ item.week }}</span>
        <span class="week-selector__range">{{ weekRangeLabelFor(item.week) }}</span>
      </button>
      <span v-if="weeks.length === 0" class="empty-weeks">暂无周次数据，请先生成一次。</span>
    </nav>

    <div class="overview-card">
      <header class="overview-header">
        <div>
          <span class="eyebrow">SUBMISSION PULSE</span>
          <h1>提交概览</h1>
          <div class="week-context">
            <strong>{{ selectedWeek || '未选择周次' }}</strong>
            <span aria-hidden="true">·</span>
            <span>{{ weekRangeLabel }}</span>
            <span class="week-help" tabindex="0" aria-describedby="week-range-help">
              <span aria-hidden="true">?</span>
              <span id="week-range-help" class="week-tooltip" role="tooltip">
                业务周期按完整 ISO 周统计（周一至周日）；提交归属窗口为该周周四 00:00 至下一周周四 00:00 前，时区为 Asia/Shanghai。
              </span>
            </span>
          </div>
          <p class="mobile-week-rule">
            业务周期为周一至周日；提交归属窗口为周四 00:00 至下一周周四 00:00 前（Asia/Shanghai）。
          </p>
          <p class="generated-at">数据生成时间 {{ formattedGeneratedAt }}</p>
        </div>
        <button class="download-button" type="button" :disabled="!selectedWeek" @click="emit('download')">
          下载提交表
        </button>
      </header>

      <section class="evaluation-rules" aria-labelledby="evaluation-rules-title">
        <header class="evaluation-rules__header">
          <div>
            <span class="eyebrow">EVALUATION GUIDE</span>
            <h2 id="evaluation-rules-title">评价规则</h2>
          </div>
          <span class="rules-separation">模板合规 ≠ 管理评价</span>
        </header>

        <div class="evaluation-rules__grid">
          <article class="rule-track rule-track--template">
            <span class="rule-track__index">A</span>
            <div>
              <strong>模板合规</strong>
              <p>仅检查钉钉线上四项字段是否完整，不替代管理结论。</p>
              <div class="rule-chip-list" aria-label="模板合规字段">
                <span>本周成果</span><span>工时投入</span><span>AI 应用</span><span>下周计划</span>
              </div>
            </div>
          </article>

          <article class="rule-track rule-track--management">
            <span class="rule-track__index">B</span>
            <div>
              <strong>管理评价 · 四个主维度</strong>
              <p>综合结论与需跟进仍保留在详情，不作为独立主维度。</p>
              <ol class="management-dimensions">
                <li v-for="rule in managementRules" :key="rule.title">
                  <b>{{ rule.title }}</b><span>{{ rule.detail }}</span>
                </li>
              </ol>
            </div>
          </article>
        </div>
      </section>

      <div class="overview-content">
        <section class="content-panel compliance-panel" aria-labelledby="compliance-title">
          <div class="panel-title">
            <div>
              <h2 id="compliance-title">已提交名单 · 模板填写正确率</h2>
              <p>按本周完成成果、工时投入分析、AI应用及效果、下周计划四项核对。</p>
            </div>
            <span :class="['average-rate', rateClass(averageRate)]">
              平均 {{ averageRate === null ? '-' : `${averageRate}%` }}
            </span>
          </div>

          <div v-if="submittedRows.length" class="table-scroll">
            <table class="compliance-table">
              <thead>
                <tr>
                  <th>姓名</th>
                  <th>部门</th>
                  <th>提交时间</th>
                  <th>模板填写正确率</th>
                  <th>合规状态</th>
                  <th>缺失项 / 说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in submittedRows" :key="row.report_id || row.userid || index">
                  <td class="person-cell">{{ row['姓名'] || '-' }}</td>
                  <td>{{ row['部门'] || '-' }}</td>
                  <td>{{ row['提交时间'] || '-' }}</td>
                  <td>
                    <div class="rate-cell">
                      <strong>{{ displayRate(row) }}</strong>
                      <span
                        class="rate-track"
                        role="progressbar"
                        :aria-label="`${row['姓名'] || '该成员'}模板填写正确率`"
                        :aria-valuenow="normalizedRate(row) ?? 0"
                        aria-valuemin="0"
                        aria-valuemax="100"
                      >
                        <span :class="rateClass(normalizedRate(row))" :style="{ width: `${normalizedRate(row) ?? 0}%` }"></span>
                      </span>
                    </div>
                  </td>
                  <td><span :class="['status-pill', rateClass(normalizedRate(row))]">{{ complianceStatus(row) }}</span></td>
                  <td class="hint-cell" :title="complianceHint(row)">{{ complianceHint(row) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state">暂无已提交人员，生成周报数据后会显示模板填写正确率。</div>
        </section>

        <section class="content-panel summary-panel" aria-labelledby="summary-title">
          <div class="panel-title">
            <div>
              <h2 id="summary-title">提交摘要</h2>
              <p>由采集流程自动生成。</p>
            </div>
          </div>
          <MarkdownReport :content="summary.submissionSummary || ''" empty-text="暂无提交摘要。" variant="compact" />
        </section>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import MarkdownReport from '../../components/MarkdownReport.vue'
import { formatIsoWeekRange } from './weekRange.js'

const props = defineProps({
  overview: { type: Object, default: () => ({}) },
  rows: { type: Array, default: () => [] },
  summary: { type: Object, default: () => ({}) },
  weeks: { type: Array, default: () => [] },
  selectedWeek: { type: String, default: '' }
})

const emit = defineEmits(['select-week', 'download'])

const managementRules = [
  { title: '虚实盘', detail: '是否有可验证交付' },
  { title: '时间健康度', detail: '投入是否匹配岗位' },
  { title: 'AI 红黑榜', detail: '工具、场景与效果' },
  { title: '下周计划', detail: '日期与产出是否明确' }
]

const weekRangeLabel = computed(() => formatIsoWeekRange(props.selectedWeek))
function weekRangeLabelFor(week) {
  return formatIsoWeekRange(week)
}
const formattedGeneratedAt = computed(() => {
  if (!props.overview.generatedAt) return '-'
  const date = new Date(props.overview.generatedAt)
  if (Number.isNaN(date.getTime())) return String(props.overview.generatedAt)
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
})

function normalizedRate(row) {
  const value = row?.['模板填写正确率']
  if (value === null || value === undefined || value === '') return null
  const match = String(value).match(/-?\d+(?:\.\d+)?/)
  if (!match) return null
  return Math.round(Math.min(100, Math.max(0, Number(match[0]))))
}

const submittedRows = computed(() => props.rows
  .map((row, index) => ({ row, index, rate: normalizedRate(row) }))
  .filter(item => item.row['提交状态'] === '已提交')
  .sort((left, right) => (left.rate ?? 101) - (right.rate ?? 101) || left.index - right.index)
  .map(item => item.row))

const averageRate = computed(() => {
  const rates = submittedRows.value.map(normalizedRate).filter(rate => rate !== null)
  if (!rates.length) return null
  return Math.round(rates.reduce((sum, rate) => sum + rate, 0) / rates.length)
})

function displayRate(row) {
  const rate = normalizedRate(row)
  return rate === null ? '-' : `${rate}%`
}

function rateClass(rate) {
  if (rate === null) return 'unknown'
  if (rate >= 100) return 'complete'
  if (rate >= 75) return 'partial'
  return 'incomplete'
}

function missingFields(row) {
  const value = row?.['模板缺失项']
  if (Array.isArray(value)) return value.map(item => String(item).trim()).filter(Boolean)
  if (!value) return []
  return String(value).split(/[、,，;；|]/).map(item => item.trim()).filter(Boolean)
}

function complianceStatus(row) {
  return row?.['模板合规状态'] || '无法判断'
}

function complianceHint(row) {
  const missing = missingFields(row)
  if (missing.length) return `缺失：${missing.join('、')}`
  if (row?.['模板检查说明']) return row['模板检查说明']
  if (normalizedRate(row) === 100) return '模板字段填写完整'
  return '暂无检查说明'
}
</script>

<style scoped src="./SubmissionOverview.css"></style>
