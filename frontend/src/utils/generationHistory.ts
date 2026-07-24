import type { Router } from 'vue-router'
import { extractArticleDisplayText, extractImageUrl, normalizeImageUrl, toPlainTextWithoutImageMarkdown } from '@/utils/aiContentRender'
import { saveAiPageState } from '@/utils/aiPageState'

export type HistoryItem = {
  id: number
  moduleKey: string
  title: string
  prompt?: string
  inputParams?: string | Record<string, any>
  outputContent?: string
  outputAssets?: string | string[] | Record<string, any> | Array<string | Record<string, any>>
  status: string
  errorMsg?: string
  costCredits?: number
  createdAt?: string
  completedAt?: string
}

export const generationModules = [
  { key: '', label: '全部', route: '/history' },
  { key: 'xhs', label: '小红书', route: '/xhs' },
  { key: 'wechat', label: '朋友圈', route: '/wechat' },
  { key: 'article', label: '公众号', route: '/article' },
  { key: 'poster', label: '海报', route: '/poster' },
  { key: 'video', label: '视频', route: '/video' },
  { key: 'pricing', label: '定价', route: '/pricing' },
  { key: 'strategy', label: '营销策略', route: '/strategy' },
  { key: 'brain', label: 'AI 店长', route: '/brain' },
  { key: 'occupancy_image', label: '房态导入', route: '/setup/occupancy-history' },
  { key: 'review', label: '好评引导', route: '/review' },
  { key: 'reply', label: '回评话术', route: '/reply' },
]

export function moduleLabel(moduleKey: string) {
  return generationModules.find(item => item.key === moduleKey)?.label || moduleKey
}

export function moduleRoute(moduleKey: string) {
  return generationModules.find(item => item.key === moduleKey)?.route || '/brain'
}

export function moduleHistoryRoute(moduleKey: string) {
  return moduleKey ? `/history/${moduleKey}` : '/history'
}

export function moduleDetailRoute(item: HistoryItem) {
  return `/history/${item.moduleKey}/${item.id}`
}

export function formatHistoryTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

export function rawParams(item?: HistoryItem | null): Record<string, any> {
  if (!item?.inputParams) return {}
  if (typeof item.inputParams === 'object') return item.inputParams as any
  try {
    return JSON.parse(item.inputParams)
  } catch {
    return {}
  }
}

export function effectiveParams(item?: HistoryItem | null) {
  const params = rawParams(item)
  return params.selectedParams && typeof params.selectedParams === 'object' ? params.selectedParams : params
}

export function parseOutputAssets(value?: HistoryItem['outputAssets']) {
  const parsed = normalizeAssetValue(value)
  if (!parsed) return []
  return Array.isArray(parsed) ? parsed.map(extractImageUrl).filter(Boolean) : [extractImageUrl(JSON.stringify(parsed))].filter(Boolean)
}

export function imageUrl(item?: HistoryItem | null) {
  const structured = parseOutputAssets(item?.outputAssets)
  const contentImage = extractImageUrl(item?.outputContent)
  return pickPreferredImageUrl([...structured, contentImage].filter(Boolean))
}

export function resultText(item?: HistoryItem | null) {
  if (item?.moduleKey === 'article') {
    return extractArticleDisplayText(item.outputContent || item.errorMsg || '暂无内容')
  }
  return toPlainTextWithoutImageMarkdown(item?.outputContent || item?.errorMsg || '暂无内容')
}

export function isPlanOutput(item?: HistoryItem | null) {
  return item?.moduleKey === 'pricing' || item?.moduleKey === 'strategy'
}

export function promptText(item?: HistoryItem | null) {
  const params = effectiveParams(item)
  if (item?.moduleKey === 'xhs') {
    return xhsPromptText(item, params)
  }
  const raw = item?.prompt
    || params.message
    || params.userQuestion
    || params.theme
    || params.customTopic
    || params.sellingPoints
    || params.content
    || params.title
    || ''
  return cleanValue(raw)
}

export function historyTitle(item: HistoryItem) {
  if (item.moduleKey === 'xhs') {
    const current = cleanValue(item.title)
    if (current && !isRawXhsTopicText(current)) return current
    return xhsOutputTitle(item)
      || cleanValue(effectiveParams(item).customTopic)
      || xhsTopicTitle(effectiveParams(item))
      || current
      || `${moduleLabel(item.moduleKey)}生成`
  }
  return item.title || promptText(item) || `${moduleLabel(item.moduleKey)}生成`
}

const XHS_TOPIC_LABELS: Record<string, string> = {
  rain: '雨天竹林',
  festival: '节日氛围',
  couple: '情侣度假',
  family: '亲子出行',
  hotspring: '私汤温泉',
  breakfast: '有机早餐',
  vlog: '日常 vlog',
  escape: '周末逃离',
}

