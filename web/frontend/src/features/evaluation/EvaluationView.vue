<template>
  <section class="page-card report-page evaluation-view">
    <div class="report-hero-card">
      <div class="report-hero-main">
        <span class="report-eyebrow">WEEKLY AI REVIEW · {{ displayWeek }}</span>
        <h1>AI 周报评价看板</h1>
        <p>主视图聚焦四个管理维度；综合结论与需跟进仍保留在员工评价详情中。</p>
      </div>
      <div class="report-status-card">
        <span :class="['status-dot', reportReady ? 'ready' : 'waiting']"></span>
        <strong>{{ reportReady ? '正式评价已生成' : '等待正式评价' }}</strong>
        <small>{{ safeSource }}</small>
      </div>
    </div>

    <div class="dimension-grid" aria-label="四个主评价维度">
      <article v-for="(dimension, index) in dimensions" :key="dimension.title" :class="`dimension-${index + 1}`">
        <span>0{{ index + 1 }}</span>
        <div>
          <strong>{{ dimension.title }}</strong>
          <small>{{ dimension.description }}</small>
        </div>
      </article>
    </div>

    <div class="report-kpi-strip">
      <article>
        <small>提交率</small>
        <strong>{{ submissionRate }}</strong>
        <span>{{ count(overview.submittedCount) }} / {{ count(overview.expectedCount) }}</span>
      </article>
      <article>
        <small>未提交</small>
        <strong>{{ count(overview.missingCount) }}</strong>
        <span>按规则确认后跟进</span>
      </article>
      <article>
        <small>负责人</small>
        <strong>{{ count(overview.leaderCandidateCount) }}</strong>
        <span>履职评价见折叠详情</span>
      </article>
      <article>
        <small>评价模式</small>
        <strong>{{ reportReady ? '正式' : '待生成' }}</strong>
        <span>{{ reportReady ? '可直接阅读' : '仅展示安全提示' }}</span>
      </article>
    </div>

    <div v-if="!reportReady" class="evaluation-notice" role="status">
      <strong>正式评价尚未就绪</strong>
      <span>生成完成后刷新页面即可查看；预处理材料不会在此展示。</span>
    </div>

    <MarkdownReport
      :content="safeReportContent"
      :download-prefix="displayWeek"
      :people="reportPeople"
      empty-text="暂无 AI 评价内容。"
      variant="report"
      @person-select="openPersonReport"
    />

    <EmployeeReportDrawer
      :open="drawerOpen"
      :week="displayWeek"
      :candidates="personCandidates"
      :selected-person="selectedPerson"
      :detail="reportDetail"
      :loading="reportLoading"
      :error="reportError"
      @close="closePersonReport"
      @select="selectPerson"
      @retry="loadSelectedPerson"
    />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { PRIMARY_EVALUATION_DIMENSIONS } from '../../api/weeks.js'
import MarkdownReport from '../../components/MarkdownReport.vue'
import EmployeeReportDrawer from './EmployeeReportDrawer.vue'

const props = defineProps({
  selectedWeek: {
    type: String,
    default: ''
  },
  analysis: {
    type: Object,
    default: () => ({})
  },
  overview: {
    type: Object,
    default: () => ({})
  },
  submissionRows: {
    type: Array,
    default: () => []
  },
  loadPersonReport: {
    type: Function,
    default: null
  }
})

const dimensionDescriptions = {
  outcomes: '本周成果是否形成可验证交付',
  timeAllocation: '投入结构是否匹配岗位重点',
  aiUsage: '工具、场景和效果是否清晰',
  nextWeekPlan: '是否同时具备日期与产出'
}
const dimensions = PRIMARY_EVALUATION_DIMENSIONS.map(dimension => ({
  ...dimension,
  description: dimensionDescriptions[dimension.key] || ''
}))

const sourceName = computed(() => `${props.analysis?.source || ''}`.split(/[\\/]/).pop())
const displayWeek = computed(() => props.selectedWeek || props.analysis?.week || '未选择周次')
const reportReady = computed(() => props.analysis?.isManagerReport === true)
const safeReportContent = computed(() => reportReady.value ? `${props.analysis?.content || ''}` : '')
const safeSource = computed(() => reportReady.value ? (sourceName.value || '正式评价') : '等待生成')
const reportPeople = computed(() => props.submissionRows
  .map(row => ({
    name: `${row?.['姓名'] || ''}`.trim(),
    userId: `${row?.userid || ''}`.trim(),
    department: `${row?.['部门'] || ''}`.trim(),
    title: `${row?.['职务'] || ''}`.trim(),
    status: `${row?.['提交状态'] || ''}`.trim()
  }))
  .filter(person => person.name && person.userId))
const submissionRate = computed(() => {
  const expected = Number(props.overview?.expectedCount || 0)
  const submitted = Number(props.overview?.submittedCount || 0)
  return expected ? `${Math.round((submitted / expected) * 100)}%` : '0%'
})

