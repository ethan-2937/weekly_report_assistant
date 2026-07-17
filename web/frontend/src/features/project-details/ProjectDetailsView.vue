<template>
  <section class="page-card project-details-view">
    <header class="project-details-hero">
      <div>
        <span class="project-details-eyebrow">PROJECT LEDGER · {{ selectedWeek || '未选择周次' }}</span>
        <h1>项目明细</h1>
        <p>汇总产品线、客户、项目、投入工时和费用；多项目内容保留填报原文，不自动拆分或分摊。</p>
      </div>
      <BrowserDownloadHint>
        <el-button type="primary" round :disabled="!selectedWeek" @click="emit('download')">
          下载项目明细表
        </el-button>
      </BrowserDownloadHint>
    </header>

    <div class="project-metrics" aria-label="项目明细汇总">
      <article>
        <small>明细记录</small>
        <strong>{{ filteredRows.length }}</strong>
        <span>当前筛选</span>
      </article>
      <article>
        <small>产品线</small>
        <strong>{{ productLineCount }}</strong>
        <span>已填产品线</span>
      </article>
      <article>
        <small>投入工时</small>
        <strong>{{ formatDays(totalDays) }}</strong>
        <span>天</span>
      </article>
      <article>
        <small>本周费用</small>
        <strong>{{ formatMoney(totalExpense) }}</strong>
        <span>差旅 + 招待</span>
      </article>
    </div>

    <div class="project-details-toolbar">
      <el-input v-model="keyword" clearable placeholder="搜索产品线、客户或项目" />
      <el-select v-model="productLine" clearable placeholder="全部产品线">
        <el-option v-for="item in productLineOptions" :key="item" :label="item" :value="item" />
      </el-select>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-empty v-else-if="!filteredRows.length" description="该周暂无可展示的项目明细" />
    <el-table v-else :data="filteredRows" class="project-details-table" row-key="sequence">
      <el-table-column prop="sequence" label="序号" width="72" align="center" />
      <el-table-column prop="productLine" label="产品线" min-width="150" show-overflow-tooltip />
      <el-table-column prop="customerName" label="客户名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="projectName" label="项目名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="investedDays" label="本周投入工时（天）" width="170" align="right" />
      <el-table-column label="本周差旅费用" width="150" align="right">
        <template #default="{ row }">{{ formatCellMoney(row.travelExpense) }}</template>
      </el-table-column>
      <el-table-column label="本周招待费用" width="150" align="right">
        <template #default="{ row }">{{ formatCellMoney(row.hospitalityExpense) }}</template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import BrowserDownloadHint from '../../components/BrowserDownloadHint.vue'

const props = defineProps({
  selectedWeek: { type: String, default: '' },
  rows: { type: Array, default: () => [] },
  error: { type: String, default: '' }
})
const emit = defineEmits(['download'])
const keyword = ref('')
const productLine = ref('')

const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return props.rows.filter(row => {
    const content = `${row.productLine} ${row.customerName} ${row.projectName}`.toLowerCase()
    return (!query || content.includes(query))
      && (!productLine.value || row.productLine === productLine.value)
  })
})

const productLineOptions = computed(() => [...new Set(
  props.rows.map(row => row.productLine.trim()).filter(Boolean)
)].sort((left, right) => left.localeCompare(right, 'zh-CN')))

const productLineCount = computed(() => new Set(
  filteredRows.value.map(row => row.productLine.trim()).filter(Boolean)
).size)
const totalDays = computed(() => sumNumeric(filteredRows.value.map(row => row.investedDays)))
const totalExpense = computed(() => sumNumeric(filteredRows.value.flatMap(row => [
  row.travelExpense,
  row.hospitalityExpense
])))

function sumNumeric(values) {
  return values.reduce((sum, value) => {
    const normalized = String(value || '').trim()
    if (!/^-?\d+(?:\.\d+)?$/.test(normalized)) return sum
    return sum + Number(normalized)
  }, 0)
}

function formatDays(value) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value)
}

function formatMoney(value) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    maximumFractionDigits: 2
  }).format(value)
}

function formatCellMoney(value) {
  const normalized = String(value || '').trim()
  return /^-?\d+(?:\.\d+)?$/.test(normalized) ? formatMoney(Number(normalized)) : (normalized || '-')
}
</script>

<style scoped src="./ProjectDetailsView.css"></style>
