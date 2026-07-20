<template>
  <section class="feedback-review-panel" aria-labelledby="feedback-review-title">
    <div class="feedback-review-panel__intro">
      <span class="feedback-review-panel__eyebrow">DELIVERY AUDIT</span>
      <h2 id="feedback-review-title">Feedback 通知复核</h2>
      <p>仅管理员可按周查看已完整发送的周一评价正文。页面不会读取或展示原始周报与内部身份标识。</p>
    </div>

    <form class="feedback-review-query" @submit.prevent="loadPreview">
      <label for="feedback-review-week">业务周</label>
      <input
        id="feedback-review-week"
        v-model.trim="weekInput"
        type="text"
        inputmode="text"
        autocomplete="off"
        placeholder="例如 2026-W29"
        aria-describedby="feedback-review-week-hint"
      />
      <button type="submit" :disabled="loading || !weekInput">
        {{ loading ? '正在核对…' : '加载本周通知' }}
      </button>
      <small id="feedback-review-week-hint">只加载一个周次，切换周次后不会保留旧正文。</small>
    </form>

    <p v-if="error" class="feedback-review-message feedback-review-message--error" role="alert">{{ error }}</p>

    <template v-if="preview">
      <div class="feedback-review-summary">
        <span><strong>{{ preview.week }}</strong> · {{ phaseLabel }}</span>
        <span>发送 {{ preview.sentCount }} / {{ preview.eligibleCount }} 人</span>
        <span>完成时间 {{ formatDate(preview.updatedAt) }}</span>
        <span :class="preview.exactMatch ? 'is-exact' : 'is-warning'">
          {{ verificationLabel }}
        </span>
      </div>

      <p
        v-if="preview.warning"
        class="feedback-review-message feedback-review-message--warning"
        role="status"
      >
        {{ preview.warning }}<template v-if="!preview.exactMatch"> 当前不会展示可能已变化的正文。</template>
      </p>

      <p v-if="preview.exactMatch && !preview.notifications.length" class="feedback-review-empty">
        该周没有符合发送条件的员工通知。
      </p>

      <div v-if="preview.notifications.length" class="feedback-review-list">
        <details v-for="(notification, index) in preview.notifications" :key="`${notification.name}-${index}`">
          <summary>
            <span class="feedback-review-index">{{ `${index + 1}`.padStart(2, '0') }}</span>
            <span>
              <strong>{{ notification.name }}</strong>
              <small>{{ [notification.department, notification.title].filter(Boolean).join(' · ') }}</small>
            </span>
            <span class="feedback-review-expand">
              <span class="feedback-review-expand__open">展开完整正文</span>
              <span class="feedback-review-expand__close">收起正文</span>
            </span>
          </summary>
          <pre>{{ notification.markdown }}</pre>
        </details>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { fetchEvaluationFeedbackPreview } from '../../api/evaluationFeedbackPreviews.js'

const props = defineProps({
  apiClient: { type: Object, required: true },
  selectedWeek: { type: String, default: '' }
})

const weekInput = ref(props.selectedWeek)
const loading = ref(false)
const error = ref('')
const preview = ref(null)

const phaseLabel = computed(() => preview.value?.phase === 'COMPLETE' ? '发送完成' : preview.value?.phase || '未知状态')
const verificationLabel = computed(() => {
  if (!preview.value?.exactMatch) return '正文一致性未确认'
  return preview.value.verificationMode === 'LEGACY_TIME' ? '旧状态时间核对' : '正文摘要一致'
})

watch(() => props.selectedWeek, (week) => {
  weekInput.value = week || ''
  clearPreview()
})

async function loadPreview() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  preview.value = null
  try {
    preview.value = await fetchEvaluationFeedbackPreview(props.apiClient, weekInput.value)
  } catch (requestError) {
    error.value = requestError?.message || '通知复核加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function clearPreview() {
  preview.value = null
  error.value = ''
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.feedback-review-panel {
  display: grid;
  gap: 18px;
  padding: 24px;
  border: 1px solid #cbd9e3;
  border-radius: 20px;
  background:
    radial-gradient(circle at 92% 8%, rgba(40, 109, 123, 0.12), transparent 32%),
    linear-gradient(145deg, #fbfcfd, #eef4f6);
}

.feedback-review-panel__intro h2,
.feedback-review-panel__intro p {
  margin: 0;
}

.feedback-review-panel__intro p {
  max-width: 760px;
  margin-top: 7px;
  color: #536575;
  line-height: 1.7;
}

.feedback-review-panel__eyebrow {
  color: #216a78;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.14em;
}

.feedback-review-query {
  display: grid;
  grid-template-columns: auto minmax(160px, 240px) auto 1fr;
  align-items: center;
  gap: 10px;
}

.feedback-review-query label {
  color: #263746;
  font-weight: 800;
}

.feedback-review-query input {
  min-width: 0;
  padding: 10px 13px;
  border: 1px solid #aebfca;
  border-radius: 10px;
  background: #fff;
  color: #182733;
  font: inherit;
}

.feedback-review-query button {
  padding: 10px 17px;
  border: 0;
  border-radius: 999px;
  background: #195e6d;
  color: #fff;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.feedback-review-query button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.feedback-review-query small {
  color: #70818e;
}

.feedback-review-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.feedback-review-summary span {
  padding: 7px 11px;
  border: 1px solid #d2dee5;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: #3c4e5c;
  font-size: 13px;
}

.feedback-review-summary .is-exact {
  border-color: #a8d0bc;
  color: #17623f;
}

.feedback-review-summary .is-warning {
  border-color: #e2bf87;
  color: #82520d;
}

.feedback-review-message,
.feedback-review-empty {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  line-height: 1.65;
}

.feedback-review-message--error {
  background: #fff0ee;
  color: #9a3028;
}

.feedback-review-message--warning {
  background: #fff6df;
  color: #75500f;
}

.feedback-review-empty {
  background: #fff;
  color: #596b78;
}

.feedback-review-list {
  display: grid;
  gap: 10px;
}

.feedback-review-list details {
  overflow: hidden;
  border: 1px solid #d3dee5;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
}

.feedback-review-list summary {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  list-style: none;
}

.feedback-review-list summary::-webkit-details-marker {
  display: none;
}

.feedback-review-list summary small {
  display: block;
  margin-top: 2px;
  color: #687986;
}

.feedback-review-index {
  color: #2a7583;
  font-size: 12px;
  font-weight: 900;
}

.feedback-review-expand {
  color: #256d7b;
  font-size: 13px;
  font-weight: 800;
}

.feedback-review-expand__close {
  display: none;
}

.feedback-review-list details[open] .feedback-review-expand__open {
  display: none;
}

.feedback-review-list details[open] .feedback-review-expand__close {
  display: inline;
}

.feedback-review-list pre {
  margin: 0;
  padding: 18px;
  border-top: 1px solid #dce5ea;
  background: #f8fafb;
  color: #1d2a34;
  font: 14px/1.8 "Microsoft YaHei", sans-serif;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 760px) {
  .feedback-review-panel {
    padding: 18px;
  }

  .feedback-review-query {
    grid-template-columns: 1fr;
  }

  .feedback-review-query button {
    width: 100%;
  }

  .feedback-review-list summary {
    grid-template-columns: auto 1fr;
  }

  .feedback-review-expand {
    grid-column: 2;
  }
}
</style>
