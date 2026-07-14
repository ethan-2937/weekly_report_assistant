<template>
  <section class="submission-overview">
    <nav class="week-selector" aria-label="选择周次">
      <button
        v-for="item in weeks"
        :key="item.week"
        type="button"
        :class="{ active: selectedWeek === item.week }"
        :aria-current="selectedWeek === item.week ? 'true' : undefined"
        @click="emit('select-week', item.week)"
      >
        {{ item.week }}
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

      <div class="overview-focus">
        <button
          class="focus-card missing-card"
          type="button"
          data-testid="missing-overview-card"
          aria-label="查看未提交名单"
          @click="emit('navigate-missing')"
        >
          <span class="focus-label">未提交</span>
          <strong>{{ safeCount(overview.missingCount) }}</strong>
          <span class="focus-meta">
            应交 {{ safeCount(overview.expectedCount) }} · 已交 {{ safeCount(overview.submittedCount) }}
          </span>
          <span class="focus-action">查看名单 <span aria-hidden="true">→</span></span>
        </button>

        <article class="focus-card leader-card" data-testid="leader-overview-card">
          <span class="focus-label">负责人</span>
          <strong>{{ safeCount(overview.leaderCandidateCount) }}</strong>
          <span class="focus-meta">负责人识别结果</span>
          <span class="focus-note">履职评价在 AI 评价页单独呈现</span>
        </article>
      </div>

      <aside class="rule-note" aria-label="规则说明">
        <p><strong>模板合规</strong><span>按钉钉线上四项模板检查填写完整性。</span></p>
        <span class="rule-divider" aria-hidden="true"></span>
        <p><strong>管理评价</strong><span>使用独立筛选标准，不以模板合规结果替代。</span></p>
      </aside>

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

const emit = defineEmits(['select-week', 'navigate-missing', 'download'])

const weekRangeLabel = computed(() => formatIsoWeekRange(props.selectedWeek))
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

function safeCount(value) {
  const count = Number(value)
  return Number.isFinite(count) && count >= 0 ? count : 0
}

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
