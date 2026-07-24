import * as XLSX from 'xlsx'
import { collectStreamContentWithFile } from '@/api/content'

export interface OccupancyRecord {
  date: string
  roomTypeName: string
  totalRooms: number
  occupiedRooms: number
  remainingRooms: number
  occupancyRate: number
}

export interface RoomOccupancySummary {
  roomTypeName: string
  totalRooms: number
  days: number
  occupiedRoomNights: number
  remainingRoomNights: number
  averageOccupancyRate: number
  latestOccupiedRooms: number
  latestRemainingRooms: number
}

export interface OccupancyImportData {
  sourceFileName: string
  sourceFileNames?: string[]
  importedAt: string
  dateRange: string
  records: OccupancyRecord[]
  roomTypeSummaries: RoomOccupancySummary[]
  averageOccupancyRate: number
  totalRoomNights: number
  occupiedRoomNights: number
  remainingRoomNights: number
}

export interface OccupancyMergeReport {
  added: number
  duplicates: number
  conflicts: number
  total: number
  skippedConflicts: number
  conflictSamples: Array<{
    date: string
    roomTypeName: string
    existing: OccupancyRecord
    incoming: OccupancyRecord
  }>
}

type CellValue = string | number | Date
type CellRows = Array<Array<CellValue>>

const occupiedKeys = ['占用房', '已占', '入住', '售出', '出租房']
const remainingKeys = ['剩余可售', '可售', '剩余', '余房']
const rateKeys = ['出租率', '入住率', '占用率']

export async function parseOccupancyFile(file: File): Promise<OccupancyImportData> {
  const buffer = await file.arrayBuffer()
  const workbook = XLSX.read(buffer, { type: 'array', cellDates: true })
  const sheetName = workbook.SheetNames[0]
  const sheet = workbook.Sheets[sheetName]
  if (!sheet) throw new Error('没有读取到表格内容')

  const rows = fillMergedCells(
    XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '' }) as CellRows,
    sheet['!merges'] || []
  )
  const records = parseOccupancyRows(rows)
  if (!records.length) {
    throw new Error('没有识别到房型、占用房、剩余可售、出租率等经营数据')
  }

  return buildImportData(file.name, records)
}

export async function parseOccupancyImage(file: File): Promise<OccupancyImportData> {
  if (!file.type.startsWith('image/')) {
    throw new Error('请上传 PNG、JPG 或 WebP 图片')
  }
  if (file.size > 10 * 1024 * 1024) {
    throw new Error('图片不能超过 10MB，请先压缩后再上传')
  }

  let content = ''
  try {
    content = await collectStreamContentWithFile('occupancy_image', {
      sourceFileName: file.name,
      sourceFileType: file.type,
      sourceFileSize: file.size,
      message: '识别上传的酒店历史房态表图片，并返回严格 JSON。',
    }, file, {
      timeoutMs: 420 * 1000,
    })
  } catch (error: any) {
    const message = String(error?.message || '')
    if (/empty|没有返回可用结果|Dify response content is empty/i.test(message)) {
      throw new Error('图片已上传到 AI，但识别工作流没有返回结果。请检查 Dify 的 End 节点是否输出 output 或 records/warnings。')
    }
    throw error
  }
  const data = parseOccupancyResultPayload(content, file.name)
  if (!data) {
    throw new Error('图片识别完成，但没有读取到可导入的房态数据。请确认 AI 输出包含日期、房型、总房数、已售/占用、剩余可售等字段。')
  }
  return data
}

