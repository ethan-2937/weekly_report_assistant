import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SubmissionOverview from './SubmissionOverview.vue'
import { formatIsoWeekRange } from './weekRange.js'

describe('submission overview', () => {
  it('shows the ISO week range and both desktop and mobile rule guidance', () => {
    const wrapper = createWrapper()

    expect(formatIsoWeekRange('2026-W28')).toBe('2026年7月6日 - 7月12日')
    expect(formatIsoWeekRange('2025-W01')).toBe('2024年12月30日 - 2025年1月5日')
    expect(wrapper.text()).toContain('2026年7月6日 - 7月12日')
    expect(wrapper.find('#week-range-help').text()).toContain('完整 ISO 周统计（周一至周日）')
    expect(wrapper.find('.mobile-week-rule').text()).toContain('周四 00:00 至下一周周四 00:00 前')
  })

  it('navigates from the missing card and labels leaders without candidate wording', async () => {
    const wrapper = createWrapper()

    await wrapper.get('[data-testid="missing-overview-card"]').trigger('click')

    expect(wrapper.emitted('navigate-missing')).toHaveLength(1)
    expect(wrapper.get('[data-testid="leader-overview-card"]').text()).toContain('负责人')
    expect(wrapper.get('[data-testid="leader-overview-card"]').text()).not.toContain('负责人候选')
  })
})

function createWrapper() {
  return mount(SubmissionOverview, {
    props: {
      selectedWeek: '2026-W28',
      weeks: [{ week: '2026-W28' }],
      overview: {
        week: '2026-W28',
        expectedCount: 8,
        submittedCount: 6,
        missingCount: 2,
        leaderCandidateCount: 1,
        generatedAt: '2026-07-13T01:30:00Z'
      },
      rows: [
        {
          userid: 'fictional-user-01',
          '姓名': '测试员工甲',
          '部门': '虚构研发部',
          '提交状态': '已提交',
          '模板填写正确率': 100,
          '模板合规状态': '符合模板'
        }
      ],
      summary: { submissionSummary: '虚构提交摘要' }
    }
  })
}
