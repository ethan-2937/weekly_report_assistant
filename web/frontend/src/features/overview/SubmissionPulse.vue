<template>
  <div class="submission-pulse" aria-label="本周提交统计">
    <article class="pulse-card pulse-card--expected">
      <span class="pulse-card__value">{{ count(overview.expectedCount) }}</span>
      <strong>应交</strong>
      <small>授权范围内人员</small>
    </article>

    <article class="pulse-card pulse-card--submitted">
      <span class="pulse-card__value">{{ count(overview.submittedCount) }}</span>
      <strong>已提交</strong>
      <small>按 userid 匹配</small>
    </article>

    <button
      class="pulse-card pulse-card--missing"
      type="button"
      data-testid="pulse-missing-card"
      aria-label="查看未提交名单"
      @click="emit('navigate-missing')"
    >
      <span class="pulse-card__value">{{ count(overview.missingCount) }}</span>
      <strong>未提交</strong>
      <small>点击查看名单 <span aria-hidden="true">↗</span></small>
    </button>

    <article class="pulse-card pulse-card--leaders">
      <span class="pulse-card__value">{{ count(overview.leaderCandidateCount) }}</span>
      <strong>负责人</strong>
      <small>履职评价单独呈现</small>
    </article>
  </div>
</template>

<script setup>
defineProps({
  overview: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['navigate-missing'])

function count(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0
}
</script>

<style scoped>
.submission-pulse {
  position: relative;
  width: min(100%, 470px);
  min-height: 286px;
  margin-inline: auto;
  font-family: "MiSans", "HarmonyOS Sans SC", "PingFang SC", sans-serif;
}

.pulse-card {
  position: absolute;
  display: grid;
  align-content: space-between;
  width: 166px;
  min-height: 184px;
  padding: 21px;
  border: 1px solid rgba(255, 255, 255, 0.66);
  border-radius: 25px;
  box-shadow: 0 24px 56px rgba(28, 45, 68, 0.18);
  color: white;
  text-align: left;
}

.pulse-card__value {
  font-size: clamp(42px, 5vw, 58px);
  font-weight: 900;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.07em;
  line-height: 0.95;
}

.pulse-card strong {
  align-self: end;
  font-size: 19px;
  letter-spacing: 0.02em;
}

.pulse-card small {
  margin-top: 5px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 11px;
  font-weight: 750;
}

.pulse-card--expected {
  top: 6px;
  left: 10px;
  z-index: 1;
  background: linear-gradient(155deg, #1769d3, #5599f3);
  transform: rotate(-1.5deg);
}

.pulse-card--submitted {
  top: 92px;
  left: 118px;
  z-index: 4;
  width: 152px;
  min-height: 166px;
  background: linear-gradient(155deg, #18864d, #52c47a);
  transform: rotate(0.8deg);
}

.pulse-card--missing {
  top: 26px;
  left: 228px;
  z-index: 3;
  min-height: 196px;
  appearance: none;
  background: linear-gradient(155deg, #f7b810, #ffd45a);
  color: #293445;
  cursor: pointer;
  font: inherit;
  transform: rotate(1.2deg);
  transition: transform 180ms ease, box-shadow 180ms ease;
}

.pulse-card--missing small {
  color: rgba(41, 52, 69, 0.72);
}

.pulse-card--missing:hover,
.pulse-card--missing:focus-visible {
  z-index: 6;
  box-shadow: 0 30px 68px rgba(151, 105, 0, 0.28);
  outline: 3px solid rgba(23, 105, 211, 0.24);
  outline-offset: 4px;
  transform: translateY(-7px) rotate(0deg);
}

.pulse-card--leaders {
  right: 0;
  bottom: 0;
  z-index: 5;
  width: 146px;
  min-height: 112px;
  padding: 17px 18px;
  background: linear-gradient(145deg, #263d5c, #4d6d88);
}

.pulse-card--leaders .pulse-card__value {
  font-size: 36px;
}

@media (max-width: 760px) {
  .submission-pulse {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    min-height: 0;
  }

  .pulse-card,
  .pulse-card--expected,
  .pulse-card--submitted,
  .pulse-card--missing,
  .pulse-card--leaders {
    position: relative;
    inset: auto;
    width: auto;
    min-height: 132px;
    padding: 16px;
    transform: none;
  }

  .pulse-card__value,
  .pulse-card--leaders .pulse-card__value {
    font-size: 36px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pulse-card--missing {
    transition: none;
  }
}
</style>
