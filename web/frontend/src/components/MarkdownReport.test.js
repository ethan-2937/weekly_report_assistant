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

    const headingBlocks = focus.findAll('.report-block--heading')
    const listBlocks = focus.findAll('.report-block--list')
    const redHeading = headingBlocks.find(block => block.text().includes('AI 红榜'))
    const blackHeading = headingBlocks.find(block => block.text().includes('AI 黑榜'))
    const redContent = listBlocks.find(block => block.text().includes('红榜：可复用方案'))
    const blackContent = listBlocks.find(block => block.text().includes('黑榜：未说明效果'))
    expect(redHeading.classes()).toContain('tone-ai')
    expect(redContent.classes()).toContain('tone-ai')
    expect(blackHeading.classes()).toContain('tone-ai-black')
    expect(blackContent.classes()).toContain('tone-ai-black')
    expect(blackHeading.classes()).not.toContain('tone-ai')
    expect(blackContent.classes()).not.toContain('tone-ai')
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
    expect(focusText).toContain('测试员工甲（虚构研发部｜工程师）：红榜：使用工具完成自动化交付；具体内容：AI应用：使用脚本自动生成日报并减少重复录入。')
    expect(focusText).toContain('测试员工乙（虚构市场部｜产品经理）：黑榜：未说明效果；具体内容：AI效果：未提供可复核的结果数据。')
  })
})