export function mergeOccupancyImport(
  previous: OccupancyImportData | null,
  incoming: OccupancyImportData,
  options: { conflictStrategy?: 'keep-existing' | 'overwrite' } = {}
): { data: OccupancyImportData; report: OccupancyMergeReport } {
  const conflictStrategy = options.conflictStrategy || 'keep-existing'
  if (!previous) {
    return {
      data: {
        ...incoming,
        sourceFileNames: Array.from(new Set([incoming.sourceFileName])),
      },
      report: {
        added: incoming.records.length,
        duplicates: 0,
        conflicts: 0,
        skippedConflicts: 0,
        conflictSamples: [],
        total: incoming.records.length,
      },
    }
  }

  let added = 0
  let duplicates = 0
  let conflicts = 0
  let skippedConflicts = 0
  const conflictSamples: OccupancyMergeReport['conflictSamples'] = []
  const recordMap = new Map(previous.records.map(record => [recordKey(record), record]))

  incoming.records.forEach(record => {
    const key = recordKey(record)
    const existing = recordMap.get(key)
    if (!existing) {
      recordMap.set(key, record)
      added += 1
      return
    }
    if (sameRecord(existing, record)) {
      duplicates += 1
      return
    }
    conflicts += 1
    skippedConflicts += conflictStrategy === 'keep-existing' ? 1 : 0
    if (conflictSamples.length < 5) {
      conflictSamples.push({
        date: existing.date,
        roomTypeName: existing.roomTypeName,
        existing,
        incoming: record,
      })
    }
    if (conflictStrategy === 'overwrite') {
      recordMap.set(key, record)
    }
  })

  const sourceFileNames = Array.from(new Set([
    ...(previous.sourceFileNames || [previous.sourceFileName]).filter(Boolean),
    incoming.sourceFileName,
  ]))
  const data = buildImportData(incoming.sourceFileName, Array.from(recordMap.values()))
  data.sourceFileNames = sourceFileNames
  data.importedAt = incoming.importedAt

  return {
    data,
    report: {
      added,
      duplicates,
      conflicts,
      skippedConflicts,
      conflictSamples,
      total: incoming.records.length,
    },
  }
}

function fillMergedCells(rows: CellRows, merges: XLSX.Range[]): CellRows {
  const next = rows.map(row => [...row])
  merges.forEach(range => {
    const value = next[range.s.r]?.[range.s.c]
    if (value === undefined || value === '') return
    for (let r = range.s.r; r <= range.e.r; r += 1) {
      next[r] ||= []
      for (let c = range.s.c; c <= range.e.c; c += 1) {
        if (next[r][c] === '') next[r][c] = value
      }
    }
  })
  return next
}

function parseOccupancyRows(rows: CellRows): OccupancyRecord[] {
  let best: OccupancyRecord[] = []
  for (let headerRow = 0; headerRow < Math.min(rows.length - 1, 8); headerRow += 1) {
    const dateRow = rows[headerRow] || []
    const metricRow = rows[headerRow + 1] || []
    const firstColumn = normalizeText(dateRow[0] || metricRow[0])
    if (!/房型|房间|客房/.test(firstColumn)) continue

    const columns = dateRow.map((cell, index) => ({
      date: parseDateLabel(cell),
      metric: normalizeText(metricRow[index]),
      index,
    })).filter(col => col.date && col.index > 0)

    if (!columns.length) continue

    const records: OccupancyRecord[] = []
    for (let rowIndex = headerRow + 2; rowIndex < rows.length; rowIndex += 1) {
      const row = rows[rowIndex] || []
        const roomInfo = parseRoomInfo(row[0])
        if (!roomInfo || /合计|总计|小计/.test(roomInfo.name)) continue

      const grouped = new Map<string, Partial<OccupancyRecord>>()
      columns.forEach(col => {
        const group = grouped.get(col.date) || {
          date: col.date,
          roomTypeName: roomInfo.name,
          totalRooms: roomInfo.totalRooms,
          occupiedRooms: 0,
          remainingRooms: 0,
          occupancyRate: 0,
        }
        const value = toNumber(row[col.index])
        if (matches(col.metric, occupiedKeys)) group.occupiedRooms = value
        if (matches(col.metric, remainingKeys)) group.remainingRooms = value
        if (matches(col.metric, rateKeys)) group.occupancyRate = parseRate(row[col.index])
        grouped.set(col.date, group)
      })

      grouped.forEach(group => {
        const totalRooms = Number(group.totalRooms || 0)
        const occupiedRooms = Number(group.occupiedRooms || 0)
        const remainingRooms = Number(group.remainingRooms || 0)
        if (!totalRooms && !occupiedRooms && !remainingRooms) return
        const inferredTotal = totalRooms || occupiedRooms + remainingRooms
        records.push({
          date: String(group.date),
          roomTypeName: String(group.roomTypeName),
          totalRooms: inferredTotal,
          occupiedRooms,
          remainingRooms,
          occupancyRate: group.occupancyRate || safeRate(occupiedRooms, inferredTotal),
        })
      })
    }

    if (records.length > best.length) best = records
  }
  return best
}

