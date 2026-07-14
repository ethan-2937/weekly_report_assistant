import { describe, expect, it, vi } from 'vitest'
import {
  DETAIL_EVALUATION_DIMENSION,
  evaluationDimensions,
  fetchWeekAnalysis,
  fetchWeeks,
  fetchWeekSubmissionStatus,
  fetchWeekSummary
} from './weeks.js'

describe('weekly report API contract', () => {
  it('delegates every weekly endpoint to the injected request client', async () => {
    const client = { request: vi.fn().mockResolvedValue({}) }

    await fetchWeeks(client)
    await fetchWeekSummary(client, '2026-W28')
    await fetchWeekAnalysis(client, '2026-W28')
    await fetchWeekSubmissionStatus(client, '2026-W28')

    expect(client.request.mock.calls).toEqual([
      ['/api/weeks'],
      ['/api/weeks/2026-W28/summary'],
      ['/api/weeks/2026-W28/analysis'],
      ['/api/weeks/2026-W28/submission-status']
    ])
    expect(fetch).not.toHaveBeenCalled()
  })

  it('keeps four primary dimensions while retaining the conclusion in detail', () => {
    const primary = evaluationDimensions()
    const withDetail = evaluationDimensions({ includeDetail: true })

    expect(primary).toHaveLength(4)
    expect(primary.map(item => item.key)).toEqual([
      'outcomes',
      'timeAllocation',
      'aiUsage',
      'nextWeekPlan'
    ])
    expect(primary).not.toContainEqual(DETAIL_EVALUATION_DIMENSION)
    expect(withDetail).toHaveLength(5)
    expect(withDetail.at(-1)).toEqual(DETAIL_EVALUATION_DIMENSION)
  })
})
