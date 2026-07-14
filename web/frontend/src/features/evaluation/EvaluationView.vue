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
      empty-text="暂无 AI 评价内容。"
      variant="report"
    />
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { PRIMARY_EVALUATION_DIMENSIONS } from '../../api/weeks.js'
import MarkdownReport from '../../components/MarkdownReport.vue'

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
const submissionRate = computed(() => {
  const expected = Number(props.overview?.expectedCount || 0)
  const submitted = Number(props.overview?.submittedCount || 0)
  return expected ? `${Math.round((submitted / expected) * 100)}%` : '0%'
})

function count(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}
</script>

<style scoped>
.evaluation-view {
  overflow: hidden;
  padding: 0;
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
  border-radius: 14px;
  background: linear-gradient(145deg, rgba(238, 244, 251, 0.9), #fff 64%);
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
  color: #172033;
  font-size: 16px;
}

.dimension-grid small {
  color: var(--muted);
  line-height: 1.55;
}

.dimension-grid .dimension-2 { border-top-color: var(--sheet-yellow-text); }
.dimension-grid .dimension-3 { border-top-color: var(--sheet-green-text); }
.dimension-grid .dimension-4 { border-top-color: var(--sheet-purple-text); }

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
