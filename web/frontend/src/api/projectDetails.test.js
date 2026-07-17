import { describe, expect, it, vi } from 'vitest'
import { fetchProjectDetails, normalizeProjectDetails } from './projectDetails.js'

describe('project detail API contract', () => {
  it('requests the selected week without sending report bodies', async () => {
    const client = { request: vi.fn().mockResolvedValue([]) }

    await fetchProjectDetails(client, '2026-W29')

    expect(client.request).toHaveBeenCalledWith('/api/weeks/2026-W29/project-details')
  })

  it('normalizes only the public seven-column data contract', () => {
    const rows = normalizeProjectDetails([{
      sequence: 3,
      productLine: '虚构产品线',
      customerName: '虚构客户',
      projectName: '虚构项目',
      investedDays: '3.5',
      travelExpense: '120',
      hospitalityExpense: '0',
      userid: 'test-user-001',
      department: '虚构研发部'
    }])

    expect(rows).toEqual([{
      sequence: 3,
      productLine: '虚构产品线',
      customerName: '虚构客户',
      projectName: '虚构项目',
      investedDays: '3.5',
      travelExpense: '120',
      hospitalityExpense: '0'
    }])
    expect(rows[0]).not.toHaveProperty('userid')
    expect(rows[0]).not.toHaveProperty('department')
  })
})
