import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MarkdownReport from './MarkdownReport.vue'

describe('Markdown report presentation', () => {
  it('orders AI highlights around the weekly focus and hides unsafe detail', async () => {
    const wrapper = mount(MarkdownReport, {
      props: {
        variant: 'report',
        content: `# 管理评价

## AI 红榜
- 红榜：可复用方案

## 需要老板拍板/协调事项
- 协调资源
- access_token: fictional-sensitive-token

## AI 黑榜
- 黑榜：未说明效果

## 团队负责人履职检查
- 不合格（下属未提交）
- 确认团队汇总完整性`
      }
    })

    const focus = wrapper.get('[aria-label="本周重点"]')
    const focusText = focus.text()
    expect(focusText.indexOf('协调资源')).toBeLessThan(focusText.indexOf('红榜'))
    expect(focusText.indexOf('红榜')).toBeLessThan(focusText.indexOf('黑榜'))

    const rankings = focus.findAll('.report-block--ai-ranking')
    const redRanking = rankings.find(block => block.text().includes('AI红榜'))
    const blackRanking = rankings.find(block => block.text().includes('AI黑榜'))
    expect(redRanking.classes()).toContain('tone-ai')
    expect(redRanking.get('.ai-ranking-card').classes()).toContain('ai-ranking-card--red')
    expect(redRanking.text()).toContain('可复用方案')
    expect(blackRanking.classes()).toContain('tone-ai-black')
    expect(blackRanking.classes()).not.toContain('tone-ai')
    expect(blackRanking.get('.ai-ranking-card').classes()).toContain('ai-ranking-card--black')
    expect(blackRanking.text()).toContain('未说明效果')
    expect(rankings[0]).toBe(redRanking)
    expect(rankings[1]).toBe(blackRanking)
    const redToggle = redRanking.get('.ai-ranking-toggle')
    const blackToggle = blackRanking.get('.ai-ranking-toggle')
    expect(redToggle.attributes('aria-expanded')).toBe('true')
    expect(blackToggle.attributes('aria-expanded')).toBe('true')
    await redToggle.trigger('click')
    expect(redToggle.attributes('aria-expanded')).toBe('false')
    expect(redRanking.get('.ai-ranking-card__content').isVisible()).toBe(false)
    expect(blackToggle.attributes('aria-expanded')).toBe('true')
    await redRanking.get('.ai-ranking-toggle').trigger('click')
    expect(redRanking.get('.ai-ranking-toggle').attributes('aria-expanded')).toBe('true')
    expect(redRanking.get('.ai-ranking-card__content').attributes('style')).toBe('')
    expect(wrapper.text()).not.toContain('fictional-sensitive-token')
    expect(wrapper.text()).not.toContain('确认团队汇总完整性')

    const leaderToggle = wrapper.get('.report-section-toggle')
    expect(leaderToggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('.report-section-group.is-collapsed .report-section-content').classes()).toContain('is-preview')
    expect(wrapper.html()).toContain('不合格（下属未提交）')
  })

  it('normalizes leader wording in compact summaries too', () => {
    const wrapper = mount(MarkdownReport, {
      props: { variant: 'compact', content: '负责人候选共 2 人。' }
    })

    expect(wrapper.text()).toContain('负责人共 2 人')
    expect(wrapper.text()).not.toContain('负责人候选')
    expect(wrapper.find('.person-report-link').exists()).toBe(false)
  })

  it('links every authorized exact-name occurrence only when people are supplied', async () => {
    const wrapper = mount(MarkdownReport, {
      props: {
        content: `# 示例员工甲评价

示例员工甲完成了虚构交付。

- 示例员工甲下周继续验证。

| 姓名 | 状态 |
| --- | --- |
| 示例员工甲 | 已提交 |`,
        people: [{
          name: '示例员工甲',
          userId: 'test-user-001',
          department: '测试研发部',
          title: '测试岗位'
        }]
      }
    })

    const links = wrapper.findAll('.person-report-link__button')
    expect(links).toHaveLength(4)

    await links[0].trigger('mouseenter')
    expect(document.body.querySelector('[role="tooltip"]')?.textContent)
      .toContain('点击鼠标左键跳转查看 TA 的周报原文')
    await links[0].trigger('click')
    expect(wrapper.emitted('person-select')?.[0]?.[0]).toEqual([
      expect.objectContaining({ name: '示例员工甲', userId: 'test-user-001' })
    ])
  })

  it('adds department and title to generated AI highlight evidence', () => {
    const wrapper = mount(MarkdownReport, {
      props: {
        variant: 'report',
        content: `# 管理评价

## 员工五维评价
| 姓名 | 部门 | 职位 | AI使用红黑榜 |
| --- | --- | --- | --- |
| 测试员工甲 | 虚构研发部 | 工程师 | 红榜：使用工具完成自动化交付 |
| 测试员工乙 | 虚构市场部 | 产品经理 | 黑榜：未说明效果 |

### 测试员工甲
- AI应用：使用脚本自动生成日报并减少重复录入。

### 测试员工乙
- AI效果：未提供可复核的结果数据。
`
      }
    })

    const focusText = wrapper.get('[aria-label="本周重点"]').text()
    expect(focusText).toContain('测试员工甲')
    expect(focusText).toContain('虚构研发部')
    expect(focusText).toContain('工程师')
    expect(focusText).toContain('使用工具完成自动化交付')
    expect(focusText).toContain('AI应用：使用脚本自动生成日报并减少重复录入。')
    expect(focusText).toContain('测试员工乙')
    expect(focusText).toContain('虚构市场部')
    expect(focusText).toContain('产品经理')
    expect(focusText).toContain('未说明效果')
    expect(focusText).toContain('AI效果：未提供可复核的结果数据。')
    expect(wrapper.findAll('.ai-ranking-person')).toHaveLength(2)
    expect(wrapper.findAll('.ai-ranking-row--detail')).toHaveLength(2)
  })

  it('keeps people in weekly focus when employee evaluations use narrative blocks', () => {
    const wrapper = mount(MarkdownReport, {
      props: {
        variant: 'report',
        people: [
          { name: '测试员工甲', userId: 'test-user-001', department: '虚构研发部', title: '工程师' },
          { name: '测试员工乙', userId: 'test-user-002', department: '虚构市场部', title: '产品经理' }
        ],
        content: `# 管理评价

## 需老板拍板/协调事项
| 优先级 | 人员/团队 | 卡点 |
| --- | --- | --- |
| P2 | 虚构研发团队 | 需要统一接口标准 |

## 员工五维评价
### 测试员工甲
- 虚实盘（本周成果）：完成接口脚本。
- AI使用红黑榜：红榜：使用代码助手生成校验脚本，减少重复检查。

### 测试员工乙
- AI使用红黑榜：黑榜：仅写使用工具，未说明场景与效果。
`
      }
    })

    const focusText = wrapper.get('[aria-label="本周重点"]').text()
    expect(focusText).toContain('虚构研发团队')
    expect(focusText).toContain('测试员工甲')
    expect(focusText).toContain('虚构研发部')
    expect(focusText).toContain('工程师')
    expect(focusText).toContain('测试员工乙')
    expect(focusText).toContain('虚构市场部')
    expect(focusText).toContain('产品经理')
    expect(wrapper.findAll('.ai-ranking-person')).toHaveLength(2)
  })
})
