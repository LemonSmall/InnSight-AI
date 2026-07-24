<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useHotelStore } from '@/stores/hotel'
import { collectStreamContent } from '@/api/content'
import { buildContentAiParams } from '@/utils/aiContextParams'
import { copyTextToClipboard } from '@/utils/clipboard'
import { loadAiPageState, saveAiPageState, watchAiPageState } from '@/utils/aiPageState'
import { elementNode, exportMarkdown, exportPdfElement, safeFilename } from '@/utils/exportDocument'
import {
  cleanMarkdown,
  columnIndex,
  firstTable,
  normalizePlanMarkdown,
  parsePlan,
  sectionByTitle as findPlanSection,
  type PlanSection,
  type PlanTable,
} from '@/utils/planMarkdown'
import AiPlanDocument from '@/components/ai/AiPlanDocument.vue'
import {
  buildStrategyParams,
  channelOptions,
  createDefaultStrategyForm,
  objectiveOptions,
  occasionOptions,
  optionLabel,
  periodOptions,
  type StrategyForm,
} from '@/utils/strategyConfig'
import {
  ArrowLeft,
  Check,
  CheckCircle2,
  ClipboardList,
  Copy,
  Download,
  FileText,
  Grid2X2,
  Loader2,
  PenLine,
  RefreshCw,
  Send,
  Sparkles,
} from 'lucide-vue-next'

interface ActionRow {
  key: string
  label: string
  content: string
  module: string
  status: string
}

const router = useRouter()
const hotelStore = useHotelStore()
const pageStateKey = 'strategy'
const form = reactive<StrategyForm>(createDefaultStrategyForm())
const generating = ref(false)
const statusText = ref('')
const errorMessage = ref('')
const aiText = ref('')
const runId = ref('')
const copied = ref(false)
const toast = ref('')
let pageAlive = true
let stopWatchingState: (() => void) | null = null

const hotelName = computed(() => hotelStore.config.poiName || hotelStore.config.name || '本店')
const cityName = computed(() => hotelStore.config.poiCity || String(hotelStore.config.city || '').split('/').filter(Boolean).pop() || '本地')
const selectedChannels = computed(() => form.channels.map(channel => optionLabel(channelOptions, channel)).filter(Boolean))
const displayStatusText = computed(() => {
  if (!statusText.value) return ''
  if (!generating.value && /^正在|生成仍在后台/.test(statusText.value)) return ''
  return statusText.value
})

const sections = computed(() => parsePlan(aiText.value))
const normalizedAiText = computed(() => normalizePlanMarkdown(aiText.value))
const planTitle = computed(() => sections.value.find(section => section.level === 1)?.title || '营销策略执行方案')

function sectionByTitle(patterns: RegExp[]) {
  return findPlanSection(sections.value, patterns)
}

const kpiSection = computed(() => sectionByTitle([/核心目标|KPI|指标|目标/]))
const tagsSection = computed(() => sectionByTitle([/标签|定位|卖点/]))
const contextSection = computed(() => sectionByTitle([/天气|热门事件|周边|机会判断|综合分析|环境机会/]))
const timelineSection = computed(() => sectionByTitle([/时间表|执行时间|阶段|节奏/]))
const channelSection = computed(() => sectionByTitle([/渠道|内容计划|发布计划/]))
const activitySection = computed(() => sectionByTitle([/活动|定价|承接/]))
const copySection = computed(() => sectionByTitle([/文案|话术|素材/]))
const actionSection = computed(() => sectionByTitle([/执行动作|底部执行|按钮/]))

const strategyTags = computed(() => {
  const tags = tagsSection.value?.bullets?.length ? tagsSection.value.bullets : []
  return tags.slice(0, 8)
})

const kpiCards = computed(() => {
  const table = firstTable(kpiSection.value)
  if (!table) return []
  const nameIndex = columnIndex(table, ['指标', '目标', 'KPI'], 0)
  const valueIndex = columnIndex(table, ['目标值', '数值', '结果'], 1)
  const basisIndex = columnIndex(table, ['依据', '原因', '说明'], 2)
  const verifyIndex = columnIndex(table, ['待核实'], 3)
  return table.rows
    .map((row, index) => {
      const metric = cleanMarkdown(row[nameIndex] || '')
      const target = cleanMarkdown(row[valueIndex] || '')
      const basis = cleanMarkdown(row[basisIndex] || '')
      const verify = cleanMarkdown(row[verifyIndex] || '')
      return {
        key: `${metric}-${target}-${index}`,
        metric,
        target: compactGoalValue(target),
        basis,
        verify,
        method: summarizeGoalMethod(metric, basis),
      }
    })
    .filter(item => item.metric || item.target)
    .slice(0, 4)
})

const timelineTable = computed(() => firstTable(timelineSection.value))
const displayTimelineTable = computed(() => withoutStatusColumn(timelineTable.value))
const channelTable = computed(() => firstTable(channelSection.value))
const displayChannelTable = computed(() => sanitizeDisplayTable(channelTable.value))
const activityTable = computed(() => firstTable(activitySection.value))
const displayActivityTable = computed(() => sanitizeDisplayTable(activityTable.value))
const executionFocusCards = computed(() => {
  const table = timelineTable.value
  if (!table?.rows?.length) return []
  const phaseIndex = columnIndex(table, ['阶段'], 0)
  const timeIndex = columnIndex(table, ['时间'], 1)
  const focusIndex = columnIndex(table, ['重点'], 2)
  const actionIndex = columnIndex(table, ['具体动作', '动作'], 3)
  const ownerIndex = columnIndex(table, ['渠道/负责人', '渠道', '负责人'], 4)
  return table.rows
    .map(row => ({
      phase: cleanMarkdown(row[phaseIndex] || ''),
      time: cleanMarkdown(row[timeIndex] || ''),
      focus: cleanMarkdown(row[focusIndex] || ''),
      action: cleanMarkdown(row[actionIndex] || ''),
      owner: cleanMarkdown(row[ownerIndex] || ''),
    }))
    .filter(item => item.phase || item.focus || item.action)
})
const copyItems = computed(() => {
  const section = copySection.value
  if (!section) return []
  return [...section.bullets, ...section.paragraphs]
    .map(formatCopyItem)
    .filter(Boolean)
})

const actionRows = computed<ActionRow[]>(() => {
  const table = firstTable(actionSection.value)
  const fromTable = table?.rows?.length ? parseActionRows(table) : []
  return fromTable.length ? fromTable : derivedActionRows.value
})
const reusableActionRows = computed(() => buildReusableActionRows(actionRows.value).slice(0, 4))

const hasAlignedContent = computed(() => Boolean(timelineTable.value || channelTable.value || activityTable.value || actionRows.value.length))

