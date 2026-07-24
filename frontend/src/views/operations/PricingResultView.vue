<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useHotelStore } from '@/stores/hotel'
import { collectStreamContent } from '@/api/content'
import { buildContentAiParams } from '@/utils/aiContextParams'
import { loadAiPageState, saveAiPageState, watchAiPageState } from '@/utils/aiPageState'
import { elementNode, exportMarkdown, exportPdfElement, safeFilename } from '@/utils/exportDocument'
import {
  cleanMarkdown,
  columnIndex,
  firstTable,
  parsePlan,
  sectionByTitle as findPlanSection,
} from '@/utils/planMarkdown'
import AiPlanDocument from '@/components/ai/AiPlanDocument.vue'
import {
  ArrowLeft,
  Check,
  CheckCircle2,
  Coins,
  Copy,
  Download,
  FileText,
  Loader2,
  RefreshCw,
  Send,
  ShieldCheck,
  Sparkles,
  TrendingUp,
} from 'lucide-vue-next'

interface ActionRow {
  key: string
  label: string
  content: string
  roomName: string
  status: string
}

interface PricingCard {
  roomName: string
  currentPrice: string
  suggestedRange: string
  floorPrice: string
  action: string
  timing: string
  risk: string
  fromAi: boolean
}

interface PriceMoveCard extends PricingCard {
  currentNumber: number | null
  rangeNumbers: number[]
  currentLabel: string
  targetLabel: string
  moveLabel: string
  moveType: 'down' | 'up' | 'flat' | 'mixed' | 'pending'
}

interface PricingTableView {
  headers: string[]
  rows: string[][]
  pending: boolean
}

const router = useRouter()
const hotelStore = useHotelStore()
const pageStateKey = 'pricing'

const form = reactive({
  pricingPeriod: 'next7',
  customStartDate: '',
  customEndDate: '',
  pricingGoal: 'balance',
  demandSignal: 'normal',
  bookingWindow: '1-3',
  eventFactor: 'normal',
  competitorPriceRange: '',
  currentPriceNotes: '',
  priceFloor: '',
  maxDiscountPercent: 10,
  targetChannels: ['ota'] as string[],
  promotionAllowed: true,
  packagePreference: 'room-only',
  riskLevel: 'balanced',
  constraints: '',
})

const generating = ref(false)
const completed = ref(false)
const statusText = ref('')
const errorMessage = ref('')
const aiText = ref('')
const runId = ref('')
const copied = ref(false)
const actionStatus = reactive<Record<string, 'pending' | 'done'>>({})
let pageAlive = true
let stopWatchingState: (() => void) | null = null

const periodOptions = [
  { value: 'today', label: '今天及当日尾房' },
  { value: 'next3', label: '未来 3 天' },
  { value: 'next7', label: '未来 7 天' },
  { value: 'weekend', label: '下一个周末' },
  { value: 'holiday', label: '指定节假日' },
  { value: 'custom', label: '自定义日期' },
]
const goalOptions = [
  { value: 'fill', label: '优先提升成交' },
  { value: 'balance', label: '平衡价格与成交' },
  { value: 'revenue', label: '优先提升收益' },
  { value: 'brand', label: '维护价格体系' },
]
const demandOptions = [
  { value: 'unknown', label: '暂不确定' },
  { value: 'weak', label: '偏弱：咨询与预订较少' },
  { value: 'normal', label: '正常：与平日接近' },
  { value: 'strong', label: '偏强：咨询或搜索明显增加' },
  { value: 'hot', label: '火热：节庆或本地事件带动' },
]
const bookingWindowOptions = [
  { value: 'same-day', label: '当天' },
  { value: '1-3', label: '提前 1-3 天' },
  { value: '4-7', label: '提前 4-7 天' },
  { value: '8-14', label: '提前 8-14 天' },
  { value: '15+', label: '提前 15 天以上' },
]
const eventOptions = [
  { value: 'normal', label: '普通工作日' },
  { value: 'weekend', label: '普通周末' },
  { value: 'holiday', label: '法定节假日' },
  { value: 'local-event', label: '演出、展会或本地活动' },
  { value: 'weather-risk', label: '天气可能影响出行' },
]
const channelOptions = [
  { value: 'ota', label: 'OTA 平台' },
  { value: 'direct', label: '电话/前台直订' },
  { value: 'wechat', label: '微信私域' },
  { value: 'member', label: '会员/老客' },
]
const packageOptions = [
  { value: 'room-only', label: '仅调整房价' },
  { value: 'value-add', label: '优先加权益，不直接降价' },
  { value: 'bundle', label: '设计住宿套餐' },
  { value: 'member-only', label: '仅做私域/会员优惠' },
]
const riskOptions = [
  { value: 'conservative', label: '稳健' },
  { value: 'balanced', label: '均衡' },
  { value: 'aggressive', label: '积极' },
]

const roomSnapshot = computed(() => (hotelStore.roomTypes || []).map(room => ({
  roomId: room.id,
  roomName: room.name,
  basePrice: room.basePrice,
  roomCount: room.count,
})))

const dateRange = computed(() => {
  if (form.pricingPeriod === 'custom') {
    return [form.customStartDate, form.customEndDate].filter(Boolean).join(' 至 ')
  }
  return labelOf(periodOptions, form.pricingPeriod)
})
const channelLabels = computed(() => form.targetChannels.map(channel => labelOf(channelOptions, channel)))
const pricingAiText = computed(() => trimToPricingResult(aiText.value))
const sections = computed(() => parsePlan(pricingAiText.value))
const planTitle = computed(() => sections.value.find(section => section.level === 1)?.title || '房型定价执行方案')
const displayStatusText = computed(() => {
  if (!statusText.value) return ''
  if (!generating.value && /^正在|生成仍在后台/.test(statusText.value)) return ''
  return statusText.value
})

function sectionByTitle(patterns: RegExp[]) {
  return findPlanSection(sections.value, patterns)
}

const conclusionSection = computed(() => sectionByTitle([/经营结论|结论|摘要/]))
const sourceSection = computed(() => sectionByTitle([/数据来源|可信度|来源/]))
const signalSection = computed(() => sectionByTitle([/需求|价格信号|信号|图表/]))
const pricingSection = computed(() => sectionByTitle([/房型定价|定价执行|逐房型|建议价/]))
const actionSection = computed(() => sectionByTitle([/执行动作|动作清单|一键执行|采纳/]))
const riskSection = computed(() => sectionByTitle([/核验|风险|复盘/]))

const sourceTable = computed(() => firstTable(sourceSection.value))
const signalTable = computed(() => firstTable(signalSection.value))
const pricingTable = computed(() => firstTable(pricingSection.value))
const riskTable = computed(() => firstTable(riskSection.value))
const loosePricingTable = computed(() => parseLoosePricingTable())
const looseActionTable = computed(() => parseLooseActionTable())
const looseSignalTable = computed(() => parseLooseSignalTable())
const looseSourceTable = computed(() => parseLooseSourceTable())

const pricingTableView = computed<PricingTableView>(() => {
  const headers = ['房型', '当前挂牌价', '建议价区间', '最低保护价', '渠道动作', '执行时点', '风险']
  if (pricingTable.value?.rows?.length) return { headers: pricingTable.value.headers, rows: pricingTable.value.rows, pending: false }
  if (loosePricingTable.value?.rows?.length) return loosePricingTable.value
  const rows = roomSnapshot.value.map(room => [
    room.roomName || '未命名房型',
    room.basePrice ? `¥${room.basePrice}` : '待补充挂牌价',
    '等待 AI 填写',
    form.priceFloor ? `¥${form.priceFloor}` : '等待 AI 填写',
    '等待 AI 填写',
    '等待 AI 填写',
    '等待 AI 填写',
  ])
  return {
    headers,
    rows: rows.length ? rows : [['待补充房型', '待补充挂牌价', '等待 AI 填写', '等待 AI 填写', '等待 AI 填写', '等待 AI 填写', '等待 AI 填写']],
    pending: true,
  }
})

