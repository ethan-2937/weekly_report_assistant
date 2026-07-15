import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
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
    expect(focusText).toContain('测试员工甲')
    expect(focusText).toContain('入选结论可复用方案')
    expect(focusText).toContain('测试员工乙')
    expect(focusText).toContain('问题结论未说明效果')
    expect(focusText.indexOf('明确最低填写标准')).toBeLessThan(focusText.indexOf('AI红榜'))
    expect(focusText.indexOf('AI红榜')).toBeLessThan(focusText.indexOf('AI黑榜'))

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

  it('loads one authorized report on demand and renders source text safely', async () => {
    const loadPersonReport = vi.fn().mockResolvedValue({
      week: '2026-W28',
      name: '测试员工甲',
      department: '虚构研发部',
      title: '测试工程师',
      status: '已提交',
      submittedAt: '2026-07-10 10:00:00',
      available: true,
      message: '',
      fields: [{ label: '本周完成成果', value: '<img src=x onerror=alert(1)>虚构交付' }]
    })
    const wrapper = createWrapper({
      submissionRows: [personRow('测试员工甲', 'test-user-001', '虚构研发部')],
      loadPersonReport
    })

    const personLink = wrapper.findAll('.person-report-link__button')
      .find(link => link.text() === '测试员工甲')
    expect(personLink).toBeTruthy()
    expect(wrapper.text()).not.toContain('虚构交付')

    await personLink.trigger('click')
    await flushPromises()

    expect(loadPersonReport).toHaveBeenCalledOnce()
    expect(loadPersonReport).toHaveBeenCalledWith('2026-W28', 'test-user-001')
    expect(wrapper.get('[role="dialog"]').text()).toContain('虚构交付')
    expect(wrapper.find('[role="dialog"] img').exists()).toBe(false)
  })

  it('requires explicit selection for duplicate names instead of guessing a userid', async () => {
    const loadPersonReport = vi.fn().mockResolvedValue({
      week: '2026-W28',
      name: '测试员工甲',
      status: '未提交',
      available: false,
      message: '该成员本周未提交，没有可查看的周报原文。',
      fields: []
    })
    const wrapper = createWrapper({
      submissionRows: [
        personRow('测试员工甲', 'test-user-001', '虚构研发一部'),
        personRow('测试员工甲', 'test-user-002', '虚构研发二部')
      ],
      loadPersonReport
    })

    await wrapper.findAll('.person-report-link__button')
      .find(link => link.text() === '测试员工甲')
      .trigger('click')

    expect(loadPersonReport).not.toHaveBeenCalled()
    const choices = wrapper.findAll('.report-source-picker button')
    expect(choices).toHaveLength(2)
    expect(wrapper.get('.report-source-picker').text()).toContain('虚构研发一部')
    expect(wrapper.get('.report-source-picker').text()).toContain('虚构研发二部')

    await choices[1].trigger('click')
    await flushPromises()
    expect(loadPersonReport).toHaveBeenCalledWith('2026-W28', 'test-user-002')
  })
})

function createWrapper(extraProps = {}) {
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
      },
      ...extraProps
    },
    global: { stubs: { teleport: true } }
  })
}

function personRow(name, userId, department) {
  return {
    '姓名': name,
    userid: userId,
    '部门': department,
    '职务': '测试工程师',
    '提交状态': '已提交'
  }
}
