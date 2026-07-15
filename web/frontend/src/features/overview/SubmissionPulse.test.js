import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SubmissionPulse from './SubmissionPulse.vue'
import source from './SubmissionPulse.vue?raw'

function styleBlock(selector) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = source.match(new RegExp(`${escapedSelector}\\s*\\{([^}]+)\\}`))
  expect(match, `missing CSS block for ${selector}`).not.toBeNull()
  return match[1]
}

describe('top submission pulse', () => {
  it('combines all four statistics and routes from the missing card', async () => {
    const wrapper = mount(SubmissionPulse, {
      props: {
        overview: {
          expectedCount: 94,
          submittedCount: 85,
          missingCount: 9,
          leaderCandidateCount: 12
        }
      }
    })

    expect(wrapper.text()).toContain('应交')
    expect(wrapper.text()).toContain('已提交')
    expect(wrapper.text()).toContain('未提交')
    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.findAll('.pulse-card__value').map((node) => node.text())).toEqual([
      '94',
      '85',
      '9',
      '12'
    ])

    await wrapper.get('[data-testid="pulse-missing-card"]').trigger('click')
    expect(wrapper.emitted('navigate-missing')).toHaveLength(1)
  })

  it('keeps the missing action above the staggered desktop stack', () => {
    expect(styleBlock('.pulse-card--expected')).toContain('z-index: 1;')
    expect(styleBlock('.pulse-card--submitted')).toContain('z-index: 2;')
    expect(styleBlock('.pulse-card--leaders')).toContain('z-index: 3;')
    expect(styleBlock('.pulse-card--missing')).toContain('z-index: 4;')
  })

  it('hops gently on desktop and disables motion for mobile and reduced-motion users', () => {
    expect(styleBlock('.pulse-card')).toContain('animation: pulse-card-hop')
    expect(source).toContain('@keyframes pulse-card-hop')
    expect(source).toMatch(
      /\.pulse-card--missing:hover,\s*\.pulse-card--missing:focus-visible\s*\{[^}]*animation-play-state: paused;/s
    )
    expect(source).toMatch(
      /@media \(max-width: 760px\)\s*\{[\s\S]*?\.pulse-card,[\s\S]*?animation: none;[\s\S]*?transform: none;/
    )
    expect(source).toMatch(
      /@media \(prefers-reduced-motion: reduce\)\s*\{\s*\.pulse-card\s*\{\s*animation: none;/
    )
  })
})
