<template>
  <span class="browser-download-hint">
    <slot />
    <span class="browser-download-hint__tooltip" role="tooltip">
      推荐使用 Chrome 111+、Edge 111+、Firefox 114+ 或 Safari 16.4+。Chromium 内核低于 111 或 Safari 低于 16.4 可能导致导出失败；若导出失败，请升级浏览器或点击右上角联系开发人员。
    </span>
  </span>
</template>

<style scoped>
.browser-download-hint {
  position: relative;
  display: inline-flex;
  min-width: 0;
}

.browser-download-hint__tooltip {
  position: absolute;
  z-index: 20;
  right: 0;
  bottom: calc(100% + 10px);
  width: min(340px, calc(100vw - 32px));
  padding: 10px 12px;
  border: 1px solid rgba(43, 91, 132, 0.22);
  border-radius: 10px;
  background: #19344a;
  box-shadow: 0 12px 28px rgba(19, 48, 70, 0.2);
  color: #f5fbff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.55;
  opacity: 0;
  pointer-events: none;
  transform: translateY(4px);
  transition: opacity 140ms ease, transform 140ms ease;
}

.browser-download-hint__tooltip::after {
  position: absolute;
  right: 20px;
  bottom: -6px;
  width: 10px;
  height: 10px;
  border-right: 1px solid rgba(43, 91, 132, 0.22);
  border-bottom: 1px solid rgba(43, 91, 132, 0.22);
  background: #19344a;
  content: '';
  transform: rotate(45deg);
}

.browser-download-hint:hover .browser-download-hint__tooltip,
.browser-download-hint:focus-within .browser-download-hint__tooltip {
  opacity: 1;
  transform: translateY(0);
}

@media (max-width: 640px) {
  .browser-download-hint__tooltip {
    right: auto;
    left: 50%;
    transform: translate(-50%, 4px);
  }

  .browser-download-hint:hover .browser-download-hint__tooltip,
  .browser-download-hint:focus-within .browser-download-hint__tooltip {
    transform: translate(-50%, 0);
  }

  .browser-download-hint__tooltip::after {
    right: auto;
    left: 50%;
    transform: translateX(-50%) rotate(45deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .browser-download-hint__tooltip {
    transition: none;
  }
}
</style>
