export interface PlanTable {
  headers: string[]
  rows: string[][]
}

export interface PlanSection {
  title: string
  level: number
  rawLines: string[]
  paragraphs: string[]
  bullets: string[]
  tables: PlanTable[]
}

const STRATEGY_TITLES = [
  '核心目标与 KPI',
  '策略标签',
  '天气、热门事件与周边机会判断',
  '执行时间表',
  '各渠道内容计划',
  '活动与定价承接',
  '核心文案示例',
  '底部执行动作',
]

const PRICING_TITLES = [
  '经营结论摘要',
  '逐房型定价执行表',
  '可执行动作清单',
  '需求与价格信号图表',
  '数据来源与可信度',
  '风险核验与复盘指标',
]

const ALL_TITLES = [...STRATEGY_TITLES, ...PRICING_TITLES]

const TABLE_HEADERS: Array<{ pattern: RegExp; headers: string[] }> = [
  { pattern: /核心目标|KPI|指标|目标/, headers: ['指标', '目标值', '依据', '待核实'] },
  { pattern: /天气|热门事件|周边|机会判断|综合分析|环境机会/, headers: ['信号', '实际情况', '对客群/需求的影响', '策略调整', '原因'] },
  { pattern: /执行时间表|执行时间|阶段|节奏/, headers: ['阶段', '时间', '重点', '具体动作', '渠道/负责人'] },
  { pattern: /各渠道内容计划|渠道|内容计划|发布计划/, headers: ['渠道', '定位', '依据', '内容主题', '承接动作', '目标'] },
  { pattern: /活动|定价|承接/, headers: ['项目', '当前依据', '建议动作', '执行条件', '说明原因'] },
  { pattern: /风险核验|复盘指标/, headers: ['事项', '核验方法', '负责人', '时间点', '复盘指标'] },
  { pattern: /底部执行动作|执行动作|按钮/, headers: ['actionKey', '按钮文案', '执行内容', '调用模块'] },
  { pattern: /逐房型定价执行表/, headers: ['房型', '当前挂牌价', '建议价区间', '最低保护价', '渠道动作', '执行时点', '风险'] },
  { pattern: /可执行动作清单/, headers: ['actionKey', 'roomName', '按钮文案', 'targetPrice', 'channel', 'status'] },
  { pattern: /需求与价格信号图表/, headers: ['信号', '当前判断', '依据', '对价格影响', '待核实'] },
  { pattern: /数据来源与可信度/, headers: ['来源', '数据', '用途', '可信度', '待核实'] },
]

export function cleanMarkdown(value: string) {
  return String(value || '')
    .replace(/\*\*/g, '')
    .replace(/`/g, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/\u00a0/g, ' ')
    .trim()
}

function extractTextPayload(value: unknown, seen = new Set<unknown>()): string {
  if (!value) return ''
  if (typeof value === 'string') return value
  if (typeof value !== 'object') return ''
  if (seen.has(value)) return ''
  seen.add(value)

  if (Array.isArray(value)) {
    return value.map(item => extractTextPayload(item, seen)).filter(Boolean).join('\n')
  }

  const record = value as Record<string, unknown>
  const preferredKeys = ['output', 'content', 'answer', 'text', 'body', 'result', 'message']
  for (const key of preferredKeys) {
    const text = extractTextPayload(record[key], seen)
    if (text) return text
  }
  return ''
}

function rawPlanMarkdown(markdown: string) {
  const value = String(markdown || '').trim()
  if (!value) return ''
  try {
    return extractTextPayload(JSON.parse(value)) || value
  } catch {
    return value
  }
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function splitKnownTitles(markdown: string) {
  let output = markdown
  ALL_TITLES.forEach(title => {
    const escaped = escapeRegExp(title).replace(/\s+/g, '\\s*')
    output = output
      .replace(new RegExp(`(^|\\n)\\s*#{0,3}\\s*(${escaped})(?=\\s*[-:：]|\\s*$)`, 'g'), `$1## ${title}`)
      .replace(new RegExp(`([^#\\n])(${escaped})(?=\\s*[-:：]|\\s*$|\\|)`, 'g'), `$1\n## ${title}`)
      .replace(new RegExp(`(##\\s*${escaped})\\s*[-:：]\\s*`, 'g'), `## ${title}\n`)
  })
  return output
}