const skeletonPricingCards = computed<PricingCard[]>(() => roomSnapshot.value.map(room => {
  const base = Number(room.basePrice || 0)
  return {
    roomName: room.roomName || '未命名房型',
    currentPrice: base ? `¥${base}` : '待核实',
    suggestedRange: '等待 AI 填写',
    floorPrice: form.priceFloor ? `¥${form.priceFloor}` : '等待 AI 填写',
    action: '等待 AI 填写',
    timing: '等待 AI 填写',
    risk: '等待 AI 填写',
    fromAi: false,
  }
}))

const pricingCards = computed<PricingCard[]>(() => {
  const table = pricingTable.value?.rows?.length ? pricingTable.value : loosePricingTable.value
  if (!table?.rows?.length) return skeletonPricingCards.value
  const roomIndex = columnIndex(table, ['房型', 'roomName'], 0)
  const currentIndex = columnIndex(table, ['当前挂牌价', '挂牌价', '当前价'], 1)
  const rangeIndex = columnIndex(table, ['建议价区间', '建议价', '价格区间'], 2)
  const floorIndex = columnIndex(table, ['最低保护价', '保护价', '底价'], 3)
  const actionIndex = columnIndex(table, ['渠道动作', '动作', '执行'], 4)
  const timingIndex = columnIndex(table, ['执行时点', '时间'], 5)
  const riskIndex = columnIndex(table, ['风险'], 6)
  const parsed = table.rows
    .map(row => normalizePricingRow({
      roomName: cleanMarkdown(row[roomIndex] || ''),
      currentPrice: cleanMarkdown(row[currentIndex] || '待核实'),
      suggestedRange: cleanMarkdown(row[rangeIndex] || '待补充'),
      floorPrice: cleanMarkdown(row[floorIndex] || '待核实'),
      action: cleanMarkdown(row[actionIndex] || '待补充渠道动作'),
      timing: cleanMarkdown(row[timingIndex] || '生成后人工复核'),
      risk: cleanMarkdown(row[riskIndex] || '待核实'),
      raw: row.map(cell => cleanMarkdown(cell || '')).filter(Boolean).join('\n'),
      fromAi: true,
    }))
    .filter(item => {
      if (!item.roomName && !item.suggestedRange) return false
      if (/^(房型|roomName)$/i.test(item.roomName)) return false
      return true
    })

  const byRoom = new Map(parsed.map(item => [normalizeRoomName(item.roomName), item]))
  const ordered = roomSnapshot.value.map(room => {
    const name = room.roomName || '未命名房型'
    const matched = byRoom.get(normalizeRoomName(name)) || findLooseRoomCard(parsed, name)
    if (matched) return { ...matched, roomName: name || matched.roomName }
    const base = Number(room.basePrice || 0)
    return {
      roomName: name,
      currentPrice: base ? `¥${base}` : '待核实',
      suggestedRange: '待核实',
      floorPrice: form.priceFloor ? `¥${form.priceFloor}` : '待核实',
      action: 'AI 未返回该房型，需人工核价',
      timing: '生成后人工复核',
      risk: '待核实',
      fromAi: false,
    }
  })

  const seen = new Set(ordered.map(item => normalizeRoomName(item.roomName)))
  const extras = parsed.filter(item => !seen.has(normalizeRoomName(item.roomName)))
  return [...ordered, ...extras]
})

const priceMoveCards = computed<PriceMoveCard[]>(() => pricingCards.value.map(card => {
  const currentNumber = priceNumbers(card.currentPrice)[0] ?? null
  const rangeNumbers = priceNumbers(card.suggestedRange)
  const move = describePriceMove(currentNumber, rangeNumbers)
  return {
    ...card,
    currentNumber,
    rangeNumbers,
    currentLabel: currentNumber ? formatMoney(currentNumber) : compactText(card.currentPrice),
    targetLabel: formatSuggestedRange(card.suggestedRange, rangeNumbers),
    moveLabel: move.label,
    moveType: move.type,
  }
}))

function normalizePricingRow(row: PricingCard & { raw?: string }) {
  const raw = cleanMarkdown(row.raw || '')
  const allText = [row.action, row.timing, row.risk, raw].filter(Boolean).join('\n')
  const base = Number(roomSnapshot.value.find(room => normalizeRoomName(room.roomName) === normalizeRoomName(row.roomName))?.basePrice || 0)
  const currentPrice = normalizedPrice(row.currentPrice, base)
  const suggestedRange = normalizedRange(row.suggestedRange, base) || extractRange(allText, base) || row.suggestedRange
  const floorPrice = normalizedPrice(row.floorPrice, 0) || extractFloor(allText, suggestedRange, base) || '待核实'
  const action = extractActionText(allText) || cleanNonPriceText(row.action) || '待核实渠道动作'
  const timing = extractTimingText(allText) || cleanTiming(row.timing) || '生成后人工复核'
  const risk = extractRiskText(allText) || cleanRisk(row.risk) || '待核实'
  return {
    ...row,
    currentPrice,
    suggestedRange,
    floorPrice,
    action: localizeParamText(action),
    timing: localizeParamText(timing),
    risk: localizeParamText(risk),
  }
}

const kpiCards = computed(() => {
  const table = pricingTable.value?.rows?.length ? pricingTable.value : signalTable.value
  if (!table) return []
  return table.headers.slice(0, 4).map((header, index) => ({
    label: header,
    value: table.rows[0]?.[index] || '待核实',
    note: table.rows[1]?.[index] || '',
  }))
})

const actionRows = computed<ActionRow[]>(() => {
  const table = firstTable(actionSection.value) || looseActionTable.value
  if (!table?.rows?.length) return []
  const keyIndex = columnIndex(table, ['actionKey', '动作', '编号'], 0)
  const roomIndex = columnIndex(table, ['roomName', '房型'], 1)
  const labelIndex = columnIndex(table, ['按钮文案', '按钮', '动作', 'channel'], 2)
  const contentIndex = columnIndex(table, ['执行内容', 'targetPrice', '价格', '内容'], 3)
  const statusIndex = columnIndex(table, ['status', '状态'], 4)
  const byRoom = new Map<string, ActionRow>()
  table.rows.forEach((row, index) => {
    if (row.every(cell => /^:?-{2,}:?$/.test(String(cell || '').trim()))) return
    const key = row[keyIndex] || ''
    if (/^:?-{2,}:?$/.test(key)) return
    const roomName = row[roomIndex] || ''
    const label = roomName ? `采纳 ${roomName}` : (row[labelIndex] || `采纳动作 ${index + 1}`)
    const action = {
      key: key || `${label}-${index}`,
      label,
      content: row[contentIndex] || row[labelIndex] || '',
      roomName,
      status: row[statusIndex] || 'pending',
    }
    const groupKey = roomName || action.key
    if (!byRoom.has(groupKey)) byRoom.set(groupKey, action)
  })
  return Array.from(byRoom.values()).slice(0, 3)
})

const actionTableView = computed<PricingTableView>(() => {
  const table = firstTable(actionSection.value)
  const headers = ['actionKey', 'roomName', '按钮文案', 'targetPrice', 'channel', 'status']
  if (table?.rows?.length) return { headers: table.headers, rows: table.rows, pending: false }
  if (looseActionTable.value?.rows?.length) return looseActionTable.value
  const rows = roomSnapshot.value.map(room => [
    '',
    room.roomName || '未命名房型',
    '等待 AI 填写',
    '等待 AI 填写',
    channelLabels.value.join('、') || '待设定',
    generating.value ? 'generating' : 'pending',
  ])
  return {
    headers,
    rows: rows.length ? rows : [['', '待补充房型', '等待 AI 填写', '等待 AI 填写', channelLabels.value.join('、') || '待设定', 'pending']],
    pending: true,
  }
})

