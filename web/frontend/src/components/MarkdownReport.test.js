import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MarkdownReport from './MarkdownReport.vue'

describe('Markdown report presentation', () => {
  it('orders AI highlights around the weekly focus and hides unsafe detail', () => {
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
    expect(focus.get('.report-section-content').classes()).toContain('has-ai-ranking-pair')
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
})