const renderedSectionTitles = computed(() => new Set([
  kpiSection.value?.title,
  tagsSection.value?.title,
  contextSection.value?.title,
  timelineSection.value?.title,
  channelSection.value?.title,
  activitySection.value?.title,
  copySection.value?.title,
  actionSection.value?.title,
  sections.value.find(section => section.level === 1)?.title,
].filter(Boolean)))

const extraSections = computed(() => sections.value.filter(section => (
  !renderedSectionTitles.value.has(section.title) &&
  !isRiskReviewSection(section.title) &&
  !isFallbackOverviewSection(section.title) &&
  Boolean(section.paragraphs.length || section.bullets.length || section.tables.length)
)))

const derivedActionRows = computed<ActionRow[]>(() => {
  const rows: ActionRow[] = []
  const channels = displayChannelTable.value
  if (channels?.rows?.length) {
    const channelIndex = columnIndex(channels, ['渠道'], 0)
    const topicIndex = columnIndex(channels, ['内容主题', '主题'], 3)
    const carryIndex = columnIndex(channels, ['承接动作', '承接'], 4)
    channels.rows.slice(0, 4).forEach((row, index) => {
      const channel = cleanMarkdown(row[channelIndex] || '')
      const topic = cleanMarkdown(row[topicIndex] || '')
      const carry = cleanMarkdown(row[carryIndex] || '')
      if (!channel && !topic && !carry) return
      rows.push({
        key: `channel_${index + 1}`,
        label: actionLabelForChannel(channel, topic),
        content: [topic, carry].filter(Boolean).join('；') || '根据渠道计划生成执行内容',
        module: moduleForChannel(channel),
        status: 'pending',
      })
    })
  }
  if (!rows.length) {
    executionFocusCards.value.slice(0, 4).forEach((card, index) => {
      rows.push({
        key: `phase_${index + 1}`,
        label: card.phase ? `推进${card.phase}` : `执行阶段 ${index + 1}`,
        content: [card.focus, card.action].filter(Boolean).join('；') || '根据执行时间表推进',
        module: card.owner || '运营执行',
        status: 'pending',
      })
    })
  }
  return rows
})

const bossSummary = computed(() => {
  const goal = kpiCards.value[0]
  const firstStep = executionFocusCards.value[0]
  const channels = selectedChannels.value.length ? selectedChannels.value.join('、') : '已选渠道'
  return {
    target: goal ? `${goal.metric}：${goal.target}` : `围绕${form.targetAudience || '目标客群'}提升${optionLabel(objectiveOptions, form.objective)}`,
    firstMove: firstStep?.action || firstStep?.focus || '先确认今日可执行资源，再按渠道发布内容并承接咨询。',
    channels,
  }
})

const simpleSteps = computed(() => {
  const fromPlan = executionFocusCards.value
    .map((card, index) => ({
      key: `${card.phase}-${index}`,
      title: card.phase || `第 ${index + 1} 步`,
      time: card.time || '今天可执行',
      focus: compactSentence(card.focus || card.action || '按方案推进', 32),
      action: compactSentence(card.action || card.focus || '确认责任人并执行', 82),
      owner: card.owner || '前台/店长',
    }))
    .filter(item => item.action)
  if (fromPlan.length) return fromPlan.slice(0, 5)
  return [
    { key: 'default-1', title: '确认卖点', time: '马上', focus: '确认今天主推内容', action: `围绕${hotelName.value}的房型、价格、周边和权益，选出 1 个最容易成交的卖点。`, owner: '店长' },
    { key: 'default-2', title: '发布内容', time: '今天', focus: '按渠道发出去', action: `优先在${selectedChannels.value.join('、') || '小红书、朋友圈、海报'}发布同一个主题，避免每个平台说法不一致。`, owner: '前台/运营' },
    { key: 'default-3', title: '承接咨询', time: '发布后', focus: '及时转化', action: '有人咨询时直接给房型、价格、位置和预订入口，减少来回问答。', owner: '前台' },
  ]
})

const opportunityHighlights = computed(() => {
  const table = firstTable(contextSection.value)
  if (table?.rows?.length) {
    const signalIndex = columnIndex(table, ['信号', '类型', '机会'], 0)
    const actualIndex = columnIndex(table, ['实际情况', '情况', '上下文'], 1)
    const adjustmentIndex = columnIndex(table, ['策略调整', '调整', '动作'], 3)
    return table.rows
      .map((row, index) => ({
        key: `${row[signalIndex] || ''}-${index}`,
        signal: compactSentence(cleanMarkdown(row[signalIndex] || '机会信号'), 18),
        detail: compactSentence(cleanMarkdown(row[actualIndex] || row[adjustmentIndex] || ''), 52),
        action: compactSentence(cleanMarkdown(row[adjustmentIndex] || ''), 64),
      }))
      .filter(item => item.detail || item.action)
      .slice(0, 3)
  }
  return [...(contextSection.value?.bullets || []), ...(contextSection.value?.paragraphs || [])]
    .map((item, index) => ({
      key: `context-${index}`,
      signal: `机会 ${index + 1}`,
      detail: compactSentence(item, 52),
      action: '',
    }))
    .slice(0, 3)
})