export function buildImportData(sourceFileName: string, records: OccupancyRecord[]): OccupancyImportData {
  const normalizedRecords = normalizeOccupancyRecords(records)
  const dates = Array.from(new Set(normalizedRecords.map(record => record.date))).sort()
  const totalRoomNights = normalizedRecords.reduce((sum, record) => sum + record.totalRooms, 0)
  const occupiedRoomNights = normalizedRecords.reduce((sum, record) => sum + record.occupiedRooms, 0)
  const remainingRoomNights = normalizedRecords.reduce((sum, record) => sum + record.remainingRooms, 0)
  const roomTypeSummaries = Array.from(groupBy(normalizedRecords, record => record.roomTypeName).entries()).map(([roomTypeName, rows]) => {
    const totalRooms = rows[0]?.totalRooms || 0
    const sortedRows = [...rows].sort((a, b) => a.date.localeCompare(b.date))
    const latest = sortedRows[sortedRows.length - 1]
    const roomNights = rows.reduce((sum, row) => sum + row.totalRooms, 0)
    const occupied = rows.reduce((sum, row) => sum + row.occupiedRooms, 0)
    const remaining = rows.reduce((sum, row) => sum + row.remainingRooms, 0)
    return {
      roomTypeName,
      totalRooms,
      days: rows.length,
      occupiedRoomNights: occupied,
      remainingRoomNights: remaining,
      averageOccupancyRate: safeRate(occupied, roomNights),
      latestOccupiedRooms: latest?.occupiedRooms || 0,
      latestRemainingRooms: latest?.remainingRooms || 0,
    }
  }).sort((a, b) => b.averageOccupancyRate - a.averageOccupancyRate)

  return {
    sourceFileName,
    importedAt: new Date().toISOString(),
    dateRange: dates.length ? dates[0] + ' 至 ' + dates[dates.length - 1] : '',
    records: normalizedRecords,
    roomTypeSummaries,
    averageOccupancyRate: safeRate(occupiedRoomNights, totalRoomNights),
    totalRoomNights,
    occupiedRoomNights,
    remainingRoomNights,
  }
}

export function parseOccupancyResultPayload(payload: any, sourceFileName = 'AI 识别结果'): OccupancyImportData | null {
  const parsed = typeof payload === 'string'
    ? (tryParseJsonPayload(payload) ?? payload)
    : payload
  const records = normalizeImageRecords(parsed)
  return records.length ? buildImportData(sourceFileName, records) : null
}

function parseJsonPayload(content: string): any {
  const text = String(content || '').trim()
  if (!text) throw new Error('图片识别没有返回内容')
  const fenced = text.replace(/^```(?:json)?/i, '').replace(/```$/i, '').trim()
  const objectStart = text.indexOf('{')
  const objectEnd = text.lastIndexOf('}')
  const objectOnly = objectStart >= 0 && objectEnd > objectStart ? text.slice(objectStart, objectEnd + 1) : ''
  const arrayStart = text.indexOf('[')
  const arrayEnd = text.lastIndexOf(']')
  const arrayOnly = arrayStart >= 0 && arrayEnd > arrayStart ? text.slice(arrayStart, arrayEnd + 1) : ''
  const candidates = Array.from(new Set([text, fenced, objectOnly, arrayOnly].filter(Boolean)))

  for (const item of candidates) {
    try {
      return JSON.parse(item)
    } catch {
      // Try the next common Dify output shape.
    }
  }
  throw new Error('图片识别结果不是有效 JSON，请检查 Dify 工作流是否只输出 JSON')
}