const signalTableView = computed<PricingTableView>(() => {
  const headers = ['信号', '当前判断', '依据', '对价格影响', '待核实']
  if (signalTable.value?.rows?.length) return { headers: signalTable.value.headers, rows: signalTable.value.rows, pending: false }
  if (looseSignalTable.value?.rows?.length) return looseSignalTable.value
  return {
    headers,
    rows: [
      ['本店需求', labelOf(demandOptions, form.demandSignal), '用户输入', '等待 AI 填写', '等待 AI 填写'],
      ['预订窗口', labelOf(bookingWindowOptions, form.bookingWindow), '用户输入', '等待 AI 填写', '等待 AI 填写'],
      ['日期因素', labelOf(eventOptions, form.eventFactor), '用户输入', '等待 AI 填写', '等待 AI 填写'],
      ['竞品价格', form.competitorPriceRange || '未填写', '用户输入/周边情报', '等待 AI 填写', '等待 AI 填写'],
    ],
    pending: true,
  }
})

const sourceTableView = computed<PricingTableView>(() => {
  const headers = ['来源', '数据', '用途', '可信度', '待核实']
  if (sourceTable.value?.rows?.length) return { headers: sourceTable.value.headers, rows: sourceTable.value.rows, pending: false }
  if (looseSourceTable.value?.rows?.length) return looseSourceTable.value
  return {
    headers,
    rows: [
      ['酒店资料', `${roomSnapshot.value.length} 个房型及挂牌价`, '定价基础', '高', '实时房态/可售库存'],
      ['用户输入', `${labelOf(goalOptions, form.pricingGoal)} / ${channelLabels.value.join('、') || '待设定'}`, '定价约束', '高', '无'],
      ['周边与竞品', form.competitorPriceRange || '未提供', '价格参照', form.competitorPriceRange ? '中' : '无', '竞品实时 OTA 价格'],
    ],
    pending: true,
  }
})

const riskTableView = computed<PricingTableView>(() => {
  const headers = ['事项', '核验方法', '负责人', '时间点', '复盘指标']
  if (riskTable.value?.rows?.length) {
    const rows = riskTable.value.rows.filter(row => isUsefulBusinessRow(riskTable.value?.headers || headers, row))
    if (rows.length) return { headers: riskTable.value.headers, rows, pending: false }
  }
  return {
    headers,
    rows: [
      ['价格竞争力', '等待 AI 填写', '等待 AI 填写', '等待 AI 填写', '等待 AI 填写'],
      ['成交与收益', '等待 AI 填写', '等待 AI 填写', '等待 AI 填写', '等待 AI 填写'],
    ],
    pending: true,
  }
})

const demandSignalCards = computed(() => {
  const table = signalTableView.value
  const signalIndex = columnIndex(table, ['信号'], 0)
  const judgmentIndex = columnIndex(table, ['当前判断'], 1)
  const basisIndex = columnIndex(table, ['依据'], 2)
  const impactIndex = columnIndex(table, ['对价格影响'], 3)
  const verifyIndex = columnIndex(table, ['待核实'], 4)
  return table.rows
    .filter(row => isUsefulBusinessRow(table.headers, row))
    .map((row, index) => ({
      key: `${row[signalIndex] || index}`,
      signal: localizeParamText(cleanMarkdown(row[signalIndex] || signalLabelFromRow(row))),
      judgment: localizeParamText(cleanMarkdown(row[judgmentIndex] || '待核实')),
      basis: compactEvidence(localizeParamText(cleanMarkdown(row[basisIndex] || '待核实'))),
      impact: localizeParamText(cleanMarkdown(row[impactIndex] || '待核实')),
      verify: localizeParamText(cleanMarkdown(row[verifyIndex] || '')),
      tone: signalTone(row),
    }))
    .filter(item => item.signal || item.basis || item.impact)
    .slice(0, 6)
})

const sourceEvidenceCards = computed(() => {
  const table = sourceTableView.value
  const sourceIndex = columnIndex(table, ['来源'], 0)
  const dataIndex = columnIndex(table, ['数据'], 1)
  const usageIndex = columnIndex(table, ['用途'], 2)
  const trustIndex = columnIndex(table, ['可信度'], 3)
  return table.rows
    .filter(row => isUsefulBusinessRow(table.headers, row))
    .map((row, index) => ({
      key: `${row[sourceIndex] || index}`,
      source: localizeParamText(cleanMarkdown(row[sourceIndex] || '来源待核实')),
      data: compactEvidence(localizeParamText(cleanMarkdown(row[dataIndex] || '待核实'))),
      usage: localizeParamText(cleanMarkdown(row[usageIndex] || '')),
      trust: localizeParamText(cleanMarkdown(row[trustIndex] || '待核实')),
    }))
    .filter(item => !/^待核实$/.test(item.data))
    .slice(0, 4)
})

const canUseCompletedActions = computed(() => completed.value && !generating.value)

const summaryItems = computed(() => [
  { label: '定价周期', value: dateRange.value || '-' },
  { label: '定价目标', value: labelOf(goalOptions, form.pricingGoal) },
  { label: '需求信号', value: labelOf(demandOptions, form.demandSignal) },
  { label: '预订窗口', value: labelOf(bookingWindowOptions, form.bookingWindow) },
  { label: '日期影响', value: labelOf(eventOptions, form.eventFactor) },
  { label: '价格策略', value: labelOf(packageOptions, form.packagePreference) },
])

