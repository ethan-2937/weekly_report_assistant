<template>
  <section class="page-card job-status-view">
    <div class="page-header">
      <div>
        <h1>运行状态</h1>
        <p>查看最近一次 Web 触发的采集任务。</p>
      </div>
      <div class="header-actions">
        <el-button round :loading="jobBusy" @click="emit('run-job')">补跑上一周</el-button>
        <el-button round @click="emit('refresh-job')">刷新状态</el-button>
      </div>
    </div>

    <div class="job-card">
      <el-tag :type="jobStatusType(latestJob.status)" effect="light">{{ latestJob.status || 'NO JOB' }}</el-tag>
      <h2>{{ latestJob.weekLabel || latestJob.weekMode || '暂无任务' }}</h2>
      <p>开始：{{ formatDate(latestJob.startedAt) }} · 结束：{{ formatDate(latestJob.finishedAt) }}</p>
      <pre>{{ latestJob.errorMessage || latestJob.stdout || '暂无日志。' }}</pre>
    </div>

    <section v-if="isAdmin" class="notification-test-panel" aria-labelledby="notification-test-title">
      <div>
        <span class="notification-test-panel__eyebrow">DELIVERY CHECK</span>
        <h2 id="notification-test-title">自动通知试发</h2>
        <p>仅向服务器配置的单一反馈接收人张艺政发送测试样例，不读取真实周报，也不影响正式任务状态。</p>
      </div>
      <div class="notification-test-actions">
        <el-button
          type="warning"
          plain
          :loading="notificationTestBusy === 'SUNDAY_REMINDER'"
          :disabled="Boolean(notificationTestBusy)"
          @click="emit('test-notification', 'SUNDAY_REMINDER')"
        >
          测试周日提醒
        </el-button>
        <el-button
          type="primary"
          plain
          :loading="notificationTestBusy === 'MONDAY_EVALUATION'"
          :disabled="Boolean(notificationTestBusy)"
          @click="emit('test-notification', 'MONDAY_EVALUATION')"
        >
          测试周一评价
        </el-button>
      </div>
    </section>

    <FeedbackPreviewPanel
      v-if="isAdmin"
      :api-client="apiClient"
      :selected-week="selectedWeek"
    />
  </section>
</template>

<script setup>
import FeedbackPreviewPanel from './FeedbackPreviewPanel.vue'

defineProps({
  latestJob: { type: Object, default: () => ({}) },
  jobBusy: { type: Boolean, default: false },
  isAdmin: { type: Boolean, default: false },
  notificationTestBusy: { type: String, default: '' },
  apiClient: { type: Object, required: true },
  selectedWeek: { type: String, default: '' }
})

const emit = defineEmits(['run-job', 'refresh-job', 'test-notification'])

function jobStatusType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.job-status-view {
  display: grid;
  gap: 22px;
}

.notification-test-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 22px;
  border: 1px solid #d7e2ea;
  border-radius: 18px;
  background: linear-gradient(135deg, #f7fafc, #edf4f7);
}

.notification-test-panel h2,
.notification-test-panel p {
  margin: 0;
}

.notification-test-panel p {
  max-width: 680px;
  margin-top: 7px;
  color: #607080;
  line-height: 1.65;
}

.notification-test-panel__eyebrow {
  color: #1f6d7f;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.14em;
}

.notification-test-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

@media (max-width: 760px) {
  .notification-test-panel,
  .notification-test-actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