export function normalizePlanMarkdown(markdown: string) {
  return splitKnownTitles(rawPlanMarkdown(markdown))
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/<think>[\s\S]*$/gi, '')
    .replace(/^\s*```(?:json|markdown|md)?/i, '')
    .replace(/```\s*$/i, '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/(?!^)(#{1,3})(?=[^\s#])/g, '\n$1 ')
    .replace(/(#{1,3}\s*[^#\n|]{1,40})\|(?=[^|\n]+\|)/g, '$1\n|')
    .replace(/([^\n])(\|[^|\n]+\|[^|\n]+\|)/g, '$1\n$2')
}

function splitTableRow(line: string) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map(cell => cleanMarkdown(cell))
}

function splitLooseCells(line: string) {
  const value = cleanMarkdown(line)
  if (!value) return []
  if (value.includes('|')) return splitTableRow(value).filter(Boolean)
  if (/\t/.test(value)) return value.split(/\t+/).map(cell => cleanMarkdown(cell)).filter(Boolean)
  return [value]
}

function isTableSeparator(line: string) {
  return /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?\s*$/.test(line)
}

function isDivider(line: string) {
  return /^\s*-{3,}\s*$/.test(line)
}

function isUsefulTableHeader(headers: string[]) {
  const usefulCells = headers.filter(cell => cell && !/^:?-{2,}:?$/.test(cell))
  return usefulCells.length >= 2
}

function normalizeHeaderCell(value: string) {
  return cleanMarkdown(value).replace(/\s+/g, '').toLowerCase()
}

function isHeaderLikeRow(headers: string[], row: string[]) {
  const normalizedHeaders = headers.map(normalizeHeaderCell).filter(Boolean)
  const cells = row.map(normalizeHeaderCell).filter(Boolean)
  if (!cells.length || !normalizedHeaders.length) return false
  const headerCellCount = cells.filter(cell => normalizedHeaders.includes(cell)).length
  return headerCellCount >= Math.min(cells.length, Math.max(2, Math.ceil(normalizedHeaders.length * 0.6)))
}

function expectedHeaders(section: PlanSection) {
  return TABLE_HEADERS.find(item => item.pattern.test(section.title))?.headers
}

function isHeaderNoise(line: string, headers: string[]) {
  const cleaned = cleanMarkdown(line)
  if (!cleaned || isDivider(cleaned) || isTableSeparator(cleaned)) return true
  const cells = splitLooseCells(cleaned)
  if (!cells.length) return true
  return cells.every(cell =>
    headers.some(header => header.toLowerCase() === cell.toLowerCase())
    || /^:?-{2,}:?$/.test(cell)
    || cell === '#',
  )
}

function padRow(row: string[], length: number) {
  const next = row.slice(0, length)
  while (next.length < length) next.push('')
  return next
}

function appendToCell(cells: string[], index: number, value: string) {
  const target = Math.max(0, Math.min(index, cells.length - 1))
  cells[target] = [cells[target], cleanMarkdown(value)].filter(Boolean).join('\n')
}

function rowAppendIndex(headers: string[]) {
  const names = ['具体动作', '内容主题', '执行内容', '建议动作', '渠道动作', '依据']
  const index = headers.findIndex(header => names.some(name => header.includes(name)))
  return index >= 0 ? index : Math.max(0, headers.length - 2)
}

function parseLooseTable(section: PlanSection, headers: string[]) {
  const source = section.rawLines
    .map(line => cleanMarkdown(line))
    .filter(line => !isHeaderNoise(line, headers))

  const rows: string[][] = []
  let index = 0

  while (index < source.length) {
    const line = source[index]
    const cells = splitLooseCells(line)
    if (!cells.length) {
      index += 1
      continue
    }

    if (cells.length >= headers.length) {
      rows.push(padRow(cells, headers.length))
      index += 1
      continue
    }

    const row = cells.slice()
    index += 1

    while (index < source.length && row.length < headers.length) {
      const nextCells = splitLooseCells(source[index])
      if (nextCells.length >= headers.length) break
      if (nextCells.length > 1 && row.length + nextCells.length <= headers.length) {
        row.push(...nextCells)
      } else if (nextCells.length > 1) {
        const room = headers.length - row.length
        row.push(...nextCells.slice(0, room))
        if (nextCells.length > room) appendToCell(row, rowAppendIndex(headers), nextCells.slice(room).join('\n'))
      } else if (nextCells.length === 1) {
        const nextLine = source[index + 1] || ''
        const followingCells = splitLooseCells(nextLine)
        if (row.length >= headers.length - 1 || followingCells.length > 1) {
          appendToCell(row, rowAppendIndex(headers), nextCells[0])
        } else {
          row.push(nextCells[0])
        }
      }
      index += 1
    }

    rows.push(padRow(row, headers.length))
  }

  return rows.filter(row => row.some(Boolean) && !isHeaderLikeRow(headers, row))
}

function pushLooseHeadingContent(section: PlanSection) {
  const tagMatch = section.title.match(/^(策略标签)\s*[-:：]\s*(.+)$/)
  if (tagMatch) {
    section.title = tagMatch[1]
    tagMatch[2]
      .split(/[-、，,]/)
      .map(item => cleanMarkdown(item))
      .filter(Boolean)
      .forEach(item => section.bullets.push(item))
  }
}

function parseStandardTable(lines: string[], startIndex: number) {
  const headers = splitTableRow(lines[startIndex])
  if (!isUsefulTableHeader(headers)) return null
  const rawRows: string[] = []
  let index = startIndex + 2

  while (index < lines.length) {
    const rowLine = lines[index].trim()
    if (!rowLine) {
      index += 1
      continue
    }
    if (isTableSeparator(rowLine) || isDivider(rowLine)) {
      index += 1
      continue
    }
    if (rowLine.startsWith('|')) {
      rawRows.push(rowLine)
      index += 1
      continue
    }
    if (rawRows.length && !/^[-*]\s+/.test(rowLine) && !/^\d+[.)]\s+/.test(rowLine)) {
      rawRows[rawRows.length - 1] += `<br>${rowLine}`
      index += 1
      continue
    }
    break
  }

  return {
    table: {
      headers,
      rows: rawRows
        .map(row => padRow(splitTableRow(row), headers.length))
        .filter(row => row.some(Boolean) && !isHeaderLikeRow(headers, row)),
    },
    nextIndex: index,
  }
}