function tryParseJsonPayload(content: string): any | null {
  try {
    return parseJsonPayload(content)
  } catch {
    return null
  }
}

function normalizeImageRecords(payload: any): OccupancyRecord[] {
  const rows = collectImageRows(payload)

  return rows.map((row: any) => {
    const roomInfo = parseRoomInfo(row?.roomTypeName ?? row?.roomType ?? row?.房型 ?? row?.房型名称 ?? '')
    const occupiedRooms = Math.max(0, Math.round(toNumber(row?.occupiedRooms ?? row?.occupied ?? row?.soldRooms ?? row?.usedRooms ?? row?.used ?? row?.已住房 ?? row?.占用房 ?? row?.出租房)))
    const remainingRooms = Math.max(0, Math.round(toNumber(row?.remainingRooms ?? row?.remaining ?? row?.availableRooms ?? row?.vacantRooms ?? row?.available ?? row?.可售房 ?? row?.剩余可售 ?? row?.余房)))
    const rawTotal = Math.round(toNumber(row?.totalRooms ?? row?.total ?? row?.roomCount ?? row?.totalRoomCount ?? row?.总房量 ?? row?.总房数 ?? roomInfo?.totalRooms))
    const totalRooms = Math.max(0, rawTotal || occupiedRooms + remainingRooms)
    const rate = parseRate(row?.occupancyRate ?? row?.rate ?? row?.出租率 ?? row?.入住率)
    return {
      date: normalizeDateLabel(row?.date ?? row?.businessDate ?? row?.日期),
      roomTypeName: cleanRoomTypeName(String(roomInfo?.name || row?.roomTypeName || row?.roomType || row?.房型 || row?.房型名称 || '').trim()),
      totalRooms,
      occupiedRooms,
      remainingRooms,
      occupancyRate: rate || safeRate(occupiedRooms, totalRooms),
    }
  }).filter((record: OccupancyRecord) => (
    record.date
    && record.roomTypeName
    && !/合计|总计|小计/.test(record.roomTypeName)
    && (record.totalRooms || record.occupiedRooms || record.remainingRooms)
  ))
}

function collectImageRows(payload: any): any[] {
  if (Array.isArray(payload)) {
    if (payload.some(isOccupancyRowLike)) return payload
    return payload.flatMap(collectImageRows)
  }
  if (typeof payload === 'string') {
    const parsed = tryParseJsonPayload(payload)
    if (parsed && parsed !== payload) return collectImageRows(parsed)
    return parseOccupancyTextRows(payload)
  }
  if (!payload || typeof payload !== 'object') return []

  const directValues = [
    payload.records,
    payload.rows,
    payload.data?.records,
    payload.data?.rows,
    payload.data?.output,
    payload.data?.outputs,
    payload.data?.outputs?.records,
    payload.data?.outputs?.rows,
    payload.data?.outputs?.output,
    payload.data?.outputs?.text,
    payload.data?.outputs?.content,
    payload.result?.records,
    payload.result?.rows,
    payload.result?.output,
    payload.output?.records,
    payload.output?.rows,
    payload.output,
    payload.outputs,
    payload.outputs?.records,
    payload.outputs?.rows,
    payload.outputs?.output,
    payload.outputs?.text,
    payload.outputs?.content,
    payload.answer,
    payload.content,
    payload.text,
  ].filter(value => value !== undefined && value !== null)
  for (const value of directValues) {
    const rows = collectImageRows(value)
    if (rows.length) return rows
  }

  const roomRows = payload.roomTypes || payload.room_type_rows || payload.房型数据 || payload.数据明细
  if (Array.isArray(roomRows)) {
    return roomRows.flatMap((room: any) => {
      const roomName = room?.roomTypeName ?? room?.roomType ?? room?.房型 ?? room?.房型名称 ?? room?.name
      const totalRooms = room?.totalRooms ?? room?.total ?? room?.roomCount ?? room?.总房量 ?? room?.总房数
      const dates = room?.dates ?? room?.daily ?? room?.days ?? room?.records ?? room?.日期数据
      if (!Array.isArray(dates)) return [room]
      return dates.map((day: any) => ({
        ...day,
        roomTypeName: day?.roomTypeName ?? day?.roomType ?? day?.房型 ?? roomName,
        totalRooms: day?.totalRooms ?? day?.total ?? day?.roomCount ?? totalRooms,
      }))
    })
  }

  const dates = payload.dates || payload.days || payload.dateColumns || payload.日期列
  if (Array.isArray(dates)) {
    return dates.flatMap((day: any) => {
      const date = day?.date ?? day?.businessDate ?? day?.日期
      const rooms = day?.rooms ?? day?.records ?? day?.roomTypes ?? day?.房型数据
      if (!Array.isArray(rooms)) return []
      return rooms.map((room: any) => ({ ...room, date: room?.date ?? room?.日期 ?? date }))
    })
  }

  return []
}

