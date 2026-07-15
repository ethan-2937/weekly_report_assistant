import { describe, expect, it, vi } from 'vitest'
import { fetchWeeklyReportDetail, normalizeWeeklyReportDetail } from './reports.js'

describe('weekly report detail API', () => {
  it('requests only one encoded person report', async () => {
    const client = { request: vi.fn().mockResolvedValue({}) }

    await fetchWeeklyReportDetail(client, '2026-W28', 'test-user+001')

    expect(client.request).toHaveBeenCalledOnce()
    expect(client.request).toHaveBeenCalledWith('/api/weeks/2026-W28/reports/test-user%2B001')
  })

  it('keeps only the bounded display contract', () => {
    const normalized = normalizeWeeklyReportDetail({
      week: '2026-W28',
      name: '示例员工甲',
      available: true,
      fields: [{ label: '本周完成成果', value: '虚构交付内容', internalPath: 'ignored' }],
      rawReports: ['不应保留的原始批量数据']
    })

    expect(normalized.fields).toEqual([{ label: '本周完成成果', value: '虚构交付内容' }])
    expect(normalized).not.toHaveProperty('rawReports')
    expect(normalized.fields[0]).not.toHaveProperty('internalPath')
  })
})
