import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import FeedbackPreviewPanel from './FeedbackPreviewPanel.vue'

describe('feedback preview panel', () => {
  it('loads one selected week on demand and renders complete employee messages', async () => {
    const client = { request: vi.fn().mockResolvedValue(previewResponse()) }
    const wrapper = mount(FeedbackPreviewPanel, {
      props: { apiClient: client, selectedWeek: '2026-W29' }
    })

    expect(client.request).not.toHaveBeenCalled()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(client.request).toHaveBeenCalledWith('/api/admin/evaluation-feedback-previews/2026-W29')
    expect(wrapper.text()).toContain('发送 1 / 1 人')
    expect(wrapper.text()).toContain('正文摘要一致')
    expect(wrapper.text()).toContain('示例员工甲')
    expect(wrapper.text()).toContain('虚构研发部 · 工程师')
    expect(wrapper.get('pre').text()).toContain('感谢您本周形成了明确交付物')
    expect(wrapper.html()).not.toContain('test-user-001')
  })

  it('clears stale content when the selected week changes', async () => {
    const client = { request: vi.fn().mockResolvedValue(previewResponse()) }
    const wrapper = mount(FeedbackPreviewPanel, {
      props: { apiClient: client, selectedWeek: '2026-W29' }
    })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('pre').exists()).toBe(true)

    await wrapper.setProps({ selectedWeek: '2026-W30' })

    expect(wrapper.get('input').element.value).toBe('2026-W30')
    expect(wrapper.find('pre').exists()).toBe(false)
  })

  it('does not display current content when historical consistency is unknown', async () => {
    const response = previewResponse()
    response.exactMatch = false
    response.warning = '反馈内容在通知完成后发生变化'
    response.notifications = []
    const wrapper = mount(FeedbackPreviewPanel, {
      props: {
        apiClient: { request: vi.fn().mockResolvedValue(response) },
        selectedWeek: '2026-W29'
      }
    })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('正文一致性未确认')
    expect(wrapper.text()).toContain('当前不会展示可能已变化的正文')
    expect(wrapper.find('pre').exists()).toBe(false)
  })

  it('labels legacy time verification while keeping eligible W29 messages visible', async () => {
    const response = previewResponse()
    response.verificationMode = 'LEGACY_TIME'
    response.warning = '该周使用旧版发送状态，正文按反馈文件时间重建'
    const wrapper = mount(FeedbackPreviewPanel, {
      props: {
        apiClient: { request: vi.fn().mockResolvedValue(response) },
        selectedWeek: '2026-W29'
      }
    })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('旧状态时间核对')
    expect(wrapper.text()).toContain('旧版发送状态')
    expect(wrapper.text()).not.toContain('当前不会展示可能已变化的正文')
    expect(wrapper.get('pre').text()).toContain('示例员工甲')
  })

  it('shows safe API errors without retaining a previous response', async () => {
    const client = { request: vi.fn().mockRejectedValue(new Error('该周反馈通知尚未完整发送')) }
    const wrapper = mount(FeedbackPreviewPanel, {
      props: { apiClient: client, selectedWeek: '2026-W30' }
    })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('尚未完整发送')
    expect(wrapper.find('.feedback-review-summary').exists()).toBe(false)
  })
})

function previewResponse() {
  return {
    week: '2026-W29',
    phase: 'COMPLETE',
    eligibleCount: 1,
    sentCount: 1,
    updatedAt: '2026-07-20T04:00:00Z',
    exactMatch: true,
    verificationMode: 'DIGEST',
    warning: '',
    notifications: [{
      name: '示例员工甲',
      department: '虚构研发部',
      title: '工程师',
      markdown: '### 示例员工甲\n\n感谢您本周形成了明确交付物。'
    }]
  }
}