function xhsPromptText(item: HistoryItem, params: Record<string, any>) {
  const prompt = cleanValue(item.prompt)
  if (prompt && !isRawXhsTopicText(prompt)) return prompt
  return cleanValue(params.customTopic)
    || cleanValue(params.note)
    || xhsTopicTitle(params)
    || xhsOutputTitle(item)
    || prompt
}

function xhsTopicTitle(params: Record<string, any>) {
  const topics = normalizeTopics(params.topics || params.theme)
    .map(item => XHS_TOPIC_LABELS[item] || item)
    .filter(Boolean)
  return topics.length ? `小红书图文：${topics.join(' / ')}` : ''
}

function xhsOutputTitle(item?: HistoryItem | null) {
  const parsed = parseJsonLike(item?.outputContent)
  if (!parsed || typeof parsed !== 'object') return ''
  const title = cleanValue((parsed as any).title)
  if (title) return title
  const titles = (parsed as any).titles
  return Array.isArray(titles) ? cleanValue(titles[0]) : ''
}

function parseJsonLike(value: any) {
  const text = String(value || '').trim()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    const match = text.match(/\{[\s\S]*\}/)
    if (!match) return null
    try {
      return JSON.parse(match[0])
    } catch {
      return null
    }
  }
}

function isRawXhsTopicText(value: string) {
  const topics = normalizeTopics(value)
  return Boolean(topics.length && topics.every(item => Boolean(XHS_TOPIC_LABELS[item])))
}

export function friendlyConfig(item?: HistoryItem | null) {
  if (!item) return []
  const params = effectiveParams(item)
  return configFields(item.moduleKey)
    .map(field => ({ key: field.key, label: field.label, value: formatConfigValue(item.moduleKey, field.key, readPath(params, field.key)) }))
    .filter(row => row.value)
}

function configFields(moduleKey: string) {
  const common = [
    { key: 'theme', label: '主题' },
    { key: 'message', label: '提示词/问题' },
    { key: 'style', label: '风格' },
    { key: 'tone', label: '语气' },
    { key: 'imageSize', label: '图片比例' },
    { key: 'withImage', label: '是否配图' },
  ]
  const fields: Record<string, Array<{ key: string; label: string }>> = {
    xhs: [
      { key: 'topics', label: '内容主题' },
      { key: 'tone', label: '内容方向' },
      { key: 'style', label: '写作风格' },
      { key: 'imageSize', label: '图片比例' },
      { key: 'imageCount', label: '图片数量' },
      { key: 'customTopic', label: '自定义主题' },
      { key: 'note', label: '额外备注' },
    ],
    wechat: [
      { key: 'slots', label: '发布时段' },
      { key: 'style', label: '内容风格' },
      { key: 'length', label: '文案长度' },
      { key: 'note', label: '额外备注' },
    ],
    article: [
      { key: 'title', label: '文章标题' },
      { key: 'style', label: '排版预设' },
      { key: 'length', label: '文章长度' },
      { key: 'imageCount', label: '配图数量' },
    ],
    poster: [
      { key: 'mode', label: '创作模式' },
      { key: 'theme', label: '海报主题' },
      { key: 'content', label: '海报内容' },
      { key: 'style', label: '海报风格' },
      { key: 'imageSize', label: '图片比例' },
    ],
    video: [
      { key: 'sellingPoints', label: '卖点' },
      { key: 'view', label: '创作视角' },
      { key: 'style', label: '文案风格' },
      { key: 'goal', label: '营销目的' },
      { key: 'duration', label: '视频时长' },
      { key: 'count', label: '生成条数' },
    ],
    pricing: [
      { key: 'dateRange', label: '定价周期' },
      { key: 'pricingGoalLabel', label: '定价目标' },
      { key: 'demandSignalLabel', label: '市场需求信号' },
      { key: 'bookingWindowLabel', label: '预订窗口' },
      { key: 'eventFactorLabel', label: '日期影响因素' },
      { key: 'competitorPriceRange', label: '竞品价格观察' },
      { key: 'targetChannels', label: '重点渠道' },
    ],
    strategy: [
      { key: 'objectiveLabel', label: '策略目标' },
      { key: 'periodLabel', label: '执行周期' },
      { key: 'occasionLabel', label: '经营场景' },
      { key: 'targetAudience', label: '目标客群' },
      { key: 'channelLabels', label: '执行渠道' },
    ],
    brain: [{ key: 'message', label: '经营问题' }],
    review: [{ key: 'guestType', label: '客群类型' }],
    reply: [
      { key: 'reviewText', label: '客户评价内容' },
      { key: 'reviewType', label: '评价类型' },
      { key: 'style', label: '回复风格' },
    ],
  }
  return fields[moduleKey] || common
}