function isOccupancyRowLike(value: any) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const hasDate = value.date || value.businessDate || value.日期
  const hasRoom = value.roomTypeName || value.roomType || value.房型 || value.房型名称
  const hasMetric = value.totalRooms !== undefined
    || value.occupiedRooms !== undefined
    || value.remainingRooms !== undefined
    || value.occupancyRate !== undefined
    || value.total !== undefined
    || value.occupied !== undefined
    || value.remaining !== undefined
    || value.总房量 !== undefined
    || value.占用房 !== undefined
    || value.剩余可售 !== undefined
    || value.出租率 !== undefined
  return Boolean(hasDate && hasRoom && hasMetric)
}

function parseOccupancyTextRows(value: string): any[] {
  const text = String(value || '').trim()
  if (!text) return []
  return parseMarkdownTableRows(text) || parseDelimitedTextRows(text) || []
}

function parseMarkdownTableRows(text: string): any[] | null {
  const tableLines = text.split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.includes('|'))
    .map(line => line.replace(/^\||\|$/g, '').split('|').map(cell => cell.trim()))
    .filter(cells => cells.length >= 3 && !cells.every(cell => /^:?-{2,}:?$/.test(cell)))
  if (tableLines.length < 2) return null
  return tableToOccupancyRows(tableLines[0], tableLines.slice(1))
}

function parseDelimitedTextRows(text: string): any[] | null {
  const lines = text.split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
  if (lines.length < 2) return null
  const delimiter = lines[0].includes('\t') ? '\t' : lines[0].includes(',') ? ',' : lines[0].includes('，') ? '，' : ''
  if (!delimiter) return null
  const table = lines.map(line => line.split(delimiter).map(cell => cell.trim())).filter(cells => cells.length >= 3)
  if (table.length < 2) return null
  return tableToOccupancyRows(table[0], table.slice(1))
}

function tableToOccupancyRows(headers: string[], rows: string[][]) {
  const headerMap = headers.map(header => normalizeText(header))
  const indexOf = (patterns: RegExp[]) => headerMap.findIndex(header => patterns.some(pattern => pattern.test(header)))
  const dateIndex = indexOf([/日期|时间|date|businessdate/i])
  const roomIndex = indexOf([/房型|房间|客房|roomtype|room/i])
  const totalIndex = indexOf([/总房|房量|总数|total|count/i])
  const occupiedIndex = indexOf([/占用|已住|入住|售出|出租房|occupied|sold|used/i])
  const remainingIndex = indexOf([/剩余|可售|余房|available|remain|vacant/i])
  const rateIndex = indexOf([/出租率|入住率|占用率|rate/i])
  if (dateIndex < 0 || roomIndex < 0 || (occupiedIndex < 0 && remainingIndex < 0 && rateIndex < 0)) return []
  return rows
    .map(row => ({
      date: row[dateIndex] || '',
      roomTypeName: row[roomIndex] || '',
      totalRooms: totalIndex >= 0 ? row[totalIndex] : '',
      occupiedRooms: occupiedIndex >= 0 ? row[occupiedIndex] : '',
      remainingRooms: remainingIndex >= 0 ? row[remainingIndex] : '',
      occupancyRate: rateIndex >= 0 ? row[rateIndex] : '',
    }))
    .filter(row => row.date || row.roomTypeName)
}

