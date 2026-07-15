import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SubmissionPulse from './SubmissionPulse.vue'

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

    await wrapper.get('[data-testid="pulse-missing-card"]').trigger('click')
    expect(wrapper.emitted('navigate-missing')).toHaveLength(1)
  })
})
