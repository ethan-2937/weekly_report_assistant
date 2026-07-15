import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EvaluationView from './EvaluationView.vue'

const report = `# 管理评价

## 需要老板拍板/协调事项
| 优先级 | 人员/团队 | 卡点 | 需要支持 | 建议时限 |
| --- | --- | --- | --- | --- |
| P2 | AI使用治理 | AI填写质量待提升 | 明确最低填写标准 | 下周周报前 |

## 员工五维评价
| 姓名 | 虚实盘（本周成果） | 时间分配健康度 | AI使用红黑榜 | 下周计划合格性 | 综合结论/需跟进 |
| --- | --- | --- | --- | --- | --- |
| 测试员工甲 | 完成 | 健康 | 红榜：可复用方案 | 合格 | 关注交付节奏 |
| 测试员工乙 | 完成 | 健康 | 黑榜：未说明效果 | 合格 | 关注 AI 效果 |

## 团队负责人履职检查
| 负责人 | 管理团队 | 履职结论 |
| --- | --- | --- |
| 测试负责人甲 | 虚构研发部 | 完成 |
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
    const focusText = wrapper.get('[aria-label="本周重点"]').text()
    expect(focusText).toContain('测试员工甲：红榜：可复用方案')
    expect(focusText).toContain('测试员工乙：黑榜：未说明效果')
    expect(focusText.indexOf('明确最低填写标准')).toBeLessThan(focusText.indexOf('AI 红榜'))
    expect(focusText.indexOf('AI 红榜')).toBeLessThan(focusText.indexOf('AI 黑榜'))

    const aiCells = wrapper.findAll('.report-cell.is-ai')
    const redCell = aiCells.find(cell => cell.text().includes('红榜：可复用方案'))
    const blackCell = aiCells.find(cell => cell.text().includes('黑榜：未说明效果'))
    expect(redCell.classes()).toContain('is-success')
    expect(redCell.classes()).toContain('is-ai-red')
    expect(redCell.classes()).not.toContain('is-danger')
    expect(blackCell.classes()).toContain('is-danger')
    expect(blackCell.classes()).toContain('is-ai-black')
    expect(blackCell.classes()).not.toContain('is-success')
    expect(wrapper.text()).toContain('员工四维评价')
    expect(wrapper.text()).toContain('综合结论/需跟进')
  })

  it('keeps employee and leader sections collapsed until explicitly expanded', async () => {
    const wrapper = createWrapper()
    const toggles = wrapper.findAll('.report-section-toggle')

    expect(toggles.map(toggle => toggle.get('strong').text())).toEqual([
      '员工四维评价',
      '团队负责人履职检查'
    ])
    expect(toggles.every(toggle => toggle.attributes('aria-expanded') === 'false')).toBe(true)
    expect(wrapper.findAll('.report-section-content.is-preview')).toHaveLength(2)
    expect(wrapper.findAll('.report-section-export')).toHaveLength(2)
    expect(wrapper.findAll('.browser-download-hint__tooltip')).toHaveLength(2)

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
