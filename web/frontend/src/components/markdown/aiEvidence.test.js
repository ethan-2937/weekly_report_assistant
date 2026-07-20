import { describe, expect, it } from 'vitest'
import { collectAiEvidence, collectPersonDetails } from './aiEvidence.js'

describe('AI evidence extraction', () => {
  it('does not guess department or title for duplicate authorized names', () => {
    const sections = [{
      title: '员工四维评价',
      focus: false,
      blocks: [
        { type: 'heading', level: 3, text: '测试同名员工' },
        { type: 'list', items: ['AI使用红黑榜：红榜：使用代码助手生成测试脚本。'] }
      ]
    }]
    const people = [
      { name: '测试同名员工', userId: 'test-user-001', department: '虚构研发一部', title: '工程师' },
      { name: '测试同名员工', userId: 'test-user-002', department: '虚构研发二部', title: '工程师' }
    ]

    const evidence = collectAiEvidence(sections, 'red', collectPersonDetails(sections), people)

    expect(evidence).toEqual(['测试同名员工：红榜：使用代码助手生成测试脚本。'])
    expect(evidence.join(' ')).not.toContain('虚构研发一部')
    expect(evidence.join(' ')).not.toContain('虚构研发二部')
  })

  it('recognizes improvement wording in an AI field as black-list evidence', () => {
    const sections = [{
      title: '员工四维评价',
      focus: false,
      blocks: [{
        type: 'table',
        headers: ['姓名', 'AI用得怎样（红黑榜）'],
        rows: [['测试员工乙', '需改进：未说明工具、应用场景与使用效果']]
      }]
    }]

    expect(collectAiEvidence(sections, 'black')).toEqual([
      '测试员工乙：需改进：未说明工具、应用场景与使用效果'
    ])
  })
})
