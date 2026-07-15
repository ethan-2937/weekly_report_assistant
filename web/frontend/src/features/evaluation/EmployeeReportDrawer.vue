<template>
  <Teleport to="body">
    <div v-if="open" class="report-source-overlay" role="presentation" @click.self="emit('close')">
      <aside
        class="report-source-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-source-title"
        @keydown.esc="emit('close')"
      >
        <header class="report-source-header">
          <div>
            <span>WEEKLY SOURCE · {{ detail?.week || week }}</span>
            <h2 id="report-source-title">{{ selectedPerson?.name || '选择要查看的成员' }}</h2>
            <p v-if="selectedPerson">{{ personMeta(selectedPerson) }}</p>
            <p v-else>存在同名人员，请根据部门和职务确认。</p>
          </div>
          <button type="button" class="report-source-close" aria-label="关闭周报原文" @click="emit('close')">×</button>
        </header>

        <div v-if="!selectedPerson && candidates.length > 1" class="report-source-picker">
          <button
            v-for="candidate in candidates"
            :key="candidate.userId"
            type="button"
            @click="emit('select', candidate)"
          >
            <strong>{{ candidate.name }}</strong>
            <span>{{ personMeta(candidate) }}</span>
          </button>
        </div>

        <div v-else-if="loading" class="report-source-loading" role="status">
          <span></span><span></span><span></span>
          <p>正在读取授权范围内的周报原文...</p>
        </div>

        <div v-else-if="error" class="report-source-state report-source-state--error" role="alert">
          <strong>原文加载失败</strong>
          <p>{{ error }}</p>
          <button type="button" @click="emit('retry')">重新加载</button>
        </div>

        <div v-else-if="detail && !detail.available" class="report-source-state">
          <strong>{{ detail.status || '暂无原文' }}</strong>
          <p>{{ detail.message || '该周没有可查看的周报原文。' }}</p>
        </div>

        <div v-else-if="detail?.available" class="report-source-content">
          <dl class="report-source-meta">
            <div><dt>周次</dt><dd>{{ detail.week }}</dd></div>
            <div><dt>提交时间</dt><dd>{{ detail.submittedAt || '未记录' }}</dd></div>
            <div><dt>状态</dt><dd>{{ detail.status }}</dd></div>
          </dl>
          <section v-for="(field, index) in detail.fields" :key="`${field.label}-${index}`" class="report-source-field">
            <span>{{ `${index + 1}`.padStart(2, '0') }}</span>
            <div>
              <h3>{{ field.label }}</h3>
              <p>{{ field.value || '未填写' }}</p>
            </div>
          </section>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup>
defineProps({
  open: { type: Boolean, default: false },
  week: { type: String, default: '' },
  candidates: { type: Array, default: () => [] },
  selectedPerson: { type: Object, default: null },
  detail: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['close', 'select', 'retry'])

function personMeta(person) {
  return [person?.department, person?.title].filter(Boolean).join(' · ') || '部门和职务未记录'
}
</script>

<style scoped>
.report-source-overlay {
  position: fixed;
  z-index: 2200;
  inset: 0;
  display: flex;
  justify-content: flex-end;
  background: rgba(13, 26, 40, 0.42);
  backdrop-filter: blur(3px);
}

.report-source-drawer {
  width: min(680px, 94vw);
  height: 100%;
  overflow-y: auto;
  background: #f5f7f6;
  box-shadow: -24px 0 70px rgba(13, 26, 40, 0.22);
  color: #17263a;
}

.report-source-header {
  position: sticky;
  z-index: 2;
  top: 0;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 32px 24px;
  background: linear-gradient(135deg, #183249, #345e62);
  color: #fff;
}

.report-source-header span {
  color: rgba(255, 255, 255, 0.7);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.report-source-header h2 {
  margin: 6px 0 4px;
  font-size: 28px;
}

.report-source-header p {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 13px;
}

.report-source-close {
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  font-size: 25px;
  cursor: pointer;
}

.report-source-picker,
.report-source-content,
.report-source-state,
.report-source-loading {
  padding: 28px 32px;
}

.report-source-picker {
  display: grid;
  gap: 12px;
}

.report-source-picker button {
  display: grid;
  gap: 5px;
  padding: 18px;
  border: 1px solid #d7e0e4;
  border-radius: 14px;
  background: #fff;
  color: #17263a;
  text-align: left;
  cursor: pointer;
}

.report-source-picker button:hover,
.report-source-picker button:focus-visible {
  border-color: #34716e;
  outline: none;
  box-shadow: 0 8px 24px rgba(28, 75, 75, 0.1);
}

.report-source-picker span {
  color: #687789;
}

.report-source-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0 0 18px;
}

.report-source-meta div,
.report-source-field {
  border: 1px solid #dce4e7;
  border-radius: 14px;
  background: #fff;
}

.report-source-meta div {
  padding: 13px 14px;
}

.report-source-meta dt {
  color: #738090;
  font-size: 11px;
}

.report-source-meta dd {
  margin: 4px 0 0;
  font-weight: 750;
}

.report-source-field {
  display: grid;
  grid-template-columns: 38px 1fr;
  gap: 12px;
  margin-top: 12px;
  padding: 20px;
}

.report-source-field > span {
  color: #34716e;
  font-size: 12px;
  font-weight: 900;
}

.report-source-field h3 {
  margin: 0 0 10px;
  font-size: 16px;
}

.report-source-field p {
  margin: 0;
  color: #344558;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.report-source-state {
  margin: 28px 32px;
  border: 1px solid #dce4e7;
  border-radius: 14px;
  background: #fff;
}

.report-source-state p {
  color: #687789;
}

.report-source-state button {
  padding: 8px 13px;
  border: 0;
  border-radius: 8px;
  background: #244e62;
  color: #fff;
  cursor: pointer;
}

.report-source-state--error {
  border-color: #e4c9c9;
}

.report-source-loading span {
  display: block;
  height: 18px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: linear-gradient(90deg, #e5eaec 25%, #f4f6f7 50%, #e5eaec 75%);
  background-size: 200% 100%;
  animation: source-loading 1.3s infinite linear;
}

.report-source-loading span:nth-child(2) { width: 82%; }
.report-source-loading span:nth-child(3) { width: 65%; }
.report-source-loading p { color: #687789; }

@keyframes source-loading {
  to { background-position: -200% 0; }
}

@media (max-width: 620px) {
  .report-source-drawer { width: 100%; }
  .report-source-header,
  .report-source-picker,
  .report-source-content,
  .report-source-loading { padding-inline: 20px; }
  .report-source-state { margin-inline: 20px; }
  .report-source-meta { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .report-source-loading span { animation: none; }
}
</style>
