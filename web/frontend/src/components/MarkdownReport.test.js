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

    const focusText = wrapper.get('[aria-label="本周重点"]').text()
    expect(focusText.indexOf('红榜')).toBeLessThan(focusText.indexOf('协调资源'))
    expect(focusText.indexOf('协调资源')).toBeLessThan(focusText.indexOf('黑榜'))
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
})
