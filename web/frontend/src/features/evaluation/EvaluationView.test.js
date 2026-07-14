import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EvaluationView from './EvaluationView.vue'

const report = `# 管理评价

## 需要老板拍板/协调事项
- 虚构协调事项

## 员工五维评价
| 姓名 | 虚实盘（本周成果） | 时间分配健康度 | AI使用红黑榜 | 下周计划合格性 | 综合结论/需跟进 |
| --- | --- | --- | --- | --- | --- |
| 测试员工甲 | 完成 | 健康 | 红榜 | 合格 | 关注交付节奏 |

## 团队负责人履职检查
- 测试负责人甲：已完成团队汇总
`

describe('AI evaluation view', () => {
  it('presents four primary dimensions while keeping the fifth in employee detail', () => {
    const wrapper = createWrapper()
    const grid = wrapper.get('.dimension-grid[aria-label="四个主评价维度"]')

    expect(grid.findAll('article')).toHaveLength(4)
    expect(grid.text()).toContain('虚实盘（本周成果）')
    expect(grid.text()).toContain('时间分配健康度')
    expect(grid.text()).toContain('AI使用红黑榜')
    expect(grid.text()).toContain('下周计划合格性')
    expect(grid.text()).not.toContain('综合结论/需跟进')
    expect(wrapper.text()).toContain('综合结论/需跟进')
    expect(wrapper.get('[aria-label="本周重点"]').text()).toContain('虚构协调事项')
  })

  it('keeps employee and leader sections collapsed until explicitly expanded', async () => {
    const wrapper = createWrapper()
    const toggles = wrapper.findAll('.report-section-toggle')

    expect(toggles.map(toggle => toggle.get('strong').text())).toEqual([
      '员工五维评价',
      '团队负责人履职检查'
    ])
    expect(toggles.every(toggle => toggle.attributes('aria-expanded') === 'false')).toBe(true)

    await toggles[0].trigger('click')
    expect(toggles[0].attributes('aria-expanded')).toBe('true')
    expect(toggles[1].attributes('aria-expanded')).toBe('false')

    await toggles[1].trigger('click')
    expect(toggles[1].attributes('aria-expanded')).toBe('true')
  })

  it('does not expose preprocessing content as a formal report', () => {
    const wrapper = mount(EvaluationView, {
      props: {
        selectedWeek: '2026-W28',
        analysis: {
          source: 'analysis_input.md',
          content: '不应展示的虚构预处理正文',
          isManagerReport: false
        }
      }
    })

    expect(wrapper.text()).toContain('正式评价尚未就绪')
    expect(wrapper.text()).not.toContain('不应展示的虚构预处理正文')
  })
})

function createWrapper() {
  return mount(EvaluationView, {
    props: {
      selectedWeek: '2026-W28',
      overview: {
        expectedCount: 8,
        submittedCount: 6,
        missingCount: 2,
        leaderCandidateCount: 1
      },
      analysis: {
        week: '2026-W28',
        source: 'manager_report.md',
        content: report,
        isManagerReport: true
      }
    }
  })
}