function trimToPricingResult(content: string) {
  const text = String(content || '')
  const strategyIndex = text.search(/\n\s*(?:#{1,3}\s*)?(?:营销策略执行方案|核心目标与\s*KPI|策略标签|执行时间表|各渠道内容计划)\b/)
  return strategyIndex > 0 ? text.slice(0, strategyIndex).trim() : text
}

function sectionText(patterns: RegExp[]) {
  const section = sectionByTitle(patterns)
  if (section?.rawLines?.length) return section.rawLines.join('\n')
  const text = pricingAiText.value
  const start = patterns.map(pattern => text.search(pattern)).filter(index => index >= 0).sort((a, b) => a - b)[0]
  if (start == null) return ''
  const rest = text.slice(start)
  const next = rest.slice(1).search(/\n\s*(?:#{1,3}\s*)?(?:经营结论摘要|逐房型定价执行表|可执行动作清单|需求与价格信号图表|数据来源与可信度|风险核验与复盘指标)\b/)
  return next >= 0 ? rest.slice(0, next + 1) : rest
}

function parseLoosePricingTable(): PricingTableView | null {
  const headers = ['房型', '当前挂牌价', '建议价区间', '最低保护价', '渠道动作', '执行时点', '风险']
  const text = sectionText([/房型定价|定价执行|逐房型|建议价/])
  if (!text.trim()) return null
  const rows = roomSnapshot.value.map((room, index) => {
    const segment = roomSegment(text, room.roomName, roomSnapshot.value[index + 1]?.roomName)
    if (!segment) return null
    const base = Number(room.basePrice || 0)
    const range = extractRange(segment, base)
    const floor = extractFloor(segment, range, base)
    return [
      room.roomName || '未命名房型',
      base ? `¥${base}` : extractFirstNumber(segment) || '待核实',
      range || '待核实',
      floor || '待核实',
      extractLine(segment, /OTA|渠道|挂牌|折扣|调价|维持|执行/) || '待核实',
      extractLine(segment, /立即|未来|第\d+天|每日|本周期|执行/) || '待核实',
      extractRisk(segment) || '待核实',
    ]
  }).filter(Boolean) as string[][]
  return rows.length ? { headers, rows, pending: false } : null
}

function parseLooseActionTable(): PricingTableView | null {
  const headers = ['actionKey', 'roomName', '按钮文案', 'targetPrice', 'channel', 'status']
  const table = loosePricingTable.value
  if (!table?.rows?.length) return null
  return {
    headers,
    rows: table.rows.map(row => [
      `pricing-${slugText(row[0])}`,
      row[0],
      `采纳 ${row[0]} 建议价`,
      extractTargetPrice(row[2]),
      channelLabels.value.join('、') || 'OTA 平台',
      'pending',
    ]),
    pending: false,
  }
}

function parseLooseSignalTable(): PricingTableView | null {
  const headers = ['信号', '当前判断', '依据', '对价格影响', '待核实']
  const text = sectionText([/需求|价格信号|信号|图表/])
  if (!text.trim()) return null
  const names = ['市场需求强度', '预订提前期', '周边竞品价格', '特殊事件/天气', '酒店定位与客源']
  const rows = names.map((name, index) => {
    const segment = roomSegment(text, name, names[index + 1])
    if (!segment) return null
    const lines = usefulLines(segment).filter(line => line !== name)
    return [name, lines[0] || '待核实', lines[1] || '待核实', lines[2] || '待核实', lines[3] || '待核实']
  }).filter(Boolean) as string[][]
  return rows.length ? { headers, rows, pending: false } : null
}

function parseLooseSourceTable(): PricingTableView | null {
  const headers = ['来源', '数据', '用途', '可信度', '待核实']
  const text = sectionText([/数据来源|可信度|来源/])
  if (!text.trim()) return null
  const names = ['用户输入', '酒店核心信息', '天气数据', '需求和事件判断', '周边信息', '用户权限']
  const rows = names.map((name, index) => {
    const segment = roomSegment(text, name, names[index + 1])
    if (!segment) return null
    const lines = usefulLines(segment).filter(line => line !== name)
    return [name, lines[0] || '待核实', lines[1] || '待核实', lines[2] || '待核实', lines[3] || '待核实']
  }).filter(Boolean) as string[][]
  return rows.length ? { headers, rows, pending: false } : null
}

function roomSegment(text: string, startLabel = '', nextLabel = '') {
  if (!startLabel) return ''
  const start = text.indexOf(startLabel)
  if (start < 0) return ''
  const rest = text.slice(start)
  const next = nextLabel ? rest.indexOf(nextLabel, startLabel.length) : -1
  return (next > 0 ? rest.slice(0, next) : rest).trim()
}

function usefulLines(text: string) {
  return text
    .split(/\r?\n/)
    .map(line => cleanMarkdown(line).replace(/^\|+/, '').trim())
    .filter(line => line && !/^[-|:\s]+$/.test(line) && !/^(房型|当前挂牌价|建议价区间|最低保护价|渠道动作|执行时点|风险)$/.test(line))
}

function extractRange(text: string, base = 0) {
  const matches = text.match(/\d{2,5}\s*(?:-|至|~|—)\s*\d{2,5}/g) || []
  const raw = matches[0]
  if (!raw) return ''
  const nums = raw.match(/\d{2,5}/g)?.map(Number).filter(Boolean) || []
  if (nums.length < 2 || !base) return raw.replace(/\s+/g, '')
  const discount = Math.max(0, Math.min(50, Number(form.maxDiscountPercent || 10))) / 100
  const minAllowed = Math.round(base * (1 - discount))
  const valid = nums.filter(num => num >= minAllowed && num <= Math.round(base * 1.5))
  if (valid.length >= 2) return `${Math.min(...valid)}-${Math.max(...valid)}`
  if (valid.length === 1) return `¥${valid[0]} - 待核实`
  return '待核实'
}

function extractFloor(text: string, range: string, base: number) {
  if (range) {
    const nums = range.match(/\d{2,5}/g)?.map(Number).filter(Boolean) || []
    if (nums.length) {
      const discount = Math.max(0, Math.min(50, Number(form.maxDiscountPercent || 10))) / 100
      const minAllowed = base ? Math.round(base * (1 - discount)) : 0
      const valid = nums.filter(num => !minAllowed || num >= minAllowed)
      if (valid.length) return `¥${Math.min(...valid)}`
    }
  }
  const numbers = text.match(/\d{2,5}/g)?.map(Number).filter(Boolean) || []
  const candidate = numbers.find(num => num > 0 && (!base || num < base))
  return candidate ? `¥${candidate}` : ''
}

function extractFirstNumber(text: string) {
  const value = text.match(/\d{2,5}/)?.[0]
  return value ? `¥${value}` : ''
}

function extractLine(text: string, pattern: RegExp) {
  return usefulLines(text).find(line => pattern.test(line)) || ''
}

function extractRisk(text: string) {
  return usefulLines(text).find(line => /风险|稀释|滞销|积压|偏高|偏低|不明|慎用/.test(line)) || ''
}

function extractTargetPrice(range: string) {
  const nums = String(range || '').match(/\d{2,5}/g)
  return nums?.[0] || ''
}

function normalizedPrice(value: string, fallback = 0) {
  const text = cleanMarkdown(value || '')
  const numbers = priceNumbers(text)
  if (numbers.length) return `¥${numbers[0]}`
  return fallback ? `¥${fallback}` : ''
}

function normalizedRange(value: string, base = 0) {
  const numbers = priceNumbers(value)
  if (numbers.length >= 2) {
    const low = Math.min(...numbers)
    const high = Math.max(...numbers)
    if (base && (low < base * 0.45 || high > base * 1.8)) return ''
    return `${low}-${high}`
  }
  if (numbers.length === 1) return `¥${numbers[0]}`
  return ''
}

function textLines(value: string) {
  return cleanMarkdown(value || '')
    .split(/\n|<br\s*\/?>|；|;/i)
    .map(line => cleanMarkdown(line).replace(/^[-*]\s*/, ''))
    .filter(Boolean)
}

function isOnlyPriceText(value: string) {
  const text = cleanMarkdown(value || '').replace(/[¥￥元\s~至\-—]/g, '')
  return Boolean(text) && /^\d+$/.test(text)
}

function cleanNonPriceText(value: string) {
  const text = cleanMarkdown(value || '')
  if (!text || isOnlyPriceText(text)) return ''
  if (/^(待核实|生成后人工复核|pending)$/i.test(text)) return ''
  return text
}

function extractActionText(value: string) {
  return textLines(value)
    .find(line => (
      !isOnlyPriceText(line) &&
      /OTA|平台|周中|周末|挂牌|调价|执行|回调|折扣|维持|收紧|限时|价格/.test(line) &&
      !/风险|不足|损伤|缺失|待核实|若|可能|偏低|偏高/.test(line)
    )) || ''
}

function extractTimingText(value: string) {
  return textLines(value)
    .find(line => /7月|第\d+天|周一|周二|周三|周四|周五|周六|周日|周中|周末|今日|明日|立即|起生效|本周期|未来/.test(line)) || ''
}

function cleanTiming(value: string) {
  const text = cleanMarkdown(value || '')
  if (!text || isOnlyPriceText(text) || /pending/i.test(text)) return ''
  return text
}

function extractRiskText(value: string) {
  return textLines(value)
    .find(line => (
      !isOnlyPriceText(line) &&
      /风险|待核实|缺失|不足|损伤|滞销|库存|房态|竞品|转化|若|可能|偏低|偏高|利润|品牌/.test(line) &&
      !/^OTA平台[:：]/.test(line)
    )) || ''
}

function cleanRisk(value: string) {
  const text = cleanMarkdown(value || '')
  if (!text || isOnlyPriceText(text) || /^OTA平台[:：]/.test(text)) return ''
  return text
}

function compactEvidence(value: string, max = 88) {
  const text = cleanMarkdown(value || '').replace(/\s+/g, ' ')
  if (!text) return '待核实'
  return text.length > max ? `${text.slice(0, max)}...` : text
}

function localizeParamText(value: string) {
  const labels: Record<string, string> = {
    eventFactor: '日期影响',
    demandSignal: '需求信号',
    bookingWindow: '预订窗口',
    pricingPeriod: '定价周期',
    pricingGoal: '定价目标',
    targetChannels: '渠道',
    packagePreference: '价格策略',
    riskLevel: '风险偏好',
    normal: '正常',
    unknown: '暂不确定',
    weak: '偏弱',
    strong: '偏强',
    hot: '火热',
    weekend: '周末',
    holiday: '节假日',
    'local-event': '本地活动',
    'weather-risk': '天气影响',
    balance: '平衡价格与成交',
    fill: '优先提升成交',
    revenue: '优先提升收益',
    brand: '维护价格体系',
    ota: 'OTA 平台',
    direct: '电话/前台直订',
    wechat: '微信私域',
    member: '会员/老客',
    'room-only': '仅调整房价',
    'value-add': '优先加权益',
    bundle: '住宿套餐',
    conservative: '稳健',
    balanced: '均衡',
    aggressive: '积极',
  }
  let text = cleanMarkdown(value || '')
  Object.entries(labels).forEach(([key, label]) => {
    text = text.replace(new RegExp(`\\b${escapeRegExp(key)}\\b`, 'g'), label)
  })
  return text.replace(/=/g, '：')
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function signalLabelFromRow(row: string[]) {
  const text = row.map(cell => cleanMarkdown(cell || '')).join(' ')
  if (/天气|高温|降雨|暴雨|晴|雨/.test(text)) return '未来天气'
  if (/活动|演出|展会|赛事|音乐会|集市/.test(text)) return '热门事件'
  if (/景点|商圈|大学|医院|会展|周边|客流/.test(text)) return '周边客流'
  if (/竞品|酒店|民宿|价格/.test(text)) return '周边价格'
  return '需求信号'
}

function signalTone(row: string[]) {
  const text = row.map(cell => cleanMarkdown(cell || '')).join(' ')
  if (/上浮|涨|偏强|利好|支撑|高峰|热门|增加/.test(text)) return 'up'
  if (/下调|降|偏弱|利空|抑制|减少/.test(text)) return 'down'
  if (/待核实|缺失|无法/.test(text)) return 'pending'
  return 'flat'
}

function isUsefulBusinessRow(headers: string[], row: string[]) {
  const cells = row.map(cell => cleanMarkdown(cell || ''))
  const filled = cells.filter(Boolean)
  if (!filled.length) return false
  if (filled.every(cell => /^待核实$|^pending$|^[-:：]+$/i.test(cell))) return false
  if (cells.every((cell, index) => !cell || cell.replace(/\s+/g, '') === cleanMarkdown(headers[index] || '').replace(/\s+/g, ''))) return false
  if (filled.length <= 2 && filled.some(cell => /^(指标|目标值|依据|事项|核验方法|负责人|时间点|复盘指标)$/.test(cell))) return false
  return true
}

function priceNumbers(value: string) {
  const text = String(value || '')
  const priceLike = text.match(/(?:¥|￥)\s*\d{2,5}|\b\d{2,5}\b(?=\s*(?:-|至|~|—|元|$))/g)
  const source = priceLike?.length ? priceLike : (text.match(/\d{2,5}/g) || [])
  return source
    .map(item => String(item).match(/\d{2,5}/)?.[0] || '')
    .map(num => Number(num))
    .filter(num => Number.isFinite(num) && num > 0)
}

function formatMoney(value: number) {
  return `¥${Math.round(value)}`
}

function formatSuggestedRange(raw: string, numbers: number[]) {
  if (numbers.length >= 2) return `${formatMoney(Math.min(...numbers))} - ${formatMoney(Math.max(...numbers))}`
  if (numbers.length === 1) return formatMoney(numbers[0])
  return cleanMarkdown(raw || '待核实')
}

function compactText(value: string) {
  const text = cleanMarkdown(value || '待核实').replace(/\s+/g, ' ')
  return text.length > 18 ? `${text.slice(0, 18)}...` : text
}

function normalizeRoomName(value: string) {
  return cleanMarkdown(value || '').replace(/\s+/g, '').toLowerCase()
}

function findLooseRoomCard(cards: PricingCard[], roomName: string) {
  const target = normalizeRoomName(roomName)
  if (!target) return undefined
  return cards.find(card => {
    const name = normalizeRoomName(card.roomName)
    return Boolean(name && (name.includes(target) || target.includes(name)))
  })
}

function formatDelta(value: number) {
  const abs = Math.abs(Math.round(value))
  return abs ? `¥${abs}` : '¥0'
}

function describePriceMove(current: number | null, range: number[]) {
  if (!current || !range.length) return { label: '待核价', type: 'pending' as const }
  const low = Math.min(...range)
  const high = Math.max(...range)
  if (high < current) {
    return { label: `下调 ${formatDelta(current - high)}-${formatDelta(current - low)}`, type: 'down' as const }
  }
  if (low > current) {
    return { label: `上调 ${formatDelta(low - current)}-${formatDelta(high - current)}`, type: 'up' as const }
  }
  if (low === current && high === current) {
    return { label: '维持当前价', type: 'flat' as const }
  }
  if (low < current && high === current) {
    return { label: `最低可降 ${formatDelta(current - low)}`, type: 'down' as const }
  }
  if (low === current && high > current) {
    return { label: `最高可升 ${formatDelta(high - current)}`, type: 'up' as const }
  }
  return { label: `覆盖当前价，浮动 ${formatDelta(current - low)}-${formatDelta(high - current)}`, type: 'mixed' as const }
}

function slugText(value: string) {
  return String(value || 'room')
    .toLowerCase()
    .replace(/[^\da-z\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function labelOf(options: { value: string; label: string }[], value: string) {
  return options.find(item => item.value === value)?.label || value
}

function persistState() {
  saveAiPageState(pageStateKey, {
    form: JSON.parse(JSON.stringify(form)),
    runId: runId.value,
    generating: generating.value,
    completed: completed.value,
    statusText: statusText.value,
    aiText: aiText.value,
    errorMessage: errorMessage.value,
    lastActivityAt: Date.now(),
  })
}

function applyStoredState(restored: any) {
  if (!restored?.form) return false
  Object.assign(form, restored.form)
  runId.value = restored.runId || runId.value || `${Date.now()}`
  generating.value = Boolean(restored.generating)
  completed.value = Boolean(restored.completed)
  statusText.value = restored.statusText || ''
  aiText.value = restored.aiText || ''
  errorMessage.value = restored.errorMessage || ''
  return true
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (!applyStoredState(restored)) {
    router.replace('/pricing')
    return false
  }
  return true
}

function isRecoverableStreamError(error: any) {
  const message = String(error?.message || error || '')
  return /连接.*中断|interrupted|aborted|network|fetch/i.test(message)
}

function shouldRestart(restored: any) {
  if (!restored?.generating) return false
  return !String(restored.aiText || '').trim()
}

function buildPricingParams() {
  return {
    theme: '酒店房型定价建议',
    dateRange: dateRange.value,
    pricingPeriod: form.pricingPeriod,
    pricingGoal: form.pricingGoal,
    pricingGoalLabel: labelOf(goalOptions, form.pricingGoal),
    demandSignal: form.demandSignal,
    demandSignalLabel: labelOf(demandOptions, form.demandSignal),
    bookingWindow: form.bookingWindow,
    bookingWindowLabel: labelOf(bookingWindowOptions, form.bookingWindow),
    eventFactor: form.eventFactor,
    eventFactorLabel: labelOf(eventOptions, form.eventFactor),
    competitorPriceRange: form.competitorPriceRange,
    currentPriceNotes: form.currentPriceNotes,
    priceFloor: form.priceFloor,
    maxDiscountPercent: form.maxDiscountPercent,
    targetChannels: form.targetChannels,
    channelLabels: channelLabels.value,
    promotionAllowed: form.promotionAllowed,
    packagePreference: form.packagePreference,
    packagePreferenceLabel: labelOf(packageOptions, form.packagePreference),
    riskLevel: form.riskLevel,
    riskLevelLabel: labelOf(riskOptions, form.riskLevel),
    constraints: form.constraints,
    roomSnapshot: roomSnapshot.value,
    evidenceRequirement: true,
    outputFormat: 'markdown',
    message: `请为${dateRange.value}生成房型定价执行方案。目标：${labelOf(goalOptions, form.pricingGoal)}；渠道：${channelLabels.value.join('、') || '待设定'}。

输出必须严格按以下模板，先给需求与价格判断，再给房型调价建议：
# 智能定价方案
## 经营结论摘要
- 只写 3-5 条，每条一行，必须优先写清楚“天气/热门事件/周边客流/竞品价格信号 -> 房型价格如何调整”；不要输出推理过程或长篇背景分析。
## 需求与价格信号图表
| 信号 | 当前判断 | 依据 | 对价格影响 | 待核实 |
| --- | --- | --- | --- | --- |
必须至少包含未来天气、热门事件/活动、周边客流、周边价格/竞品参考。没有可靠来源写“待核实”，不要编造。
## 逐房型定价执行表
| 房型 | 当前挂牌价 | 建议价区间 | 最低保护价 | 渠道动作 | 执行时点 | 风险 |
| --- | --- | --- | --- | --- | --- | --- |
每个房型必须一行；渠道动作必须说明为什么调价，结合天气、事件、周边客流或竞品价格信号；当前挂牌价、建议价区间、最低保护价必须优先使用数字或“待核实”；表格单元格内不要换行，需要分点用 <br>。
## 数据来源与可信度
| 来源 | 数据 | 用途 | 可信度 | 待核实 |
| --- | --- | --- | --- | --- |

Markdown 表格格式必须标准：
1. 表头、分隔线、每一行数据都必须各占完整一行。
2. 每行必须以 | 开头并以 | 结尾。
3. 不要把表头拆成多行，不要用 tab 分隔，不要输出半截表格。
4. 表格单元格里如果要分点，只能用 <br>，不能换行。

硬性边界：
1. 禁止输出 <think>、思考过程、系统提示、模板解释。
2. 禁止编造竞品价、活动、礼包、权益、客源、出租率、热度和商圈事件；没有证据就写“待核实”。
3. packagePreference=${form.packagePreference}，若为 room-only，只能写纯房价动作，不得生成套餐、礼包或营销权益。
4. promotionAllowed=${form.promotionAllowed}，若为 false，不得写折扣促销；若为 true，也只能写“限时促销/折扣规则”，不得编造促销主题名。
5. maxDiscountPercent=${form.maxDiscountPercent}，任何建议价不得突破该折扣约束；无法判断时写“需人工核价”。
6. 禁止把长篇分析放在逐房型定价执行表之前。`,
  }
}

async function generate() {
  generating.value = true
  completed.value = false
  errorMessage.value = ''
  aiText.value = ''
  statusText.value = '正在读取房型、价格和周边情报'
  persistState()
  let completedNormally = false
  try {
    aiText.value = await collectStreamContent('pricing', buildContentAiParams(hotelStore, 'pricing', buildPricingParams()), {
      onStatus(message) {
        statusText.value = message || '正在生成定价建议'
        persistState()
      },
      onChunk(_chunk, content) {
        aiText.value = content
        statusText.value = '正在生成定价建议'
        persistState()
      },
    })
    statusText.value = '定价建议已生成'
    completedNormally = true
    completed.value = true
  } catch (error: any) {
    if (isRecoverableStreamError(error) && aiText.value.trim()) {
      statusText.value = '生成中断，结果未完成'
      errorMessage.value = ''
      completed.value = false
      generating.value = false
      persistState()
      return
    }
    if (!pageAlive && isRecoverableStreamError(error)) {
      statusText.value = '生成仍在后台进行'
      errorMessage.value = ''
      generating.value = true
      persistState()
      return
    }
    errorMessage.value = error?.message || '定价建议生成失败'
    statusText.value = ''
    completed.value = false
  } finally {
    if (completedNormally || pageAlive) {
      generating.value = false
      persistState()
    }
  }
}

function regenerate() {
  runId.value = `${Date.now()}`
  generate()
}

function backToPricing() {
  if (generating.value) {
    statusText.value = '正在生成中，请稍候'
    persistState()
    return
  }
  router.push('/pricing')
}

async function copyResult() {
  if (!completed.value || !aiText.value) return
  await navigator.clipboard.writeText(aiText.value)
  copied.value = true
  window.setTimeout(() => { copied.value = false }, 1600)
}

async function exportResult(format: 'markdown' | 'pdf') {
  if (!completed.value || !aiText.value) return
  const filename = safeFilename(`${planTitle.value}-${dateRange.value || form.pricingPeriod}`)
  if (format === 'markdown') {
    exportMarkdown(filename, planTitle.value, aiText.value)
    return
  }
  const ok = await exportPdfElement(planTitle.value, elementNode('#pricing-export-content'), filename)
  if (!ok) errorMessage.value = '导出内容为空，请稍后重试'
}

function executeAction(key: string) {
  if (!canUseCompletedActions.value) return
  actionStatus[key] = 'done'
}

function executeAll() {
  if (!canUseCompletedActions.value) return
  actionRows.value.forEach(action => { actionStatus[action.key] = 'done' })
}

onMounted(async () => {
  pageAlive = true
  if (!hotelStore.config.name || !hotelStore.roomTypes.length) {
    await hotelStore.loadFromApi().catch(() => {})
  }
  const restored = loadAiPageState<any>(pageStateKey)
  if (!applyStoredState(restored)) {
    router.replace('/pricing')
    return
  }
  stopWatchingState = watchAiPageState<any>(pageStateKey, state => {
    if (state?.runId && state.runId !== runId.value) return
    applyStoredState(state)
  })
  if (shouldRestart(restored)) generate()
})

onUnmounted(() => {
  pageAlive = false
  stopWatchingState?.()
  stopWatchingState = null
})
</script>

<template>
  <div class="min-h-full min-w-0 overflow-x-hidden bg-cream-50/60">
    <div class="sticky top-0 z-20 border-b border-cream-300 bg-white/95 px-6 py-3 backdrop-blur">
      <div class="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-3">
          <button class="icon-button" title="返回修改条件" @click="backToPricing"><ArrowLeft class="h-4 w-4" /></button>
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <h1 class="truncate text-base font-semibold text-bamboo-900">{{ planTitle }}</h1>
              <span v-if="displayStatusText" class="rounded-full bg-bamboo-50 px-2.5 py-1 text-[11px] text-bamboo-700">{{ displayStatusText }}</span>
            </div>
            <p class="mt-0.5 truncate text-xs text-warm-500">{{ dateRange }} / {{ labelOf(goalOptions, form.pricingGoal) }} / {{ channelLabels.join('、') }}</p>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <button class="secondary-button" :disabled="!completed || !aiText" @click="copyResult"><Check v-if="copied" class="h-4 w-4" /><Copy v-else class="h-4 w-4" />{{ copied ? '已复制' : '复制全文' }}</button>
          <button class="secondary-button" :disabled="!completed || !aiText" @click="exportResult('markdown')"><Download class="h-4 w-4" />导出 Markdown</button>
          <button class="secondary-button" :disabled="!completed || !aiText" @click="exportResult('pdf')"><Download class="h-4 w-4" />导出 PDF</button>
          <button class="primary-button" :disabled="generating" @click="regenerate"><Loader2 v-if="generating" class="h-4 w-4 animate-spin" /><RefreshCw v-else class="h-4 w-4" />重新生成</button>
        </div>
      </div>
    </div>

    <main id="pricing-export-content" class="mx-auto w-full max-w-7xl min-w-0 overflow-x-hidden px-5 py-7">
      <section v-if="aiText || generating" class="plan-section">
        <div class="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-cream-200 pb-4">
          <div>
            <div class="flex items-center gap-2 text-bamboo-800">
              <Coins class="h-4 w-4" />
              <h2 class="text-xl font-semibold">{{ planTitle }}</h2>
            </div>
            <p class="mt-2 text-xs text-warm-600">
              周期：{{ dateRange }} <span class="mx-2 text-warm-300">/</span>
              目标：{{ labelOf(goalOptions, form.pricingGoal) }} <span class="mx-2 text-warm-300">/</span>
              渠道：{{ channelLabels.join('、') || '待设定' }}
            </p>
          </div>
          <div v-if="generating" class="inline-flex items-center gap-2 rounded-full bg-bamboo-50 px-3 py-1.5 text-xs font-semibold text-bamboo-700">
            <Loader2 class="h-3.5 w-3.5 animate-spin" />
            AI 正在输出
          </div>
        </div>

        <section v-if="demandSignalCards.length" class="demand-focus-panel">
          <div class="price-focus-head">
            <div>
              <h3>需求与价格判断</h3>
              <p>先看未来天气、热门事件、周边客流和价格信号，再决定房价怎么动。</p>
            </div>
            <span>{{ demandSignalCards.length }} 个信号</span>
          </div>
          <div class="demand-grid">
            <article v-for="item in demandSignalCards" :key="item.key" class="demand-card" :class="`signal-${item.tone}`">
              <div class="demand-card-head">
                <h4>{{ item.signal }}</h4>
                <span>{{ item.judgment }}</span>
              </div>
              <strong>{{ item.impact }}</strong>
              <p>{{ item.basis }}</p>
              <small v-if="item.verify && item.verify !== '无'">{{ item.verify }}</small>
            </article>
          </div>
        </section>

        <section v-if="priceMoveCards.length" class="price-focus-panel">
          <div class="price-focus-head">
            <div>
              <h3>房型调价建议</h3>
              <p>结合上方需求信号，落到每个房型本周期建议价、变价幅度和调价理由。</p>
            </div>
            <span>{{ priceMoveCards.length }} 个房型</span>
          </div>

          <div class="price-move-grid">
            <article v-for="card in priceMoveCards" :key="card.roomName" class="price-move-card" :class="`move-${card.moveType}`">
              <div class="price-move-top">
                <h4>{{ card.roomName || '未命名房型' }}</h4>
                <span>{{ card.moveLabel }}</span>
              </div>

              <div class="price-stack">
                <div class="price-row">
                  <span>当前挂牌价</span>
                  <strong class="current-price">{{ card.currentLabel }}</strong>
                </div>
                <div class="price-row target-row">
                  <span>建议执行价</span>
                  <strong class="target-price">{{ card.targetLabel }}</strong>
                </div>
                <div class="price-row floor-row">
                  <span>最低保护价</span>
                  <strong>{{ card.floorPrice }}</strong>
                </div>
              </div>

              <dl>
                <div>
                  <dt>调价逻辑</dt>
                  <dd>{{ card.action }}</dd>
                </div>
                <div>
                  <dt>执行时点</dt>
                  <dd>{{ card.timing }}</dd>
                </div>
                <div>
                  <dt>风险</dt>
                  <dd>{{ card.risk }}</dd>
                </div>
              </dl>
            </article>
          </div>
        </section>

        <section v-if="sourceEvidenceCards.length" class="source-evidence-panel">
          <div class="price-focus-head">
            <div>
              <h3>关键依据来源</h3>
              <p>只保留对需求和价格判断有帮助的来源，弱化无效待核实表格。</p>
            </div>
          </div>
          <div class="source-evidence-grid">
            <article v-for="item in sourceEvidenceCards" :key="item.key" class="source-evidence-card">
              <div>
                <strong>{{ item.source }}</strong>
                <span>{{ item.trust }}</span>
              </div>
              <p>{{ item.data }}</p>
              <small v-if="item.usage">{{ item.usage }}</small>
            </article>
          </div>
        </section>

        <AiPlanDocument
          v-if="aiText"
          :content="aiText"
          :hidden-section-patterns="['可执行动作清单', '风险核验与复盘指标']"
        />
        <div v-else class="flex min-h-[360px] flex-col items-center justify-center text-center">
          <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-bamboo-50 text-bamboo-800"><Loader2 class="h-7 w-7 animate-spin" /></div>
          <h3 class="mt-5 text-base font-semibold text-bamboo-900">正在生成定价方案</h3>
          <p class="mt-2 max-w-md text-sm leading-6 text-warm-500">等待 AI 返回完整内容，页面不会生成前端兜底方案。</p>
        </div>
      </section>

      <div v-else-if="generating" class="flex min-h-[620px] flex-col items-center justify-center text-center">
        <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-bamboo-50 text-bamboo-800"><Loader2 class="h-7 w-7 animate-spin" /></div>
        <h3 class="mt-5 text-base font-semibold text-bamboo-900">正在生成定价方案</h3>
        <p class="mt-2 max-w-md text-sm leading-6 text-warm-500">正在结合房型、挂牌价、周期、周边情报和渠道边界组织可执行动作。</p>
      </div>
      <div v-else class="flex min-h-[620px] flex-col items-center justify-center text-center">
        <FileText class="h-10 w-10 text-warm-300" />
        <h3 class="mt-4 text-base font-semibold text-bamboo-900">暂无生成结果</h3>
        <p class="mt-2 text-sm text-warm-500">可以重新生成，或返回修改定价条件。</p>
      </div>

      <p v-if="errorMessage" class="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{{ errorMessage }}</p>
    </main>
  </div>
</template>

<style scoped>
.primary-button,
.secondary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  border-radius: 0.7rem;
  padding: 0.6rem 0.85rem;
  font-size: 0.75rem;
  font-weight: 600;
  transition: 150ms ease;
}
.primary-button { background: #234d32; color: white; }
.primary-button:hover { background: #183b26; }
.secondary-button { border: 1px solid #e5d8c5; background: white; color: #5f5143; }
.secondary-button:hover { border-color: #8cac77; background: #f7faf4; }
.primary-button:disabled,
.secondary-button:disabled { cursor: not-allowed; opacity: 0.5; }
.done-button { border-color: #7ba569; background: #edf6e9; color: #234d32; }
.icon-button {
  display: inline-flex;
  height: 2.25rem;
  width: 2.25rem;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: 1px solid #e5d8c5;
  border-radius: 0.65rem;
  color: #776655;
}
.icon-button:hover { border-color: #8cac77; background: #f7faf4; color: #234d32; }
.metric-card {
  min-height: 110px;
  border: 1px solid #eadfce;
  border-radius: 0.85rem;
  background: white;
  padding: 1.1rem;
  text-align: center;
  box-shadow: 0 8px 18px rgb(54 79 50 / 0.05);
}
.metric-card strong { display: block; color: #0f5a2a; font-size: 1.2rem; line-height: 1.3; }
.metric-card span { margin-top: 0.35rem; display: block; font-size: 0.78rem; font-weight: 700; color: #6c5b4b; }
.metric-card small { margin-top: 0.35rem; display: block; color: #9a8772; font-size: 0.68rem; line-height: 1.5; }
.plan-section {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  margin-top: 1.25rem;
  border: 1px solid #eadfce;
  border-radius: 0.95rem;
  background: white;
  padding: 1.35rem;
  box-shadow: 0 8px 20px rgb(54 79 50 / 0.05);
}
.section-title { display: flex; align-items: center; gap: 0.5rem; color: #1f3f2b; }
.section-title h3 { font-size: 0.98rem; font-weight: 700; }
.raw-preview summary { list-style: none; }
.raw-preview summary::-webkit-details-marker { display: none; }
.section-badge {
  border-radius: 999px;
  background: #f5f0e8;
  padding: 0.2rem 0.55rem;
  color: #9a6f21;
  font-size: 0.68rem;
  font-weight: 700;
}
.price-focus-panel {
  margin-bottom: 1.35rem;
  border-radius: 0.95rem;
  border: 1px solid #eadfce;
  background: #fffdfa;
  padding: 1rem;
}
.demand-focus-panel,
.source-evidence-panel {
  margin-bottom: 1.35rem;
  border-radius: 0.95rem;
  border: 1px solid #eadfce;
  background: white;
  padding: 1rem;
}
.price-focus-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.9rem;
}
.price-focus-head h3 {
  color: #203f2b;
  font-size: 1rem;
  font-weight: 800;
}
.price-focus-head p {
  margin-top: 0.2rem;
  color: #8b7460;
  font-size: 0.75rem;
}
.price-focus-head > span {
  flex-shrink: 0;
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.25rem 0.65rem;
  color: #315b37;
  font-size: 0.7rem;
  font-weight: 800;
}
.demand-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
}
.demand-card {
  min-width: 0;
  border-radius: 0.9rem;
  border: 1px solid #eadfce;
  background: #fffdfa;
  padding: 1rem;
  box-shadow: 0 8px 18px rgb(54 79 50 / 0.04);
}
.demand-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}
.demand-card-head h4 {
  color: #203f2b;
  font-size: 0.92rem;
  font-weight: 850;
  line-height: 1.45;
}
.demand-card-head span {
  flex-shrink: 0;
  border-radius: 999px;
  background: #f5f0e8;
  padding: 0.22rem 0.55rem;
  color: #6c5b4b;
  font-size: 0.68rem;
  font-weight: 900;
}
.demand-card strong {
  margin-top: 0.85rem;
  display: block;
  color: #0f5a2a;
  font-size: 1rem;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.demand-card p {
  margin-top: 0.55rem;
  color: #66594b;
  font-size: 0.78rem;
  line-height: 1.75;
  overflow-wrap: anywhere;
}
.demand-card small {
  margin-top: 0.7rem;
  display: block;
  color: #9a8772;
  font-size: 0.68rem;
  line-height: 1.55;
}
.signal-up { border-color: #b9d7aa; background: #fbfff8; }
.signal-up .demand-card-head span { background: #eef7ea; color: #166534; }
.signal-down { border-color: #f0d1b8; background: #fffaf6; }
.signal-down .demand-card-head span { background: #fff1e8; color: #b45309; }
.signal-pending .demand-card-head span { background: #faf7f1; color: #8b7460; }
.source-evidence-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 0.85rem;
}
.source-evidence-card {
  min-width: 0;
  border-radius: 0.8rem;
  background: #faf7f1;
  padding: 0.85rem;
}
.source-evidence-card div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.65rem;
}
.source-evidence-card strong {
  color: #203f2b;
  font-size: 0.8rem;
  line-height: 1.45;
}
.source-evidence-card span {
  flex-shrink: 0;
  color: #9a6f21;
  font-size: 0.68rem;
  font-weight: 900;
}
.source-evidence-card p {
  margin-top: 0.45rem;
  color: #66594b;
  font-size: 0.75rem;
  line-height: 1.65;
}
.source-evidence-card small {
  margin-top: 0.35rem;
  display: block;
  color: #9a8772;
  font-size: 0.68rem;
}
.price-move-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 1rem;
}
.price-move-card {
  min-width: 0;
  border-radius: 0.9rem;
  border: 1px solid #eadfce;
  background: white;
  padding: 1rem;
  box-shadow: 0 8px 18px rgb(54 79 50 / 0.04);
}
.price-move-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.7rem;
}
.price-move-top h4 {
  min-width: 0;
  color: #203f2b;
  font-size: 0.92rem;
  font-weight: 800;
  line-height: 1.45;
}
.price-move-top span {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 0.22rem 0.55rem;
  font-size: 0.68rem;
  font-weight: 900;
}
.move-down .price-move-top span { background: #fff1e8; color: #b45309; }
.move-up .price-move-top span { background: #eef7ea; color: #166534; }
.move-flat .price-move-top span { background: #f5f0e8; color: #6c5b4b; }
.move-mixed .price-move-top span { background: #eef4ff; color: #3451a3; }
.move-pending .price-move-top span { background: #faf7f1; color: #8b7460; }
.price-stack {
  margin-top: 0.95rem;
  display: grid;
  gap: 0.65rem;
}
.price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border-radius: 0.75rem;
  background: #faf7f1;
  padding: 0.75rem 0.85rem;
}
.price-row span {
  flex-shrink: 0;
  color: #9a8772;
  font-size: 0.72rem;
  font-weight: 800;
}
.price-row strong {
  min-width: 0;
  line-height: 1.2;
  overflow-wrap: anywhere;
  text-align: right;
}
.target-row {
  background: #f4f8f0;
}
.floor-row {
  border: 1px dashed #d9c8ae;
  background: white;
}
.current-price {
  color: #6c5b4b;
  font-size: 1.2rem;
}
.target-price {
  color: #0f5a2a;
  font-size: 1.65rem;
}
@media (max-width: 760px) {
  .price-move-grid {
    grid-template-columns: 1fr;
  }
}
.price-move-card dl {
  margin-top: 0.85rem;
  display: grid;
  gap: 0.55rem;
}
.price-move-card dt {
  color: #ad8a3d;
  font-size: 0.66rem;
  font-weight: 800;
}
.price-move-card dd {
  margin-top: 0.12rem;
  color: #66594b;
  font-size: 0.74rem;
  line-height: 1.65;
  overflow-wrap: anywhere;
  white-space: pre-line;
}
.price-card {
  min-width: 0;
  border: 1px solid #eadfce;
  border-radius: 0.85rem;
  background: #fffaf2;
  padding: 1rem;
}
.price-card h4 { color: #203f2b; font-size: 0.9rem; font-weight: 800; }
.price-card p { margin-top: 0.2rem; color: #8b7460; font-size: 0.72rem; }
.price-card span {
  flex-shrink: 0;
  border-radius: 999px;
  background: white;
  padding: 0.18rem 0.5rem;
  color: #315b37;
  font-size: 0.66rem;
  font-weight: 800;
}
.price-card strong {
  display: block;
  margin-top: 0.85rem;
  color: #0f5a2a;
  font-size: 1rem;
  line-height: 1.55;
}
.price-card dl { margin-top: 0.85rem; display: grid; gap: 0.55rem; }
.price-card div { min-width: 0; }
.price-card dt { color: #ad8a3d; font-size: 0.66rem; font-weight: 800; }
.price-card dd { margin-top: 0.15rem; color: #66594b; font-size: 0.74rem; line-height: 1.6; overflow-wrap: anywhere; }
.strategy-table {
  width: 100%;
  min-width: 760px;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 0.76rem;
}
.strategy-table th { background: #f5f0e8; color: #315b37; font-weight: 700; text-align: left; }
.strategy-table th,
.strategy-table td {
  border-bottom: 1px solid #eadfce;
  padding: 0.75rem;
  vertical-align: top;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-line;
}
.paragraph-line,
.bullet-list li {
  overflow-wrap: anywhere;
  white-space: pre-line;
  border-radius: 0.6rem;
  background: #faf7f1;
  padding: 0.65rem 0.85rem;
  color: #5f5143;
  font-size: 0.8rem;
  line-height: 1.75;
}
.bullet-list { display: grid; gap: 0.5rem; }
.empty-block,
.empty-action {
  margin-top: 1rem;
  border-radius: 0.75rem;
  background: #faf7f1;
  padding: 0.85rem 1rem;
  color: #8b7460;
  font-size: 0.8rem;
}
.bottom-actions {
  margin-top: 1.25rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border-radius: 0.95rem;
  background: #eef7ea;
  padding: 1rem;
}
.bottom-actions h3 { font-size: 0.95rem; font-weight: 700; color: #203f2b; }
.bottom-actions p { margin-top: 0.2rem; font-size: 0.75rem; color: #6f8067; }
</style>