const drawerOpen = ref(false)
const personCandidates = ref([])
const selectedPerson = ref(null)
const reportDetail = ref(null)
const reportLoading = ref(false)
const reportError = ref('')
let requestSequence = 0

watch(() => props.selectedWeek, closePersonReport)

function openPersonReport(candidates) {
  const validCandidates = (Array.isArray(candidates) ? candidates : [])
    .filter(candidate => candidate?.userId)
  if (!validCandidates.length) return
  requestSequence += 1
  drawerOpen.value = true
  personCandidates.value = validCandidates
  selectedPerson.value = null
  reportDetail.value = null
  reportError.value = ''
  reportLoading.value = false
  if (validCandidates.length === 1) selectPerson(validCandidates[0])
}

function selectPerson(person) {
  if (!person?.userId) return
  selectedPerson.value = person
  loadSelectedPerson()
}

async function loadSelectedPerson() {
  if (!selectedPerson.value?.userId || !props.loadPersonReport) {
    reportError.value = '周报原文加载功能暂不可用，请联系开发人员。'
    return
  }
  const currentRequest = ++requestSequence
  reportLoading.value = true
  reportError.value = ''
  reportDetail.value = null
  try {
    const detail = await props.loadPersonReport(props.selectedWeek, selectedPerson.value.userId)
    if (currentRequest === requestSequence) reportDetail.value = detail
  } catch (error) {
    if (currentRequest === requestSequence) {
      reportError.value = error?.message || '请求失败，请稍后重试。'
    }
  } finally {
    if (currentRequest === requestSequence) reportLoading.value = false
  }
}

function closePersonReport() {
  requestSequence += 1
  drawerOpen.value = false
  personCandidates.value = []
  selectedPerson.value = null
  reportDetail.value = null
  reportLoading.value = false
  reportError.value = ''
}

function count(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}
</script>

<style scoped>
.evaluation-view {
  --evaluation-ink: #17263a;
  --evaluation-muted: #627084;
  overflow: hidden;
  padding: 0;
  color: var(--evaluation-ink);
  font-family: "MiSans", "HarmonyOS Sans SC", "PingFang SC", sans-serif;
}

.report-hero-card {
  padding-block: 38px;
  background:
    radial-gradient(circle at 86% 14%, rgba(232, 174, 45, 0.24), transparent 25%),
    radial-gradient(circle at 58% 0%, rgba(52, 168, 133, 0.2), transparent 28%),
    linear-gradient(128deg, #14283f 0%, #294760 54%, #355f61 100%);
}

.report-hero-main h1 {
  font-size: clamp(36px, 4.4vw, 60px);
  font-weight: 850;
  letter-spacing: -0.055em;
}

.report-hero-main p {
  max-width: 760px;
  color: rgba(255, 255, 255, 0.82);
  font-weight: 650;
}

.report-status-card {
  border-color: rgba(255, 255, 255, 0.58);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.97), rgba(231, 241, 245, 0.92));
}

.dimension-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 18px 24px 2px;
}

.dimension-grid article {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--sheet-line);
  border-top: 4px solid var(--sheet-blue-text);
  border-radius: 16px;
  background: linear-gradient(145deg, rgba(238, 244, 251, 0.82), #fff 66%);
  box-shadow: 0 10px 28px rgba(35, 57, 82, 0.06);
}

.dimension-grid article > span {
  color: var(--sheet-blue-text);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.dimension-grid article div {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.dimension-grid strong,
.dimension-grid small {
  display: block;
}

.dimension-grid strong {
  color: var(--evaluation-ink);
  font-size: 15px;
  font-weight: 850;
}

.dimension-grid small {
  color: var(--evaluation-muted);
  font-size: 12px;
  line-height: 1.55;
}

.dimension-grid .dimension-2 { border-top-color: var(--sheet-yellow-text); }
.dimension-grid .dimension-3 { border-top-color: var(--sheet-green-text); }
.dimension-grid .dimension-4 { border-top-color: var(--sheet-purple-text); }

.report-kpi-strip article {
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(35, 57, 82, 0.045);
}

.report-kpi-strip strong {
  color: var(--evaluation-ink);
  font-weight: 850;
}

.evaluation-notice {
  display: flex;
  gap: 10px;
  align-items: baseline;
  margin: 16px 24px 0;
  padding: 14px 16px;
  border: 1px solid rgba(234, 179, 8, 0.28);
  border-radius: 12px;
  background: rgba(254, 249, 195, 0.48);
  color: #713f12;
}

@media (max-width: 980px) {
  .dimension-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .dimension-grid {
    grid-template-columns: 1fr;
    padding-inline: 16px;
  }

  .evaluation-notice {
    align-items: flex-start;
    flex-direction: column;
    margin-inline: 16px;
  }
}
</style>
