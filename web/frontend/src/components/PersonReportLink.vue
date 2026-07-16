<template>
  <span class="person-report-link">
    <button
      ref="buttonElement"
      type="button"
      class="person-report-link__button"
      :aria-label="`查看${name}的周报原文`"
      :aria-describedby="tooltipVisible ? tooltipId : undefined"
      @mouseenter="showTooltip"
      @mouseleave="hideTooltip"
      @focus="showTooltip"
      @blur="hideTooltip"
      @click="selectPerson"
    >
      {{ name }}
    </button>
    <Teleport to="body">
      <span
        v-if="tooltipVisible"
        :id="tooltipId"
        ref="tooltipElement"
        :class="['person-report-link__tooltip', { 'is-below': tooltipBelow }]"
        :style="tooltipStyle"
        role="tooltip"
      >
        点击鼠标左键跳转查看 TA 的周报原文
      </span>
    </Teleport>
  </span>
</template>

<script setup>
import { nextTick, ref, useId } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  candidates: { type: Array, required: true }
})

const emit = defineEmits(['select'])
const buttonElement = ref(null)
const tooltipElement = ref(null)
const tooltipVisible = ref(false)
const tooltipBelow = ref(false)
const tooltipStyle = ref({})
const tooltipId = `person-report-tooltip-${useId()}`

async function showTooltip() {
  tooltipVisible.value = true
  await nextTick()
  placeTooltip()
}

function hideTooltip() {
  tooltipVisible.value = false
}

function selectPerson() {
  hideTooltip()
  emit('select', props.candidates)
}

function placeTooltip() {
  if (!buttonElement.value || !tooltipElement.value) return
  const buttonRect = buttonElement.value.getBoundingClientRect()
  const tooltipRect = tooltipElement.value.getBoundingClientRect()
  const margin = 8
  const width = Math.min(tooltipRect.width || 300, window.innerWidth - margin * 2)
  const height = tooltipRect.height || 38
  const targetX = buttonRect.left + buttonRect.width / 2
  const centerX = Math.min(
    Math.max(targetX, margin + width / 2),
    window.innerWidth - margin - width / 2
  )
  const tooltipLeft = centerX - width / 2
  const arrowLeft = Math.min(Math.max(targetX - tooltipLeft, 10), width - 10)
  const showBelow = buttonRect.top < height + 12
  tooltipBelow.value = showBelow
  tooltipStyle.value = {
    left: `${Math.round(centerX)}px`,
    top: `${Math.round(showBelow ? buttonRect.bottom + 9 : buttonRect.top - 9)}px`,
    '--tooltip-arrow-left': `${Math.round(arrowLeft)}px`
  }
}
</script>

<style scoped>
.person-report-link {
  display: inline-flex;
  vertical-align: baseline;
}

.person-report-link__button {
  display: inline;
  padding: 0;
  border: 0;
  border-bottom: 1px dashed currentColor;
  background: transparent;
  color: inherit;
  font: inherit;
  font-weight: inherit;
  line-height: inherit;
  cursor: pointer;
}

.person-report-link__button:hover,
.person-report-link__button:focus-visible {
  color: #174f72;
  border-bottom-style: solid;
  outline: none;
}

.person-report-link__tooltip {
  position: fixed;
  z-index: 3000;
  box-sizing: border-box;
  width: max-content;
  max-width: min(300px, calc(100vw - 16px));
  padding: 8px 10px;
  border-radius: 8px;
  background: #17263a;
  box-shadow: 0 8px 24px rgba(23, 38, 58, 0.2);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.45;
  pointer-events: none;
  transform: translate(-50%, -100%);
  animation: person-tooltip-in 140ms ease both;
}

.person-report-link__tooltip::after {
  position: absolute;
  top: 100%;
  left: var(--tooltip-arrow-left, 50%);
  border: 5px solid transparent;
  border-top-color: #17263a;
  content: '';
  transform: translateX(-50%);
}

.person-report-link__tooltip.is-below {
  transform: translate(-50%, 0);
}

.person-report-link__tooltip.is-below::after {
  top: auto;
  bottom: 100%;
  border-top-color: transparent;
  border-bottom-color: #17263a;
}

@keyframes person-tooltip-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@media (hover: none) {
  .person-report-link__tooltip {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .person-report-link__tooltip {
    animation: none;
  }
}
</style>
