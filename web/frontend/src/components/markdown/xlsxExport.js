const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
const encoder = new TextEncoder()
let crcTable

export function sectionBlocksToRows(blocks = []) {
  const rows = []
  for (const block of blocks) {
    if (block.type === 'table') {
      appendGroup(rows, [block.headers, ...block.rows])
    } else if (block.type === 'list') {
      appendGroup(rows, [['内容'], ...block.items.map(item => [item])])
    } else if (block.type === 'ordered-list') {
      appendGroup(rows, [['序号', '内容'], ...block.items.map((item, index) => [`${index + 1}`, item])])
    } else if (['paragraph', 'quote', 'code'].includes(block.type) && block.text) {
      appendGroup(rows, [['内容'], [block.text]])
    }
  }
  return rows.length ? rows : [['内容'], ['暂无可导出内容']]
}

export function createXlsxBlob({ sheetName, rows }) {
  return new Blob([buildXlsxBytes([{ name: sheetName, rows }])], { type: XLSX_MIME })
}

export function downloadXlsx({ filename, sheetName, rows }) {
  const blob = createXlsxBlob({ sheetName, rows })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = safeFilename(filename)
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function buildXlsxBytes(sheets) {
  const normalized = normalizeSheets(sheets)
  const files = [
    ['[Content_Types].xml', contentTypesXml(normalized.length)],
    ['_rels/.rels', rootRelationshipsXml()],
    ['xl/workbook.xml', workbookXml(normalized)],
    ['xl/_rels/workbook.xml.rels', workbookRelationshipsXml(normalized.length)],
    ['xl/styles.xml', stylesXml()],
    ...normalized.map((sheet, index) => [`xl/worksheets/sheet${index + 1}.xml`, worksheetXml(sheet.rows)])
  ]
  return createStoredZip(files.map(([name, text]) => ({ name, bytes: encoder.encode(text) })))
}

function appendGroup(target, group) {
  if (!group.length) return
  if (target.length) target.push([])
  target.push(...group.map(row => row.map(value => String(value ?? ''))))
}

function normalizeSheets(sheets) {
  const used = new Set()
  return sheets.map((sheet, index) => {
    const base = safeSheetName(sheet.name || `Sheet${index + 1}`)
    let name = base
    let suffix = 2
    while (used.has(name)) name = `${base.slice(0, 28)}-${suffix++}`
    used.add(name)
    const rows = Array.isArray(sheet.rows) && sheet.rows.length ? sheet.rows : [['内容'], ['暂无可导出内容']]
    return { name, rows: rows.map(row => row.map(value => String(value ?? ''))) }
  })
}

function safeSheetName(value) {
  return String(value).replace(/[\\/*?:[\]]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 31) || 'Sheet1'
}

function safeFilename(value) {
  const stem = String(value || '周报评价').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-').trim() || '周报评价'
  return stem.toLowerCase().endsWith('.xlsx') ? stem : `${stem}.xlsx`
}

function contentTypesXml(sheetCount) {
  const sheets = Array.from({ length: sheetCount }, (_, index) =>
    `<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`
  ).join('')
  return xml(`
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  ${sheets}
</Types>`)
}

function rootRelationshipsXml() {
  return xml(`
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`)
}

function workbookXml(sheets) {
  const nodes = sheets.map((sheet, index) =>
    `<sheet name="${escapeXml(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>`
  ).join('')
  return xml(`
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>${nodes}</sheets>
</workbook>`)
}

function workbookRelationshipsXml(sheetCount) {
  const sheets = Array.from({ length: sheetCount }, (_, index) =>
    `<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>`
  ).join('')
  return xml(`
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  ${sheets}
  <Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`)
}

function stylesXml() {
  return xml(`
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2"><font><sz val="11"/><name val="Aptos"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Aptos"/></font></fonts>
  <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF34495E"/><bgColor indexed="64"/></patternFill></fill></fills>
  <borders count="2"><border/><border><left style="thin"><color rgb="FFDCE4EC"/></left><right style="thin"><color rgb="FFDCE4EC"/></right><top style="thin"><color rgb="FFDCE4EC"/></top><bottom style="thin"><color rgb="FFDCE4EC"/></bottom></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyAlignment="1"><alignment horizontal="left" vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="2" borderId="1" applyAlignment="1"><alignment horizontal="left" vertical="center" wrapText="1"/></xf></cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>`)
}

function worksheetXml(rows) {
  const widthCount = Math.max(1, ...rows.map(row => row.length))
  const widths = Array.from({ length: widthCount }, (_, column) => {
    const width = Math.min(52, Math.max(12, ...rows.map(row => displayWidth(row[column] || '') + 2)))
    return `<col min="${column + 1}" max="${column + 1}" width="${width}" customWidth="1"/>`
  }).join('')
  const data = rows.map((row, rowIndex) => {
    const cells = row.map((value, columnIndex) => {
      const reference = `${columnName(columnIndex + 1)}${rowIndex + 1}`
      return `<c r="${reference}" s="${rowIndex === 0 ? 1 : 0}" t="inlineStr"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>`
    }).join('')
    return `<row r="${rowIndex + 1}">${cells}</row>`
  }).join('')
  return xml(`
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
  <cols>${widths}</cols><sheetData>${data}</sheetData>
  <autoFilter ref="A1:${columnName(widthCount)}${Math.max(1, rows.length)}"/>
</worksheet>`)
}

function xml(body) {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>${body.replace(/>\s+</g, '><').trim()}`
}

function escapeXml(value) {
  return String(value).replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function displayWidth(value) {
  return Array.from(String(value)).reduce((sum, char) => sum + (/[^\u0000-\u00ff]/.test(char) ? 2 : 1), 0)
}

function columnName(index) {
  let name = ''
  while (index > 0) {
    index -= 1
    name = String.fromCharCode(65 + (index % 26)) + name
    index = Math.floor(index / 26)
  }
  return name
}

function createStoredZip(files) {
  const localParts = []
  const centralParts = []
  let offset = 0
  for (const file of files) {
    const name = encoder.encode(file.name)
    const checksum = crc32(file.bytes)
    const local = concatBytes([u32(0x04034b50), u16(20), u16(0), u16(0), u16(0), u16(33), u32(checksum), u32(file.bytes.length), u32(file.bytes.length), u16(name.length), u16(0), name, file.bytes])
    const central = concatBytes([u32(0x02014b50), u16(20), u16(20), u16(0), u16(0), u16(0), u16(33), u32(checksum), u32(file.bytes.length), u32(file.bytes.length), u16(name.length), u16(0), u16(0), u16(0), u16(0), u32(0), u32(offset), name])
    localParts.push(local)
    centralParts.push(central)
    offset += local.length
  }
  const central = concatBytes(centralParts)
  const end = concatBytes([u32(0x06054b50), u16(0), u16(0), u16(files.length), u16(files.length), u32(central.length), u32(offset), u16(0)])
  return concatBytes([...localParts, central, end])
}

function crc32(bytes) {
  if (!crcTable) crcTable = createCrcTable()
  let crc = 0xffffffff
  for (const byte of bytes) crc = (crc >>> 8) ^ crcTable[(crc ^ byte) & 0xff]
  return (crc ^ 0xffffffff) >>> 0
}

function createCrcTable() {
  return Array.from({ length: 256 }, (_, index) => {
    let value = index
    for (let bit = 0; bit < 8; bit += 1) value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1)
    return value >>> 0
  })
}

function u16(value) { return Uint8Array.of(value & 0xff, (value >>> 8) & 0xff) }
function u32(value) { return Uint8Array.of(value & 0xff, (value >>> 8) & 0xff, (value >>> 16) & 0xff, (value >>> 24) & 0xff) }
function concatBytes(parts) {
  const result = new Uint8Array(parts.reduce((sum, part) => sum + part.length, 0))
  let offset = 0
  for (const part of parts) { result.set(part, offset); offset += part.length }
  return result
}
