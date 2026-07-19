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

  it('uses the former statistic area for separated evaluation rules', () => {
    const wrapper = createWrapper()

    const rules = wrapper.get('.evaluation-rules')
    expect(rules.text()).toContain('模板合规 ≠ 管理评价')
    expect(rules.text()).toContain('销售 9 项')
    expect(rules.text()).toContain('技术/产品 11 项')
    expect(rules.text()).toContain('虚实盘')
    expect(rules.text()).toContain('AI 红黑榜')
    expect(wrapper.get('.hint-cell').text()).toContain('11项必填字段')
    expect(wrapper.find('[data-testid="missing-overview-card"]').exists()).toBe(false)
    expect(wrapper.find('.person-report-link').exists()).toBe(false)
  })

  it('shows the browser compatibility hint for Excel export', () => {
    const wrapper = createWrapper()

    const hint = wrapper.get('.browser-download-hint__tooltip')
    expect(hint.attributes('role')).toBe('tooltip')
    expect(hint.text()).toContain('Chrome 111+')
    expect(hint.text()).toContain('Safari 16.4+')
    expect(hint.text()).toContain('联系开发人员')
  })

  it('offers the original report workbook without replacing the submission export', async () => {
    const wrapper = createWrapper()
    const buttons = wrapper.findAll('.download-button')

    expect(buttons.map(button => button.text())).toEqual(['下载原周报', '下载提交表'])
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('download-original')).toHaveLength(1)
    expect(wrapper.emitted('download')).toHaveLength(1)
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
          '模板合规状态': '符合模板',
          '模板检查说明': '按技术/产品岗位核对11项必填字段。 均已填写。'
        }
      ],
      summary: { submissionSummary: '虚构提交摘要' }
    }
  })
}