function readPath(source: Record<string, any>, key: string) {
  return key.split('.').reduce<any>((value, part) => value?.[part], source)
}

export function cleanValue(value: any): string {
  if (value === undefined || value === null || value === '') return ''
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) return value.map(cleanValue).filter(Boolean).join('、')
  if (typeof value === 'object') return ''
  return String(value).trim()
}

function normalizeTopics(value: any) {
  if (Array.isArray(value)) return value.map(String)
  return String(value || '')
    .split(/[,，、\s]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function formatConfigValue(moduleKey: string, key: string, value: any) {
  if (moduleKey !== 'xhs') return cleanValue(value)

  if (key === 'topics') {
    return normalizeTopics(value).map(item => XHS_TOPIC_LABELS[item] || item).join(' / ')
    const labels: Record<string, string> = {
      rain: '雨天竹林',
      festival: '节日氛围',
      couple: '情侣度假',
      family: '亲子出行',
      hotspring: '私汤温泉',
      breakfast: '有机早餐',
      vlog: '日常 vlog',
      escape: '周末逃离',
    }
    return normalizeTopics(value).map(item => labels[item] || item).join(' / ')
  }

  if (key === 'tone') {
    const labels: Record<string, string> = {
      emotional: '情绪种草',
      guide: '攻略干货',
      deal: '限时特惠',
      review: '探店测评',
    }
    return labels[String(value || '')] || cleanValue(value)
  }

  if (key === 'style') {
    const labels: Record<string, string> = {
      warm: '治愈温暖',
      young: '活泼元气',
      luxury: '轻奢精致',
      story: '故事叙事',
    }
    return labels[String(value || '')] || cleanValue(value)
  }

  if (key === 'imageSize') {
    const labels: Record<string, string> = {
      '1:1': '1:1 方形',
      '4:3': '4:3 横图',
      '3:4': '3:4 竖图',
      '16:9': '16:9 宽屏',
    }
    return labels[String(value || '')] || cleanValue(value)
  }

  if (key === 'imageCount') {
    return value ? `${value} 张` : ''
  }

  return cleanValue(value)
}

function normalizeAssetValue(value: HistoryItem['outputAssets']) {
  if (!value) return null
  if (Array.isArray(value)) return value
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function pickPreferredImageUrl(urls: string[]) {
  const ranked = urls
    .map(url => normalizeImageUrl(url))
    .filter(Boolean)
    .filter(url => !isTemporaryUnsignedImageUrl(url))
    .sort((left, right) => scoreImageUrl(right) - scoreImageUrl(left))
  return ranked[0] || ''
}

function isTemporaryUnsignedImageUrl(url: string) {
  return isSiliconFlowTemporaryImageUrl(url) && !isCompleteSiliconFlowSignedUrl(url)
}

function scoreImageUrl(url: string) {
  let score = 0
  if (/[?&]X-Amz-Signature=/i.test(url)) score += 100
  if (/[?&]X-Amz-Security-Token=/i.test(url)) score += 20
  if (/temporary\/outputs\//i.test(url)) score -= 10
  if (/^https?:\/\//i.test(url)) score += 5
  return score
}

function isSiliconFlowTemporaryImageUrl(url: string) {
  return /s3\.siliconflow\.cn\/(?:temporary|t)\/outputs\//i.test(url)
}

function isCompleteSiliconFlowSignedUrl(url: string) {
  if (!isSiliconFlowTemporaryImageUrl(url)) return true
  return /[?&]X-Amz-Signature=[0-9a-f]{32,}/i.test(url)
    && /[?&]X-Amz-Security-Token=[^&#\s]+/i.test(url)
    && /[?&]X-Amz-Credential=[^&#\s]*(?:aws4_request|aws4%5[fF]request)/i.test(url)
}

export function reuseGeneration(item: HistoryItem, router: Router) {
  saveReuseState(item)
  router.push({ path: moduleRoute(item.moduleKey) })
}

function saveReuseState(item: HistoryItem) {
  const params = effectiveParams(item)
  const message = promptText(item)

  if (item.moduleKey === 'xhs') {
    saveAiPageState('xhs', {
      selectedTopics: normalizeTopics(params.topics || params.theme),
      selectedTone: params.tone || 'emotional',
      style: params.style || 'warm',
      note: params.note || '',
      withImage: params.withImage ?? true,
      imageSize: params.imageSize || '3:4',
      imageCount: Number(params.imageCount || 6),
      customTopic: params.customTopic || '',
      generated: false,
      title: '',
      body: '',
      tags: [],
      xhsImageUrl: '',
      xhsLastImageUrl: imageUrl(item) || '',
      xhsGenerationId: item.id,
    })
    return
  }

  if (item.moduleKey === 'wechat') {
    const slots = Array.isArray(params.slots) ? params.slots : []
    const image = imageUrl(item) || extractImageUrl(JSON.stringify(item))
    saveAiPageState('wechat', {
      slots: { morning: !slots.length || slots.includes('morning'), noon: !slots.length || slots.includes('noon'), evening: !slots.length || slots.includes('evening') },
      style: params.style || 'auto',
      length: params.length || 'mid',
      withImage: params.withImage ?? true,
      imageSize: params.imageSize || '1:1',
      note: params.note || '',
      generated: false,
      outputs: image ? [
        { id: 'morning', label: '早间', time: '07:30-08:00', typeLabel: '种草引流', typeClass: 'bg-amber-50 text-amber-700', content: '', imageUrl: image, lastImageUrl: image },
        { id: 'noon', label: '午间', time: '12:00-12:30', typeLabel: '互动留客', typeClass: 'bg-blue-50 text-blue-600', content: '', imageUrl: image, lastImageUrl: image },
        { id: 'evening', label: '晚间', time: '20:00-21:00', typeLabel: '转化收口', typeClass: 'bg-purple-50 text-purple-700', content: '', imageUrl: image, lastImageUrl: image },
      ] : undefined,
    })
    return
  }

  if (item.moduleKey === 'pricing') {
    saveAiPageState('pricing', {
      form: {
        pricingPeriod: params.pricingPeriod || 'next7',
        customStartDate: params.customStartDate || '',
        customEndDate: params.customEndDate || '',
        pricingGoal: params.pricingGoal || 'balance',
        demandSignal: params.demandSignal || 'normal',
        bookingWindow: params.bookingWindow || '1-3',
        eventFactor: params.eventFactor || 'normal',
        competitorPriceRange: params.competitorPriceRange || '',
        currentPriceNotes: params.currentPriceNotes || '',
        priceFloor: params.priceFloor || '',
        maxDiscountPercent: Number(params.maxDiscountPercent || 10),
        targetChannels: Array.isArray(params.targetChannels) ? params.targetChannels : ['ota'],
        promotionAllowed: params.promotionAllowed ?? true,
        packagePreference: params.packagePreference || 'room-only',
        riskLevel: params.riskLevel || 'balanced',
        constraints: params.constraints || '',
      },
      showAdvanced: true,
      generating: false,
      statusText: '',
      aiText: '',
    })
    return
  }

  if (item.moduleKey === 'strategy') {
    saveAiPageState('strategy', {
      form: {
        objective: params.objective || 'conversion',
        period: params.period || '14d',
        occasion: params.occasion || 'normal',
        targetAudience: params.targetAudience || '',
        channels: Array.isArray(params.channels) ? params.channels : ['xhs', 'wechat', 'ota'],
        budgetLevel: params.budgetLevel || 'low',
        executionCapacity: params.executionCapacity || 'small-team',
        outputDepth: params.outputDepth || 'action-plan',
        marketSignals: params.marketSignals || '',
        competitorObservations: params.competitorObservations || '',
        availableOffers: params.availableOffers || '',
        constraints: params.constraints || '',
        evidenceRequirement: params.evidenceRequirement ?? true,
      },
      runId: '',
      generating: false,
      aiText: '',
    })
    return
  }

  if (item.moduleKey === 'article') {
    saveAiPageState('article', { articleTitle: params.title || params.topic || '', selectedStyle: params.style || 'teal_tech', selectedLength: params.length || 'medium', generated: false })
    return
  }

  if (item.moduleKey === 'poster') {
    saveAiPageState('poster', { mode: params.mode || 'text2img', t2iTheme: params.theme || '', t2iContent: params.content || params.message || '', t2iStyle: params.style || 'chinese', t2iGenerated: false })
    return
  }

  if (item.moduleKey === 'video') {
    saveAiPageState('video', { sellingPoints: params.sellingPoints || message, selectedView: params.view || '商家老板', selectedStyle: params.style || '沉浸式体验', selectedGoal: params.goal || '引流涨粉', selectedDuration: params.duration || '30', generateCount: Number(params.count || 3), generated: false, versions: [] })
    return
  }

  if (item.moduleKey === 'reply') saveAiPageState('reply', {
    reviewType: params.reviewType || '五星好评·夸环境',
    replyStyle: params.style || '温暖亲切',
    reviewText: params.reviewText || '',
    replyText: '',
  })
  if (item.moduleKey === 'review') saveAiPageState('review', { selectedType: params.guestType || null, selectedIncentive: null, reviews: {} })
  if (item.moduleKey === 'brain') saveAiPageState('brain', { input: message, messages: [] })
}