function parseSectionContent(section: PlanSection) {
  const lines = section.rawLines
  let index = 0
  while (index < lines.length) {
    const line = lines[index].trim()
    if (!line) {
      index += 1
      continue
    }

    if (isDivider(line) || isTableSeparator(line)) {
      index += 1
      continue
    }

    if (line.includes('|') && lines[index + 1] && isTableSeparator(lines[index + 1])) {
      const parsed = parseStandardTable(lines, index)
      if (parsed) {
        section.tables.push(parsed.table)
        index = parsed.nextIndex
        continue
      }
    }

    const bullet = line.match(/^[-*]\s+(.+)$|^\d+[.)]\s+(.+)$/)
    if (bullet) {
      section.bullets.push(cleanMarkdown(bullet[1] || bullet[2]))
      index += 1
      continue
    }

    section.paragraphs.push(cleanMarkdown(line))
    index += 1
  }

  pushLooseHeadingContent(section)

  const headers = expectedHeaders(section)
  if (headers && !section.tables.length) {
    const looseRows = parseLooseTable(section, headers)
    if (looseRows.length) {
      section.tables.push({ headers, rows: looseRows })
      section.paragraphs = []
    }
  }
}

export function parsePlan(markdown: string) {
  const sections: PlanSection[] = []
  let current: PlanSection | null = null
  const lines = normalizePlanMarkdown(markdown).split('\n')

  lines.forEach(rawLine => {
    const line = rawLine.trim()
    if (/^#{1,3}\s*$/.test(line)) return

    const heading = line.match(/^(#{1,3})\s*(.+)$/)
    if (heading) {
      current = {
        title: cleanMarkdown(heading[2]),
        level: heading[1].length,
        rawLines: [],
        paragraphs: [],
        bullets: [],
        tables: [],
      }
      sections.push(current)
      return
    }

    const knownTitle = ALL_TITLES.find(title => line === title)
    if (knownTitle) {
      current = {
        title: knownTitle,
        level: 2,
        rawLines: [],
        paragraphs: [],
        bullets: [],
        tables: [],
      }
      sections.push(current)
      return
    }

    if (!current && !line) return

    if (!current) {
      current = {
        title: '方案概览',
        level: 2,
        rawLines: [],
        paragraphs: [],
        bullets: [],
        tables: [],
      }
      sections.push(current)
    }
    current.rawLines.push(rawLine)
  })

  sections.forEach(parseSectionContent)
  return sections.filter(section =>
    section.title !== '方案概览'
    || section.paragraphs.length
    || section.bullets.length
    || section.tables.length,
  )
}

export function sectionByTitle(sections: PlanSection[], patterns: RegExp[]) {
  return sections.find(section => patterns.some(pattern => pattern.test(section.title)))
}

export function firstTable(section?: PlanSection) {
  return section?.tables?.[0]
}

export function columnIndex(table: PlanTable, names: string[], fallback: number) {
  const index = table.headers.findIndex(header => names.some(name => header.includes(name)))
  return index >= 0 ? index : fallback
}
