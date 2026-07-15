import { describe, expect, it } from 'vitest'
import { buildXlsxBytes, createXlsxBlob, sectionBlocksToRows } from './xlsxExport.js'

describe('XLSX evaluation export', () => {
  it('converts authorized report blocks into left-aligned worksheet rows', () => {
    const rows = sectionBlocksToRows([
      {
        type: 'table',
        headers: ['姓名', 'AI使用红黑榜'],
        rows: [['测试员工甲', '红榜：可复用方案']]
      }
    ])

    expect(rows).toEqual([
      ['姓名', 'AI使用红黑榜'],
      ['测试员工甲', '红榜：可复用方案']
    ])
  })

  it('builds a real dependency-free XLSX zip with workbook and worksheet parts', async () => {
    const rows = [['负责人', '履职结论'], ['测试负责人甲', '完成']]
    const bytes = buildXlsxBytes([{ name: '负责人履职检查', rows }])
    const archiveText = new TextDecoder().decode(bytes)
    const blob = createXlsxBlob({ sheetName: '负责人履职检查', rows })

    expect([...bytes.slice(0, 4)]).toEqual([0x50, 0x4b, 0x03, 0x04])
    expect(archiveText).toContain('xl/workbook.xml')
    expect(archiveText).toContain('xl/worksheets/sheet1.xml')
    expect(archiveText).toContain('测试负责人甲')
    expect(archiveText).toContain('horizontal="left"')
    expect(blob.type).toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    expect((await blob.arrayBuffer()).byteLength).toBe(bytes.length)
  })
})
