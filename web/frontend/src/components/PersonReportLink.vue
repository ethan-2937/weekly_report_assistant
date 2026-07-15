<template>
  <span class="person-report-link">
    <button
      type="button"
      class="person-report-link__button"
      :aria-label="`查看${name}的周报原文`"
      title="点击鼠标左键跳转查看 TA 的周报原文"
      @click="emit('select', candidates)"
    >
      {{ name }}
    </button>
    <span class="person-report-link__tooltip" role="tooltip">
      点击鼠标左键跳转查看 TA 的周报原文
    </span>
  </span>
</template>

<script setup>
defineProps({
  name: { type: String, required: true },
  candidates: { type: Array, required: true }
})

const emit = defineEmits(['select'])
</script>

<style scoped>
.person-report-link {
  position: relative;
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
  position: absolute;
  z-index: 30;
  bottom: calc(100% + 9px);
  left: 50%;
  width: max-content;
  max-width: min(300px, 80vw);
  padding: 8px 10px;
  border-radius: 8px;
  background: #17263a;
  box-shadow: 0 8px 24px rgba(23, 38, 58, 0.2);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.45;
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, 4px);
  transition: opacity 140ms ease, transform 140ms ease;
}

.person-report-link__tooltip::after {
  position: absolute;
  top: 100%;
  left: 50%;
  border: 5px solid transparent;
  border-top-color: #17263a;
  content: '';
  transform: translateX(-50%);
}

.person-report-link:hover .person-report-link__tooltip,
.person-report-link:focus-within .person-report-link__tooltip {
  opacity: 1;
  transform: translate(-50%, 0);
}

@media (hover: none) {
  .person-report-link__tooltip {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .person-report-link__tooltip {
    transition: none;
  }
}
</style>