function normalizeDateLabel(value: unknown) {
  const text = String(value || '').trim()
  if (!text) return ''
  const full = text.match(/(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/)
  if (full) return `${full[1]}-${full[2].padStart(2, '0')}-${full[3].padStart(2, '0')}`
  const short = text.match(/(\d{1,2})[-/.月](\d{1,2})/)
  if (short) return `${currentYear()}-${short[1].padStart(2, '0')}-${short[2].padStart(2, '0')}`
  return text
}

function recordKey(record: OccupancyRecord) {
  return `${record.date}::${cleanRoomTypeName(record.roomTypeName)}`
}

function sameRecord(left: OccupancyRecord, right: OccupancyRecord) {
  return left.totalRooms === right.totalRooms
    && left.occupiedRooms === right.occupiedRooms
    && left.remainingRooms === right.remainingRooms
    && Math.round(left.occupancyRate * 10000) === Math.round(right.occupancyRate * 10000)
}

function parseRoomInfo(value: unknown) {
  const text = String(value || '').trim()
  if (!text) return null
  const match = text.match(/^(.*?)[，,]\s*(\d+)\s*[，,]/)
  return {
    name: cleanRoomTypeName(match?.[1] || text),
    totalRooms: match ? Number(match[2]) : 0,
  }
}

function cleanRoomTypeName(value: string) {
  return String(value || '')
    .replace(/(?:[（(]\s*\d+\s*[)）])+\s*$/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function normalizeOccupancyRecords(records: OccupancyRecord[]) {
  const recordMap = new Map<string, OccupancyRecord>()
  records.forEach(record => {
    const normalized: OccupancyRecord = {
      ...record,
      date: normalizeDateLabel(record.date),
      roomTypeName: cleanRoomTypeName(record.roomTypeName),
    }
    if (!normalized.date || !normalized.roomTypeName) return

    const key = recordKey(normalized)
    const existing = recordMap.get(key)
    if (!existing || recordCompleteness(normalized) >= recordCompleteness(existing)) {
      recordMap.set(key, normalized)
    }
  })
  return Array.from(recordMap.values())
}

function recordCompleteness(record: OccupancyRecord) {
  return [
    record.totalRooms,
    record.occupiedRooms,
    record.remainingRooms,
    record.occupancyRate,
  ].filter(value => Number.isFinite(value) && value > 0).length
}

function parseDateLabel(value: CellValue) {
  if (value && typeof value === 'object' && value instanceof Date) return formatDate(value as Date)
  if (typeof value === 'number' && value > 20000) {
    const parsed = XLSX.SSF.parse_date_code(value)
    if (parsed) return formatDate(new Date(parsed.y, parsed.m - 1, parsed.d))
  }
  const text = String(value || '').trim()
  const match = text.match(/(\d{1,2})[-/.月](\d{1,2})/)
  if (!match) return ''
  return `${currentYear()}-${match[1].padStart(2, '0')}-${match[2].padStart(2, '0')}`
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function currentYear() {
  return new Date().getFullYear()
}

function matches(metric: string, keys: string[]) {
  return keys.some(key => metric.includes(key))
}

function normalizeText(value: CellValue) {
  return String(value || '').replace(/\s+/g, '')
}

function toNumber(value: unknown) {
  const text = String(value ?? '').replace(/,/g, '').trim()
  const match = text.match(/-?\d+(\.\d+)?/)
  return match ? Number(match[0]) : 0
}

function parseRate(value: unknown) {
  const text = String(value ?? '')
  const number = toNumber(value)
  if (!number) return 0
  return text.includes('%') || number > 1 ? number / 100 : number
}

function safeRate(occupied: number, total: number) {
  if (!total) return 0
  return Number((occupied / total).toFixed(4))
}

function groupBy<T>(items: T[], keyOf: (item: T) => string) {
  const map = new Map<string, T[]>()
  items.forEach(item => {
    const key = keyOf(item)
    map.set(key, [...(map.get(key) || []), item])
  })
  return map
}