function persistState() {
  saveAiPageState(pageStateKey, {
    form: JSON.parse(JSON.stringify(form)),
    runId: runId.value,
    generating: generating.value,
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
  statusText.value = restored.statusText || ''
  aiText.value = restored.aiText || ''
  errorMessage.value = restored.errorMessage || ''
  return true
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (!applyStoredState(restored)) {
    router.replace('/strategy')
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

async function generate() {
  generating.value = true
  errorMessage.value = ''
  aiText.value = ''
  statusText.value = '正在读取酒店资料与实时经营上下文'
  persistState()
  let completedNormally = false
  try {
    const params = buildStrategyParams(form)
    params.message = `请生成营销策略执行方案。周期：${optionLabel(periodOptions, form.period)}；目标：${optionLabel(objectiveOptions, form.objective)}；客群：${form.targetAudience || '由 AI 基于上下文设定'}；渠道：${selectedChannels.value.join('、') || '待设定'}。

输出必须严格按以下模板，先给目标和执行步骤：
# 营销策略执行方案
## 核心目标与 KPI
| 指标 | 目标值 | 依据 | 待核实 |
| --- | --- | --- | --- |
必须突出本次要实现的目标，不要写空泛口号；无法核实的指标不要硬写数字。
## 天气、热门事件与周边机会判断
| 信号 | 实际情况 | 对客群/需求的影响 | 策略调整 | 原因 |
| --- | --- | --- | --- | --- |
必须优先结合未来天气、热门事件、周边景点/商圈/交通/展会/演出等实际上下文；没有拿到真实信号时写“未获取，不作为策略依据”，不要编造。
## 执行时间表
| 阶段 | 时间 | 重点 | 具体动作 | 渠道/负责人 |
| --- | --- | --- | --- | --- |
不要输出“状态”列；具体动作必须说明怎么执行，以及动作与天气/热门事件/周边机会的关系。
## 各渠道内容计划
| 渠道 | 定位 | 依据 | 内容主题 | 承接动作 | 目标 |
| --- | --- | --- | --- | --- | --- |
## 策略标签
- 标签
## 活动与定价承接
| 项目 | 当前依据 | 建议动作 | 执行条件 | 说明原因 |
| --- | --- | --- | --- | --- |
## 核心文案示例
- 至少输出 3 条文案；必须覆盖用户选择的重点渠道，结合当前天气、热门事件、周边机会或客群；每条写清适用渠道，不要只给一句空泛标题。
## 底部执行动作
| actionKey | 按钮文案 | 执行内容 | 调用模块 |
| --- | --- | --- | --- |

禁止输出“风险核验与复盘指标”；删除无效、待核实、没有业务价值的填充内容；每一段都要服务“为什么这么做”和“具体怎么做”。`
    aiText.value = await collectStreamContent('strategy', buildContentAiParams(hotelStore, 'strategy', params), {
      onStatus(message) {
        statusText.value = message || '正在生成营销策略'
        persistState()
      },
      onChunk(_chunk, content) {
        aiText.value = content
        statusText.value = '正在生成营销策略'
        persistState()
      },
    })
    statusText.value = '营销策略已生成'
    completedNormally = true
  } catch (error: any) {
    if (isRecoverableStreamError(error) && aiText.value.trim()) {
      statusText.value = '生成结果可能不完整'
      errorMessage.value = ''
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
    errorMessage.value = error?.message || '营销策略生成失败'
    statusText.value = ''
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

function backToStrategy() {
  if (generating.value) {
    statusText.value = '正在生成中，请稍候'
    persistState()
    return
  }
  router.push('/strategy')
}

async function copyResult() {
  if (!aiText.value) return
  const ok = await copyTextToClipboard(normalizedAiText.value || aiText.value)
  copied.value = ok
  if (!ok) flash('复制失败')
  window.setTimeout(() => { copied.value = false }, 1600)
}

async function exportResult(format: 'markdown' | 'pdf') {
  if (!aiText.value) return
  const filename = safeFilename(`${planTitle.value}-${form.targetAudience || form.objective || 'AI智能设定'}`)
  if (format === 'markdown') {
    exportMarkdown(filename, planTitle.value, normalizedAiText.value || aiText.value)
    return
  }
  const ok = await exportPdfElement(planTitle.value, elementNode('#strategy-export-content'), filename)
  if (!ok) errorMessage.value = '导出内容为空，请稍后重试'
}

async function copyText(text: string) {
  const ok = await copyTextToClipboard(text)
  flash(ok ? '已复制' : '复制失败')
}

async function executeAction(key: string) {
  const action = reusableActionRows.value.find(item => item.key === key)
  if (!action) return
  const target = targetForAction(action)
  if (target) {
    saveReuseDraft(target.moduleKey, action)
    router.push({ path: target.path, query: { reuse: 'strategy' } })
    flash('已带着策略配置跳转，原生成内容不会被覆盖')
    return
  }
  await copyActionContent(action)
  flash('已复制执行内容，可粘贴到对应平台处理')
}

function tableCell(row: string[], index: number) {
  return row[index] || '待核实'
}

function sanitizeDisplayTable(table?: PlanTable | null) {
  if (!table) return null
  return {
    headers: table.headers,
    rows: table.rows.filter(row => isUsefulDisplayRow(table.headers, row)),
  }
}

function isUsefulDisplayRow(headers: string[], row: string[]) {
  const cells = row.map(cell => cleanMarkdown(cell || ''))
  const filled = cells.filter(Boolean)
  if (!filled.length) return false
  if (filled.every(cell => /^待核实$|^pending$|^[-:：]+$/i.test(cell))) return false
  const normalizedHeaders = headers.map(normalizeDisplayCell).filter(Boolean)
  const headerLikeCount = filled.filter(cell => normalizedHeaders.includes(normalizeDisplayCell(cell))).length
  return headerLikeCount < Math.min(filled.length, Math.max(2, Math.ceil(normalizedHeaders.length * 0.6)))
}

function normalizeDisplayCell(value: string) {
  return cleanMarkdown(value).replace(/\s+/g, '').toLowerCase()
}

function isRiskReviewSection(title: string) {
  return /风险核验|复盘指标|风险复盘/.test(title)
}

function isFallbackOverviewSection(title: string) {
  return title === '方案概览'
}

function withoutStatusColumn(table?: PlanTable) {
  if (!table) return null
  const statusIndex = table.headers.findIndex(header => /状态|status/i.test(header))
  if (statusIndex < 0) return table
  const actionIndex = columnIndex(table, ['具体动作', '动作', '执行内容'], Math.max(0, statusIndex - 1))
  return {
    headers: table.headers.filter((_, index) => index !== statusIndex),
    rows: table.rows.map(row => {
      const next = row.slice()
      const status = cleanMarkdown(next[statusIndex] || '')
      if (status && !/pending|待执行|进行中|完成|已完成|done/i.test(status)) {
        next[actionIndex] = [next[actionIndex], status].filter(Boolean).join('\n')
      }
      next.splice(statusIndex, 1)
      return next
    }),
  }
}

function formatCopyItem(value: string) {
  return cleanMarkdown(value)
    .replace(/^[-*]\s*/, '')
    .replace(/^\d+[.)]\s*/, '')
    .replace(/^\|+\s*/, '')
    .replace(/\s*\|+$/, '')
    .trim()
}

function parseActionRows(table: PlanTable): ActionRow[] {
  const keyIndex = columnIndex(table, ['actionKey', '动作', '编号'], 0)
  const labelIndex = columnIndex(table, ['按钮文案', '按钮', '名称'], 1)
  const contentIndex = columnIndex(table, ['执行内容', '内容', '动作'], 2)
  const moduleIndex = columnIndex(table, ['调用模块', '模块', '去向'], 3)
  const statusIndex = columnIndex(table, ['状态'], 4)
  return table.rows
    .map((row, index) => {
      const label = cleanActionCell(row[labelIndex] || row[keyIndex] || `执行动作 ${index + 1}`)
      return {
        key: cleanActionCell(row[keyIndex] || label || `action_${index + 1}`),
        label,
        content: cleanActionCell(row[contentIndex] || ''),
        module: cleanActionCell(row[moduleIndex] || ''),
        status: cleanActionCell(row[statusIndex] || 'pending'),
      }
    })
    .filter(row => row.label)
}

function cleanActionCell(value: string) {
  return cleanMarkdown(value)
    .replace(/^\|+\s*/, '')
    .replace(/\s*\|+$/, '')
    .trim()
}

function compactGoalValue(value: string) {
  const text = cleanMarkdown(value || '待核实')
  if (/待核实|无|暂无/.test(text)) return '待核实'
  if (text.length <= 10) return text
  return text.replace(/[，。,；;].*$/, '').slice(0, 14)
}

function summarizeGoalMethod(metric: string, basis: string) {
  const text = cleanMarkdown(basis || '')
  if (/小红书|种草|笔记|内容/.test(metric + text)) return '内容种草带来咨询'
  if (/朋友圈|社群|微信|私域/.test(metric + text)) return '私域互动承接转化'
  if (/OTA|详情页|预订|转化/.test(metric + text)) return '优化详情页和预订入口'
  if (/发布|完成率|执行/.test(metric + text)) return '按节奏完成发布和复盘'
  return text ? compactSentence(text, 24) : '按方案动作推进'
}

function compactSentence(value: string, max = 36) {
  const text = cleanMarkdown(value || '').replace(/\s+/g, ' ')
  return text.length > max ? `${text.slice(0, max)}...` : text
}

function actionLabelForChannel(channel: string, topic: string) {
  const text = `${channel} ${topic}`
  if (/小红书/.test(text)) return '生成小红书种草笔记'
  if (/朋友圈|社群|微信|私域/.test(text)) return '生成私域承接话术'
  if (/OTA/.test(text)) return '优化OTA详情页'
  if (/公众号/.test(text)) return '生成公众号内容'
  return channel ? `生成${channel}执行内容` : '生成渠道执行内容'
}

function moduleForChannel(channel: string) {
  if (/小红书/.test(channel)) return '小红书内容'
  if (/朋友圈|社群|微信|私域/.test(channel)) return '私域运营'
  if (/海报|图片|物料/.test(channel)) return '海报物料'
  if (/抖音|视频|口播|短视频/.test(channel)) return '短视频'
  if (/OTA/.test(channel)) return 'OTA运营'
  if (/公众号/.test(channel)) return '公众号内容'
  return '内容生成'
}

function targetForAction(action: ActionRow) {
  const text = `${action.label} ${action.module} ${action.content}`
  if (/小红书|xhs/i.test(text)) return { path: '/xhs', moduleKey: 'xhs' }
  if (/朋友圈|社群|私域|微信|wechat/i.test(text)) return { path: '/wechat', moduleKey: 'wechat' }
  if (/海报|图片|物料|poster/i.test(text)) return { path: '/poster', moduleKey: 'poster' }
  if (/抖音|视频|口播|短视频|video/i.test(text)) return { path: '/video', moduleKey: 'video' }
  return null
}

function buildReusableActionRows(rows: ActionRow[]) {
  const desired = [
    { moduleKey: 'xhs', path: '/xhs', label: '生成小红书', module: '小红书内容' },
    { moduleKey: 'wechat', path: '/wechat', label: '生成朋友圈', module: '朋友圈文案' },
    { moduleKey: 'poster', path: '/poster', label: '生成营销海报', module: '营销海报' },
    { moduleKey: 'video', path: '/video', label: '生成短视频脚本', module: '视频口播' },
  ]
  return desired.map((item, index) => {
    const matched = rows.find(row => targetForAction(row)?.moduleKey === item.moduleKey)
    const fallbackContent = matched?.content
      || simpleSteps.value[index]?.action
      || bossSummary.value.firstMove
      || '根据当前营销策略继续生成内容'
    return {
      key: item.moduleKey,
      label: matched?.label || item.label,
      content: fallbackContent,
      module: matched?.module || item.module,
      status: matched?.status || 'pending',
    }
  })
}

function saveReuseDraft(moduleKey: string, action: ActionRow) {
  saveAiPageState(`strategy-reuse:${moduleKey}`, {
    source: 'strategy',
    title: action.label,
    content: formatActionContent(action),
    savedAt: Date.now(),
  })
}

function saveActionDraft(moduleKey: string, action: ActionRow) {
  const content = formatActionContent(action)
  if (moduleKey === 'xhs') {
    saveAiPageState('xhs', {
      selectedTopics: ['escape'],
      selectedTone: 'deal',
      note: content,
      customTopic: action.label,
      generated: true,
      title: action.label,
      body: content,
      tags: [],
      xhsImageUrl: '',
    })
    return
  }
  if (moduleKey === 'wechat') {
    saveAiPageState('wechat', {
      slots: { morning: true, noon: true, evening: true },
      style: 'auto',
      length: 'mid',
      note: content,
      generated: true,
      outputs: [
        { id: 'morning', label: '早间', time: '08:00', typeLabel: '策略承接', typeClass: 'bg-amber-50 text-amber-700', content },
        { id: 'noon', label: '午间', time: '12:00', typeLabel: '策略承接', typeClass: 'bg-blue-50 text-blue-600', content },
        { id: 'evening', label: '晚间', time: '20:30', typeLabel: '策略承接', typeClass: 'bg-purple-50 text-purple-700', content },
      ],
    })
    return
  }
  if (moduleKey === 'article') {
    saveAiPageState('article', {
      articleTitle: action.label,
      selectedStyle: 'teal_tech',
      selectedLength: 'medium',
      step: 'done',
      sections: [{ heading: action.label, paragraphs: content.split(/\n+/).filter(Boolean), image: '' }],
      ending: '',
    })
    return
  }
  if (moduleKey === 'poster') {
    saveAiPageState('poster', {
      mode: 'text2img',
      t2iTheme: action.label,
      t2iContent: content,
      t2iStyle: 'conversion',
      t2iScene: 'promo',
      t2iGenerated: true,
      t2iResultText: content,
      t2iImageUrl: '',
    })
    return
  }
  if (moduleKey === 'video') {
    saveAiPageState('video', {
      sellingPoints: content,
      selectedView: '商家老板',
      selectedStyle: '沉浸式体验',
      selectedGoal: '直接转化预订',
      selectedDuration: '30',
      generateCount: 3,
      generated: true,
      versions: [{ num: 1, label: '策略动作', badge: '待执行', badgeClass: 'bg-bamboo-50 text-bamboo-700', text: content }],
    })
    return
  }
  if (moduleKey === 'reply') {
    saveAiPageState('reply', {
      reviewType: action.label,
      replyStyle: '温暖亲切',
      replyText: content,
    })
    return
  }
  if (moduleKey === 'review') {
    const selectedType = /亲子|家庭/.test(content) ? 'family' : /商务|差旅/.test(content) ? 'biz' : 'couple'
    saveAiPageState('review', {
      selectedType,
      selectedIncentive: null,
      reviews: { [selectedType]: content },
    })
    return
  }
}

async function copyActionContent(action: ActionRow) {
  const content = formatActionContent(action)
  if (content) await copyTextToClipboard(content)
}

function formatActionContent(action: ActionRow) {
  return [
    action.label,
    action.content ? `执行内容：${action.content}` : '',
    action.module ? `调用模块：${action.module}` : '',
  ].filter(Boolean).join('\n')
}

function flash(message: string) {
  toast.value = message
  window.setTimeout(() => { toast.value = '' }, 1600)
}

onMounted(async () => {
  pageAlive = true
  if (!hotelStore.config.name || !hotelStore.roomTypes.length) {
    await hotelStore.loadFromApi().catch(() => {})
  }
  const restored = loadAiPageState<any>(pageStateKey)
  if (!applyStoredState(restored)) {
    router.replace('/strategy')
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
  <div class="min-h-full min-w-0 overflow-x-hidden bg-[#f7f4ee]">
    <transition name="toast">
      <div v-if="toast" class="fixed right-6 top-6 z-50 rounded-lg bg-bamboo-900 px-4 py-2 text-sm text-bamboo-50 shadow-xl">
        {{ toast }}
      </div>
    </transition>
    <div class="sticky top-0 z-20 border-b border-cream-300 bg-white/95 px-6 py-3 backdrop-blur">
      <div class="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-3">
          <button class="icon-button" title="返回修改条件" @click="backToStrategy">
            <ArrowLeft class="h-4 w-4" />
          </button>
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <h1 class="truncate text-base font-semibold text-bamboo-900">{{ planTitle }}</h1>
              <span v-if="displayStatusText" class="rounded-full bg-bamboo-50 px-2.5 py-1 text-[11px] text-bamboo-700">{{ displayStatusText }}</span>
            </div>
            <p class="mt-0.5 truncate text-xs text-warm-500">{{ hotelName }} / {{ cityName }} / {{ optionLabel(periodOptions, form.period) }}</p>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <button class="secondary-button" :disabled="!aiText" @click="copyResult"><Check v-if="copied" class="h-4 w-4" /><Copy v-else class="h-4 w-4" />{{ copied ? '已复制' : '复制全文' }}</button>
          <button class="secondary-button" :disabled="!aiText" @click="exportResult('markdown')"><Download class="h-4 w-4" />导出 Markdown</button>
          <button class="secondary-button" :disabled="!aiText" @click="exportResult('pdf')"><Download class="h-4 w-4" />导出 PDF</button>
          <button class="primary-button" :disabled="generating" @click="regenerate"><Loader2 v-if="generating" class="h-4 w-4 animate-spin" /><RefreshCw v-else class="h-4 w-4" />重新生成</button>
        </div>
      </div>
    </div>

    <main id="strategy-export-content" class="mx-auto w-full max-w-7xl min-w-0 overflow-x-hidden px-5 py-7">
      <template v-if="aiText">
        <section class="strategy-brief">
          <div class="brief-main">
            <div>
              <div class="brief-eyebrow"><Sparkles class="h-4 w-4" /> 给老板和前台看的执行版</div>
              <h2>{{ planTitle }}</h2>
              <p>
                {{ hotelName }} · {{ cityName }} · {{ optionLabel(periodOptions, form.period) }}
                <span>/</span>
                {{ form.targetAudience || 'AI 已结合酒店资料设定客群' }}
              </p>
            </div>
            <div class="brief-tags">
              <span v-for="channel in selectedChannels" :key="channel">{{ channel }}</span>
            </div>
          </div>
          <div class="brief-grid">
            <article>
              <small>本次重点</small>
              <strong>{{ bossSummary.target }}</strong>
            </article>
            <article>
              <small>先做什么</small>
              <strong>{{ bossSummary.firstMove }}</strong>
            </article>
            <article>
              <small>主要渠道</small>
              <strong>{{ bossSummary.channels }}</strong>
            </article>
          </div>
        </section>

        <section class="alignment-status" :class="hasAlignedContent ? 'alignment-ok' : 'alignment-warn'">
          <CheckCircle2 v-if="hasAlignedContent" class="h-4 w-4" />
          <RefreshCw v-else class="h-4 w-4" />
          <span>{{ hasAlignedContent ? '已提取成门店可执行步骤，按顺序做即可。' : '暂未识别到完整结构，下面保留 AI 原文供复核。' }}</span>
        </section>

        <div class="strategy-layout">
          <section class="manager-panel">
            <div class="section-title"><ClipboardList class="h-4 w-4 text-bamboo-700" /><h3>照着做的步骤</h3></div>
            <div class="step-list">
              <article v-for="(step, index) in simpleSteps" :key="step.key" class="step-card">
                <span class="step-number">{{ index + 1 }}</span>
                <div class="step-content">
                  <div class="step-head">
                    <h4>{{ step.title }}</h4>
                    <small>{{ step.time }}</small>
                  </div>
                  <strong>{{ step.focus }}</strong>
                  <p>{{ step.action }}</p>
                  <em>{{ step.owner }}</em>
                </div>
              </article>
            </div>
          </section>

          <aside class="manager-side">
            <section v-if="opportunityHighlights.length" class="side-panel">
              <div class="section-title"><Sparkles class="h-4 w-4 text-amber-600" /><h3>机会提醒</h3></div>
              <article v-for="item in opportunityHighlights" :key="item.key" class="opportunity-card">
                <small>{{ item.signal }}</small>
                <strong>{{ item.action || item.detail }}</strong>
                <p v-if="item.action && item.detail">{{ item.detail }}</p>
              </article>
            </section>

            <section v-if="kpiCards.length" class="side-panel">
              <div class="section-title"><CheckCircle2 class="h-4 w-4 text-bamboo-700" /><h3>验收重点</h3></div>
              <article v-for="item in kpiCards.slice(0, 3)" :key="item.key" class="goal-mini-card">
                <span>{{ item.metric || '目标' }}</span>
                <strong>{{ item.target || '待核实' }}</strong>
                <p>{{ item.method }}</p>
              </article>
            </section>
          </aside>
        </div>

        <section v-if="copyItems.length" class="plan-section compact-section">
          <div class="section-title"><PenLine class="h-4 w-4 text-purple-600" /><h3>{{ copySection?.title || '可直接复制的话术' }}</h3></div>
          <div class="copy-grid">
            <button v-for="(item, index) in copyItems.slice(0, 6)" :key="`${item}-${index}`" class="copy-card" @click="copyText(item)">
              <span class="copy-index">{{ index + 1 }}</span>
              <span class="copy-text">{{ item }}</span>
              <small>复制</small>
            </button>
          </div>
        </section>

        <section class="bottom-actions">
          <div>
            <h3>继续生成内容</h3>
            <p>只带入复用配置，不覆盖目标页面原来的生成结果。</p>
          </div>
          <div class="reuse-grid">
            <button
              v-for="action in reusableActionRows"
              :key="action.key"
              class="reuse-button"
              :title="[action.content, action.module].filter(Boolean).join(' / ')"
              @click="executeAction(action.key)"
            >
              <Send class="h-4 w-4" />
              <span>{{ action.label }}</span>
              <small>{{ action.module }}</small>
            </button>
          </div>
        </section>

        <section class="plan-section detail-section">
          <div class="section-title"><FileText class="h-4 w-4 text-bamboo-700" /><h3>完整方案原文</h3></div>
          <details class="raw-plan-details">
            <summary>展开查看 AI 完整输出</summary>
            <div class="mt-4 rounded-xl border border-cream-200 bg-white px-5 py-4">
              <AiPlanDocument :content="normalizedAiText || aiText" :hidden-section-patterns="['方案概览', '风险核验', '复盘指标', '风险复盘']" />
            </div>
          </details>
        </section>

        <section v-if="false && executionFocusCards.length" class="execution-focus-panel">
          <div class="execution-focus-head">
            <div>
              <h3>执行重点</h3>
              <p>按阶段优先查看本周期要做什么、什么时候做、由哪个渠道承接。</p>
            </div>
            <span>{{ executionFocusCards.length }} 个阶段</span>
          </div>
          <div class="execution-grid">
            <article v-for="card in executionFocusCards" :key="`${card.phase}-${card.time}`" class="execution-card">
              <div class="execution-card-head">
                <h4>{{ card.phase || '执行阶段' }}</h4>
                <span>{{ card.time || '待核实' }}</span>
              </div>
              <strong>{{ card.focus || '待核实重点' }}</strong>
              <p>{{ card.action || '待核实具体动作' }}</p>
              <small>{{ card.owner || '待核实渠道/负责人' }}</small>
            </article>
          </div>
        </section>

        <section v-if="false" class="plan-section">
          <div class="section-title"><ClipboardList class="h-4 w-4 text-indigo-600" /><h3>{{ timelineSection?.title || '执行时间表' }}</h3></div>
          <div v-if="displayTimelineTable" class="mt-4 overflow-x-auto rounded-xl border border-cream-300">
            <table class="strategy-table">
              <thead>
                <tr><th v-for="header in displayTimelineTable.headers" :key="header">{{ header }}</th></tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in displayTimelineTable.rows" :key="rowIndex">
                  <td v-for="(_header, cellIndex) in displayTimelineTable.headers" :key="cellIndex">{{ tableCell(row, cellIndex) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-block">暂未识别到时间表结构，已在完整 AI 方案中保留原文。</div>
        </section>

        <section v-if="false" class="plan-section">
          <div class="section-title"><Grid2X2 class="h-4 w-4 text-rose-600" /><h3>{{ channelSection?.title || '各渠道内容计划' }}</h3></div>
          <div v-if="displayChannelTable" class="mt-4 grid gap-4 md:grid-cols-2">
            <article v-for="(row, rowIndex) in displayChannelTable.rows" :key="rowIndex" class="channel-card">
              <h4>{{ row[0] || `渠道 ${rowIndex + 1}` }}</h4>
              <dl>
                <template v-for="(header, cellIndex) in displayChannelTable.headers.slice(1)" :key="`${rowIndex}-${header}`">
                  <dt>{{ header }}</dt>
                  <dd>{{ tableCell(row, cellIndex + 1) }}</dd>
                </template>
              </dl>
            </article>
          </div>
          <div v-else class="empty-block">暂未识别到渠道计划表，已在完整 AI 方案中保留原文。</div>
        </section>

        <section v-if="false && displayActivityTable" class="plan-section">
          <div class="section-title"><Sparkles class="h-4 w-4 text-amber-600" /><h3>{{ activitySection?.title || '活动与定价承接' }}</h3></div>
          <div class="mt-4 overflow-x-auto rounded-xl border border-cream-300">
            <table class="strategy-table">
              <thead>
                <tr><th v-for="header in displayActivityTable.headers" :key="header">{{ header }}</th></tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in displayActivityTable.rows" :key="rowIndex">
                  <td v-for="(_header, cellIndex) in displayActivityTable.headers" :key="cellIndex">{{ tableCell(row, cellIndex) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-if="false && copyItems.length" class="plan-section">
          <div class="section-title"><PenLine class="h-4 w-4 text-purple-600" /><h3>{{ copySection?.title || '核心文案示例' }}</h3></div>
          <div class="copy-grid">
            <button v-for="(item, index) in copyItems" :key="`${item}-${index}`" class="copy-card" @click="copyText(item)">
              <span class="copy-index">{{ index + 1 }}</span>
              <span class="copy-text">{{ item }}</span>
              <small>复制</small>
            </button>
          </div>
        </section>

        <section v-if="false" v-for="section in extraSections" :key="section.title" class="plan-section">
          <div class="section-title"><FileText class="h-4 w-4 text-bamboo-700" /><h3>{{ section.title }}</h3></div>
          <div class="mt-4 space-y-3">
            <p v-for="paragraph in section.paragraphs" :key="paragraph" class="paragraph-line">{{ paragraph }}</p>
            <ul v-if="section.bullets.length" class="bullet-list">
              <li v-for="bullet in section.bullets" :key="bullet">{{ bullet }}</li>
            </ul>
            <div v-for="(table, tableIndex) in section.tables" :key="tableIndex" class="overflow-x-auto rounded-xl border border-cream-300">
              <table class="strategy-table">
                <thead>
                  <tr><th v-for="header in table.headers" :key="header">{{ header }}</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIndex) in table.rows" :key="rowIndex">
                    <td v-for="(_header, cellIndex) in table.headers" :key="cellIndex">{{ tableCell(row, cellIndex) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section v-if="false" class="plan-section">
          <div class="section-title"><FileText class="h-4 w-4 text-bamboo-700" /><h3>AI 方案对齐预览</h3></div>
          <div class="mt-4 rounded-xl border border-cream-200 bg-white px-5 py-4">
            <AiPlanDocument :content="normalizedAiText || aiText" :hidden-section-patterns="['方案概览', '风险核验', '复盘指标', '风险复盘']" />
          </div>
        </section>

        <section v-if="false" class="bottom-actions">
          <div>
            <h3>继续生成内容</h3>
            <p>点击后会跳到对应页面，并把这条策略动作注入为生成配置。</p>
          </div>
          <div v-if="actionRows.length" class="flex flex-wrap gap-2">
            <button
              v-for="action in actionRows"
              :key="action.key"
              class="secondary-button"
              :title="[action.content, action.module].filter(Boolean).join(' / ')"
              @click="executeAction(action.key)"
            >
              <Send class="h-4 w-4" />
              {{ action.label }}
            </button>
          </div>
          <div v-else class="empty-action">暂未识别到可跳转的生成动作。</div>
        </section>
      </template>

      <div v-else-if="generating" class="flex min-h-[620px] flex-col items-center justify-center text-center">
        <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-bamboo-50 text-bamboo-800"><Loader2 class="h-7 w-7 animate-spin" /></div>
        <h3 class="mt-5 text-base font-semibold text-bamboo-900">正在生成营销策略</h3>
        <p class="mt-2 max-w-md text-sm leading-6 text-warm-500">正在结合酒店资料、周边情报、用户选填条件和实时上下文组织策略。</p>
      </div>
      <div v-else class="flex min-h-[620px] flex-col items-center justify-center text-center">
        <FileText class="h-10 w-10 text-warm-300" />
        <h3 class="mt-4 text-base font-semibold text-bamboo-900">暂无生成结果</h3>
        <p class="mt-2 text-sm text-warm-500">可以重新生成，或返回修改策略条件。</p>
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
.strategy-brief {
  margin-bottom: 1rem;
  overflow: hidden;
  border: 1px solid #d9e7ce;
  border-radius: 1rem;
  background: linear-gradient(135deg, #fffdfa 0%, #f2f8ee 100%);
  box-shadow: 0 12px 28px rgb(54 79 50 / 0.07);
}
.brief-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid #e5d8c5;
  padding: 1.15rem 1.25rem 0.95rem;
}
.brief-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: #b27418;
  font-size: 0.72rem;
  font-weight: 900;
}
.brief-main h2 {
  margin-top: 0.4rem;
  color: #123522;
  font-size: 1.35rem;
  font-weight: 900;
  line-height: 1.35;
}
.brief-main p {
  margin-top: 0.35rem;
  color: #715f50;
  font-size: 0.78rem;
  line-height: 1.6;
}
.brief-main p span { margin: 0 0.45rem; color: #c6a986; }
.brief-tags {
  display: flex;
  max-width: 38%;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.45rem;
}
.brief-tags span {
  border-radius: 999px;
  background: white;
  padding: 0.32rem 0.65rem;
  color: #315b37;
  font-size: 0.7rem;
  font-weight: 800;
  box-shadow: inset 0 0 0 1px #d9e7ce;
}
.brief-grid {
  display: grid;
  grid-template-columns: 1.05fr 1.4fr 0.85fr;
  gap: 0.75rem;
  padding: 0.9rem 1.25rem 1.1rem;
}
.brief-grid article {
  min-width: 0;
  border-radius: 0.85rem;
  background: rgb(255 255 255 / 0.82);
  padding: 0.85rem 0.95rem;
}
.brief-grid small,
.opportunity-card small,
.goal-mini-card span {
  display: block;
  color: #b27418;
  font-size: 0.68rem;
  font-weight: 900;
}
.brief-grid strong {
  margin-top: 0.35rem;
  display: block;
  color: #163723;
  font-size: 0.92rem;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.goal-panel {
  margin-bottom: 1.25rem;
  border: 1px solid #eadfce;
  border-radius: 0.95rem;
  background: #fffdfa;
  padding: 1rem;
}
.goal-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.9rem;
}
.goal-panel-head h3 {
  color: #203f2b;
  font-size: 1rem;
  font-weight: 800;
}
.goal-panel-head p {
  margin-top: 0.2rem;
  color: #8b7460;
  font-size: 0.75rem;
}
.goal-panel-head > span {
  flex-shrink: 0;
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.25rem 0.65rem;
  color: #315b37;
  font-size: 0.7rem;
  font-weight: 800;
}
.goal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1rem;
}
.goal-card {
  min-width: 0;
  border: 1px solid #eadfce;
  border-radius: 0.9rem;
  background: white;
  padding: 1rem;
  box-shadow: 0 8px 18px rgb(54 79 50 / 0.04);
}
.goal-card-top small {
  display: block;
  color: #c08825;
  font-size: 0.68rem;
  font-weight: 800;
}
.goal-card-top strong {
  margin-top: 0.25rem;
  display: block;
  color: #203f2b;
  font-size: 0.95rem;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.goal-card-value {
  margin-top: 0.8rem;
  border-radius: 0.8rem;
  background: #f4f8f0;
  padding: 0.8rem 0.9rem;
  color: #0f5a2a;
  font-size: 1.3rem;
  font-weight: 900;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
.goal-card dl {
  margin-top: 0.85rem;
  display: grid;
  gap: 0.65rem;
}
.goal-card dt {
  color: #9f7b2d;
  font-size: 0.68rem;
  font-weight: 800;
}
.goal-card dd {
  margin-top: 0.16rem;
  color: #5f5143;
  font-size: 0.76rem;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.alignment-status {
  margin-bottom: 1rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-radius: 0.75rem;
  padding: 0.8rem 1rem;
  font-size: 0.78rem;
  font-weight: 600;
  line-height: 1.6;
}
.strategy-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 1rem;
  align-items: start;
}
.manager-panel,
.side-panel {
  min-width: 0;
  border: 1px solid #eadfce;
  border-radius: 0.95rem;
  background: white;
  padding: 1.05rem;
  box-shadow: 0 8px 20px rgb(54 79 50 / 0.05);
}
.manager-side {
  display: grid;
  gap: 1rem;
}
.step-list {
  margin-top: 0.95rem;
  display: grid;
  gap: 0.75rem;
}
.step-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.75rem;
  border: 1px solid #eadfce;
  border-radius: 0.9rem;
  background: #fffdfa;
  padding: 0.85rem;
}
.step-number {
  display: inline-flex;
  height: 2rem;
  width: 2rem;
  align-items: center;
  justify-content: center;
  border-radius: 0.7rem;
  background: #234d32;
  color: white;
  font-size: 0.82rem;
  font-weight: 900;
}
.step-content { min-width: 0; }
.step-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.6rem;
}
.step-head h4 {
  color: #123522;
  font-size: 0.92rem;
  font-weight: 900;
}
.step-head small,
.step-content em {
  flex-shrink: 0;
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.18rem 0.5rem;
  color: #315b37;
  font-size: 0.66rem;
  font-style: normal;
  font-weight: 800;
}
.step-content strong {
  margin-top: 0.45rem;
  display: block;
  color: #0f5a2a;
  font-size: 0.9rem;
  line-height: 1.55;
}
.step-content p {
  margin-top: 0.35rem;
  color: #5f5143;
  font-size: 0.8rem;
  line-height: 1.75;
  white-space: pre-line;
}
.step-content em { margin-top: 0.55rem; display: inline-flex; }
.opportunity-card,
.goal-mini-card {
  margin-top: 0.7rem;
  border-radius: 0.85rem;
  background: #fffdfa;
  padding: 0.8rem;
  box-shadow: inset 0 0 0 1px #f0e5d4;
}
.opportunity-card strong,
.goal-mini-card strong {
  margin-top: 0.28rem;
  display: block;
  color: #123522;
  font-size: 0.84rem;
  line-height: 1.55;
}
.opportunity-card p,
.goal-mini-card p {
  margin-top: 0.3rem;
  color: #786653;
  font-size: 0.73rem;
  line-height: 1.6;
}
.compact-section {
  padding: 1.05rem;
}
.detail-section {
  background: #fffdfa;
}
.raw-plan-details summary {
  margin-top: 0.85rem;
  cursor: pointer;
  border-radius: 0.75rem;
  background: white;
  padding: 0.75rem 0.9rem;
  color: #315b37;
  font-size: 0.78rem;
  font-weight: 800;
}
.alignment-ok {
  border: 1px solid #cfe3c7;
  background: #eef7ea;
  color: #315b37;
}
.alignment-warn {
  border: 1px solid #eadfce;
  background: #faf7f1;
  color: #8b7460;
}
.execution-focus-panel {
  margin-bottom: 1.25rem;
  border: 1px solid #eadfce;
  border-radius: 0.95rem;
  background: #fffdfa;
  padding: 1rem;
}
.execution-focus-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.9rem;
}
.execution-focus-head h3 {
  color: #203f2b;
  font-size: 1rem;
  font-weight: 800;
}
.execution-focus-head p {
  margin-top: 0.2rem;
  color: #8b7460;
  font-size: 0.75rem;
}
.execution-focus-head > span {
  flex-shrink: 0;
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.25rem 0.65rem;
  color: #315b37;
  font-size: 0.7rem;
  font-weight: 800;
}
.execution-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1rem;
}
.execution-card {
  min-width: 0;
  border: 1px solid #eadfce;
  border-radius: 0.9rem;
  background: white;
  padding: 1rem;
  box-shadow: 0 8px 18px rgb(54 79 50 / 0.04);
}
.execution-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.7rem;
}
.execution-card-head h4 {
  color: #203f2b;
  font-size: 0.92rem;
  font-weight: 800;
  line-height: 1.45;
}
.execution-card-head span {
  flex-shrink: 0;
  border-radius: 999px;
  background: #f5f0e8;
  padding: 0.22rem 0.55rem;
  color: #6c5b4b;
  font-size: 0.68rem;
  font-weight: 900;
}
.execution-card strong {
  display: block;
  margin-top: 0.85rem;
  color: #0f5a2a;
  font-size: 1rem;
  line-height: 1.5;
}
.execution-card p {
  margin-top: 0.55rem;
  color: #66594b;
  font-size: 0.8rem;
  line-height: 1.75;
  white-space: pre-line;
}
.execution-card small {
  margin-top: 0.7rem;
  display: inline-flex;
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.25rem 0.55rem;
  color: #315b37;
  font-size: 0.68rem;
  font-weight: 800;
}
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
.strategy-table {
  width: 100%;
  min-width: 760px;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 0.76rem;
}
.strategy-table th {
  background: #f5f0e8;
  color: #315b37;
  font-weight: 700;
  text-align: left;
}
.strategy-table th,
.strategy-table td {
  border-bottom: 1px solid #eadfce;
  padding: 0.75rem;
  vertical-align: top;
  line-height: 1.7;
  text-align: left;
  overflow-wrap: anywhere;
  word-break: break-word;
  white-space: pre-line;
}
.channel-card {
  min-width: 0;
  border: 1px solid #eadfce;
  border-radius: 0.85rem;
  padding: 1rem;
  background: #fffdfa;
}
.channel-card h4 { font-size: 0.9rem; font-weight: 800; color: #203f2b; }
.channel-card dl { margin-top: 0.85rem; display: grid; gap: 0.55rem; }
.channel-card dt { font-size: 0.7rem; font-weight: 700; color: #c08825; }
.channel-card dd { font-size: 0.76rem; line-height: 1.65; color: #5f5143; }
.copy-grid {
  margin-top: 1rem;
  display: grid;
  gap: 0.75rem;
}
.copy-card {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: flex-start;
  gap: 0.75rem;
  border: 1px solid #eadfce;
  border-radius: 0.85rem;
  background: #fffdfa;
  padding: 0.9rem 1rem;
  text-align: left;
  color: #4f4338;
  transition: 150ms ease;
}
.copy-card:hover {
  border-color: #8cac77;
  background: #f7faf4;
}
.copy-index {
  display: inline-flex;
  height: 1.35rem;
  min-width: 1.35rem;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #eef7ea;
  color: #315b37;
  font-size: 0.68rem;
  font-weight: 900;
}
.copy-text {
  min-width: 0;
  font-size: 0.84rem;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.copy-card small {
  flex-shrink: 0;
  border-radius: 999px;
  background: white;
  padding: 0.18rem 0.5rem;
  color: #8b7460;
  font-size: 0.68rem;
  font-weight: 800;
}
.paragraph-line {
  overflow-wrap: anywhere;
  white-space: pre-line;
  border-radius: 0.6rem;
  background: #faf7f1;
  padding: 0.7rem 0.85rem;
  color: #5f5143;
  font-size: 0.8rem;
  line-height: 1.75;
}
.bullet-list {
  display: grid;
  gap: 0.5rem;
  color: #5f5143;
  font-size: 0.8rem;
  line-height: 1.7;
}
.bullet-list li {
  border-radius: 0.6rem;
  background: #faf7f1;
  padding: 0.65rem 0.85rem;
}
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
  display: grid;
  grid-template-columns: minmax(190px, 0.55fr) minmax(0, 1fr);
  align-items: center;
  gap: 1rem;
  border: 1px solid #cfe3c7;
  border-radius: 0.95rem;
  background: #eef7ea;
  padding: 0.95rem;
}
.bottom-actions h3 { font-size: 0.95rem; font-weight: 700; color: #203f2b; }
.bottom-actions p { margin-top: 0.2rem; font-size: 0.75rem; color: #6f8067; }
.reuse-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.65rem;
}
.reuse-button {
  display: grid;
  min-width: 0;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  column-gap: 0.45rem;
  border: 1px solid #d9e7ce;
  border-radius: 0.8rem;
  background: white;
  padding: 0.7rem 0.75rem;
  text-align: left;
  color: #234d32;
  transition: 150ms ease;
}
.reuse-button:hover {
  border-color: #8cac77;
  background: #f7faf4;
}
.reuse-button span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.8rem;
  font-weight: 900;
}
.reuse-button small {
  grid-column: 2;
  color: #8b7460;
  font-size: 0.66rem;
  font-weight: 700;
}
@media (max-width: 1100px) {
  .strategy-layout,
  .bottom-actions {
    grid-template-columns: 1fr;
  }
  .manager-side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .reuse-grid,
  .brief-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .brief-main {
    flex-direction: column;
  }
  .brief-tags {
    max-width: none;
    justify-content: flex-start;
  }
  .brief-grid,
  .manager-side,
  .reuse-grid {
    grid-template-columns: 1fr;
  }
}
</style>
