import { describe, expect, it, vi } from 'vitest'
import { fetchEvaluationFeedbackPreview, normalizeEvaluationFeedbackPreview } from './evaluationFeedbackPreviews.js'

describe('evaluation feedback preview API', () => {
  it('requests one validated week without a report payload', async () => {
    const client = { request: vi.fn().mockResolvedValue({ week: '2026-W29', notifications: [] }) }

    await fetchEvaluationFeedbackPreview(client, '2026-W29')

    expect(client.request).toHaveBeenCalledWith('/api/admin/evaluation-feedback-previews/2026-W29')
  })

  it('rejects unsafe week labels before making a request', async () => {
    const client = { request: vi.fn() }

    await expect(fetchEvaluationFeedbackPreview(client, '../../output')).rejects.toThrow(TypeError)
    expect(client.request).not.toHaveBeenCalled()
  })

  it('keeps only public preview fields', () => {
    const preview = normalizeEvaluationFeedbackPreview({
      week: '2026-W29',
      phase: 'COMPLETE',
      eligibleCount: 1,
      sentCount: 1,
      exactMatch: true,
      verificationMode: 'DIGEST',
      notifications: [{
        name: '示例员工甲',
        department: '虚构研发部',
        title: '工程师',
        markdown: '虚构通知正文',
        userid: 'test-user-001'
      }]
    })

    expect(preview.notifications[0]).toEqual({
      name: '示例员工甲',
      department: '虚构研发部',
      title: '工程师',
      markdown: '虚构通知正文'
    })
    expect(preview.verificationMode).toBe('DIGEST')
    expect(JSON.stringify(preview)).not.toContain('test-user-001')
  })
})
