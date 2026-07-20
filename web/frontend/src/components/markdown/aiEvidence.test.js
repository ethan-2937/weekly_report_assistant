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

  it('recognizes current evaluation labels in an AI field as black-list evidence', () => {
    const sections = [{
      title: '员工四维评价',
      focus: false,
      blocks: [{
        type: 'table',
        headers: ['姓名', 'AI用得怎样（红黑榜）'],
        rows: [
          ['测试员工甲', '黑榜：仅写代码助手协助'],
          ['测试员工乙', '未使用：AI栏为横线'],
          ['测试员工丙', '黑榜：只列工具名称'],
          ['测试员工丁', '需改进：有测试场景，缺工具和效果'],
          ['测试员工戊', '未使用：明确填无']
        ]
      }]
    }]

    expect(collectAiEvidence(sections, 'black')).toEqual([
      '测试员工甲：黑榜：仅写代码助手协助',
      '测试员工乙：未使用：AI栏为横线',
      '测试员工丙：黑榜：只列工具名称',
      '测试员工丁：需改进：有测试场景，缺工具和效果',
      '测试员工戊：未使用：明确填无'
    ])
  })
})
