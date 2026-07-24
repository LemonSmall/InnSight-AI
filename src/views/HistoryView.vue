<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Clock, Copy, Download, ExternalLink, FileText, Loader2, RotateCcw } from 'lucide-vue-next'
import { getGenerationHistory } from '@/api/history'
import { extractImageUrl, toPlainTextWithoutImageMarkdown } from '@/utils/aiContentRender'
import { saveAiPageState } from '@/utils/aiPageState'
import AiGenerationPreview from '@/components/ai/AiGenerationPreview.vue'
import { useHotelStore } from '@/stores/hotel'
import type { OccupancyImportData } from '@/utils/occupancyImport'

type HistoryItem = {
  id: number
  moduleKey: string
  title: string
  prompt?: string
  inputParams?: string | Record<string, any>
  outputContent?: string
  outputAssets?: string | string[]
  status: string
  errorMsg?: string
  costCredits?: number
  createdAt?: string
  completedAt?: string
}

const router = useRouter()
const store = useHotelStore()
const loading = ref(false)
const activeModule = ref('')
const histories = ref<HistoryItem[]>([])
const selected = ref<HistoryItem | null>(null)
const toast = ref('')

const modules = [
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

const occupancyHistoryItems = computed(() => {
  if (activeModule.value && activeModule.value !== 'occupancy_image') return []
  return store.occupancyHistory.map((item, index) => occupancyItemToHistory(item, index))
})

const filtered = computed(() => {
  const rows = [...occupancyHistoryItems.value, ...histories.value]
  return rows.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
})

let refreshTimer: ReturnType<typeof window.setInterval> | null = null

onMounted(async () => {
  await load()
  refreshTimer = window.setInterval(() => {
    if (histories.value.some(item => item.status === 'processing')) load(false)
  }, 3000)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

async function load(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const { data } = await getGenerationHistory(activeModule.value || undefined, 100)
    const selectedId = selected.value?.id
    histories.value = data.data || []
    selected.value = filtered.value.find(item => item.id === selectedId) || filtered.value[0] || null
  } catch {
    flash('历史记录加载失败')
  } finally {
    if (showLoading) loading.value = false
  }
}

function occupancyItemToHistory(item: OccupancyImportData, index: number): HistoryItem {
  const source = sourceFileLabel(item)
  const importedAt = item.importedAt || new Date().toISOString()
  const timestamp = Date.parse(importedAt)
  return {
    id: Number.isFinite(timestamp) ? -timestamp - index : -900000 - index,
    moduleKey: 'occupancy_image',
    title: `房态导入：${source}`,
    prompt: '上传历史房态表并解析为可导入房态数据',
    inputParams: {
      sourceFileName: source,
      sourceType: source.match(/\.(png|jpe?g|webp)$/i) ? '图片上传' : '表格上传',
      recordCount: item.records.length,
    },
    outputContent: JSON.stringify({ records: item.records }, null, 2),
    status: 'success',
    costCredits: 0,
    createdAt: importedAt,
    completedAt: importedAt,
  }
}

function sourceFileLabel(item: OccupancyImportData) {
  const names = item.sourceFileNames?.length ? item.sourceFileNames : [item.sourceFileName]
  return names.filter(Boolean).join('、') || '未命名房态表'
}

async function switchModule(moduleKey: string) {
  activeModule.value = moduleKey
  await load()
}

function moduleLabel(moduleKey: string) {
  return modules.find(item => item.key === moduleKey)?.label || moduleKey
}

function moduleRoute(moduleKey: string) {
  return modules.find(item => item.key === moduleKey)?.route || '/brain'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function rawParams(item?: HistoryItem | null): Record<string, any> {
  if (!item?.inputParams) return {}
  if (typeof item.inputParams === 'object') return item.inputParams as any
  try {
    return JSON.parse(item.inputParams)
  } catch {
    return {}
  }
}

function imageUrl(item?: HistoryItem | null) {
  const structured = parseOutputAssets(item?.outputAssets)
  return structured[0] || extractImageUrl(item?.outputContent)
}

function parseOutputAssets(value?: string | string[]) {
  if (Array.isArray(value)) return value.filter(item => typeof item === 'string')
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter(item => typeof item === 'string') : []
  } catch {
    return []
  }
}

function resultText(item?: HistoryItem | null) {
  return toPlainTextWithoutImageMarkdown(item?.outputContent || item?.errorMsg || '暂无内容')
}

function wechatHistorySlots(item?: HistoryItem | null) {
  const data = parseHistoryJson(item?.outputContent)
  if (!data) return []
  const suggestions = normalizeHistoryList(data.imageSuggestions || data.image_suggestions || data.imageSuggestion || data.image_suggestion)
  const schedules = normalizeHistoryList(data.publishSchedule || data.publish_schedule || data.schedule || data.schedules)
  return [
    { key: 'morning', label: '早间朋友圈', time: '08:00' },
    { key: 'noon', label: '午间朋友圈', time: '12:00' },
    { key: 'evening', label: '晚间朋友圈', time: '20:30' },
  ].map((slot, index) => ({
    ...slot,
    content: wechatSlotText(data, slot.key),
    suggestion: suggestions[index] || (slot.key === 'evening' ? suggestions[0] : '') || '',
    schedule: findWechatSchedule(schedules, slot.key, index),
  })).filter(slot => slot.content)
}

function parseHistoryJson(value?: string) {
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

function normalizeHistoryList(value: any) {
  if (Array.isArray(value)) return value.map(item => String(item || '').trim()).filter(Boolean)
  if (typeof value === 'string') return value.split(/\n+/).map(item => item.trim()).filter(Boolean)
  return []
}

function wechatSlotText(data: any, key: string) {
  const value = data?.[key]
  if (typeof value === 'string') return value.trim()
  if (value && typeof value === 'object') return String(value.content || value.text || value.body || '').trim()
  return ''
}

function findWechatSchedule(list: string[], key: string, index: number) {
  const patterns: Record<string, RegExp> = {
    morning: /morning|早间|早上|上午|10:00|08:00/i,
    noon: /noon|午间|中午|12:00/i,
    evening: /evening|晚间|晚上|20:30|20:00/i,
  }
  return list.find(item => patterns[key].test(item)) || list[index] || ''
}

function isPlanOutput(item?: HistoryItem | null) {
  return item?.moduleKey === 'pricing' || item?.moduleKey === 'strategy'
}

function effectiveParams(item?: HistoryItem | null) {
  const params = rawParams(item)
  return params.selectedParams && typeof params.selectedParams === 'object' ? params.selectedParams : params
}

function promptText(item?: HistoryItem | null) {
  const params = effectiveParams(item)
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

function historyTitle(item: HistoryItem) {
  return item.title || promptText(item) || `${moduleLabel(item.moduleKey)}生成`
}

function friendlyConfig(item?: HistoryItem | null) {
  if (!item) return []
  const params = effectiveParams(item)
  const fields = configFields(item.moduleKey)
  return fields
    .map(field => ({ label: field.label, value: cleanValue(readPath(params, field.key)) }))
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
      { key: 'customTopic', label: '自定义主题' },
      { key: 'tone', label: '内容方向' },
      { key: 'style', label: '写作风格' },
      { key: 'note', label: '额外备注' },
      { key: 'imageSize', label: '图片比例' },
      { key: 'imageCount', label: '图片数量' },
      { key: 'withImage', label: '是否配图' },
    ],
    wechat: [
      { key: 'slots', label: '发布时段' },
      { key: 'style', label: '内容风格' },
      { key: 'length', label: '文案长度' },
      { key: 'note', label: '额外备注' },
      { key: 'imageSize', label: '图片比例' },
      { key: 'withImage', label: '是否配图' },
    ],
    article: [
      { key: 'title', label: '文章标题' },
      { key: 'style', label: '排版预设' },
      { key: 'length', label: '文章长度' },
      { key: 'imageCount', label: '配图数量' },
      { key: 'fileName', label: '上传文件' },
    ],
    poster: [
      { key: 'mode', label: '创作模式' },
      { key: 'theme', label: '海报主题' },
      { key: 'content', label: '海报内容' },
      { key: 'style', label: '海报风格' },
      { key: 'imageSize', label: '图片比例' },
    ],
    video: [
      { key: 'sellingPoints', label: '商家/卖点' },
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
      { key: 'priceFloor', label: '最低可接受价' },
      { key: 'targetChannels', label: '重点渠道' },
    ],
    brain: [{ key: 'message', label: '经营问题' }],
    review: [{ key: 'guestType', label: '客群类型' }],
    reply: [
      { key: 'reviewType', label: '评价类型' },
      { key: 'style', label: '回复风格' },
    ],
  }
  return fields[moduleKey] || common
}

function readPath(source: Record<string, any>, key: string) {
  return key.split('.').reduce<any>((value, part) => value?.[part], source)
}

function cleanValue(value: any): string {
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

async function copyContent() {
  if (!selected.value?.outputContent) return
  await window.navigator.clipboard?.writeText(selected.value.outputContent)
  flash('内容已复制')
}

function reuse(item: HistoryItem) {
  saveReuseState(item)
  const route = moduleRoute(item.moduleKey)
  router.push({ path: route })
  flash('已复用当时配置')
}

function saveReuseState(item: HistoryItem) {
  const params = effectiveParams(item)
  const message = promptText(item)
  const state: Record<string, any> = {}

  if (item.moduleKey === 'xhs') {
    state.selectedTopics = normalizeTopics(params.topics || params.theme)
    state.selectedTone = params.tone || 'emotional'
    state.style = params.style || 'warm'
    state.note = params.note || ''
    state.withImage = params.withImage ?? true
    state.imageSize = params.imageSize || '3:4'
    state.imageCount = Number(params.imageCount || 6)
    state.customTopic = params.customTopic || ''
    state.generated = false
    state.title = ''
    state.body = ''
    state.tags = []
    state.xhsImageUrl = ''
    saveAiPageState('xhs', state)
    return
  }

  if (item.moduleKey === 'wechat') {
    const slots = Array.isArray(params.slots) ? params.slots : []
    saveAiPageState('wechat', {
      slots: {
        morning: !slots.length || slots.includes('morning'),
        noon: !slots.length || slots.includes('noon'),
        evening: !slots.length || slots.includes('evening'),
      },
      style: params.style || 'auto',
      length: params.length || 'mid',
      withImage: params.withImage ?? true,
      imageSize: params.imageSize || '1:1',
      note: params.note || '',
      generated: false,
    })
    return
  }

  if (item.moduleKey === 'article') {
    saveAiPageState('article', {
      step: 'config',
      selectedStyle: params.style || 'teal_tech',
      selectedLength: params.length || 'medium',
      withImage: params.withImage ?? true,
      imageCount: Number(params.imageCount || 3),
      articleTitle: params.title || params.topic || '',
      sections: [],
      ending: '',
      previewMode: 'desktop',
    })
    return
  }

  if (item.moduleKey === 'poster') {
    saveAiPageState('poster', {
      mode: params.mode || 'text2img',
      t2iTheme: params.theme || params.poster_theme || '',
      t2iContent: params.content || params.message || '',
      t2iStyle: params.visual_style || params.style || 'chinese',
      t2iSize: params.imageSize || params.size || '3:4',
      t2iGenerated: false,
      t2iImageUrl: '',
      t2iResultText: '',
      beautifyDesc: params.prompt || params.content || '',
      beautifySize: params.imageSize || params.size || '3:4',
      beautifyDone: false,
      beautifyResult: '',
      t2iTaskId: null,
      t2iGenerating: false,
    })
    return
  }

  if (item.moduleKey === 'video') {
    saveAiPageState('video', {
      selectedView: params.view || '商家老板',
      sellingPoints: params.sellingPoints || message,
      selectedStyle: params.style || '沉浸式体验',
      selectedGoal: params.goal || '引流涨粉',
      selectedDuration: params.duration || '30',
      generateCount: Number(params.count || 3),
      generated: false,
      versions: [],
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

  if (item.moduleKey === 'reply') {
    saveAiPageState('reply', {
      reviewType: params.reviewType || '五星好评·夸环境',
      replyStyle: params.style || '温暖亲切',
      replyText: '',
    })
    return
  }

  if (item.moduleKey === 'review') {
    saveAiPageState('review', {
      selectedType: params.guestType || null,
      selectedIncentive: null,
      reviews: {},
    })
    return
  }

  if (item.moduleKey === 'brain') {
    saveAiPageState('brain', {
      input: message,
      messages: [],
    })
  }
}

function flash(message: string) {
  toast.value = message
  setTimeout(() => (toast.value = ''), 1600)
}
</script>

<template>
  <div class="space-y-5 pb-8">
    <transition name="toast">
      <div v-if="toast" class="fixed right-6 top-6 z-50 rounded-lg bg-bamboo-900 px-4 py-2 text-sm text-bamboo-50 shadow-xl">
        {{ toast }}
      </div>
    </transition>

    <section class="rounded-2xl border border-cream-300 bg-white p-6 shadow-sm">
      <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div class="flex items-center gap-2 text-xs font-semibold text-bamboo-700">
            <Clock class="h-4 w-4" />
            内容历史
          </div>
          <h1 class="mt-3 text-2xl font-semibold text-bamboo-950">每一次生成，都能找回和复用</h1>
          <p class="mt-2 text-sm text-warm-600">查看当时的问题、创作配置和生成结果，也可以一键回到对应页面继续生成。</p>
        </div>
        <button class="inline-flex items-center gap-2 rounded-lg border border-cream-300 px-3 py-2 text-xs font-medium text-warm-700 hover:border-bamboo-400 hover:text-bamboo-800" @click="load()">
          <Loader2 v-if="loading" class="h-3.5 w-3.5 animate-spin" />
          <RotateCcw v-else class="h-3.5 w-3.5" />
          刷新
        </button>
      </div>

      <div class="mt-5 flex flex-wrap gap-2">
        <button
          v-for="module in modules"
          :key="module.key"
          class="rounded-full border px-3 py-1.5 text-xs transition"
          :class="activeModule === module.key ? 'border-bamboo-800 bg-bamboo-800 text-bamboo-50' : 'border-cream-300 bg-white text-warm-600 hover:border-bamboo-400'"
          @click="switchModule(module.key)"
        >
          {{ module.label }}
        </button>
      </div>
    </section>

    <section class="grid h-[calc(100vh-260px)] min-h-[620px] gap-5 xl:grid-cols-[380px_1fr]">
      <div class="flex min-h-0 flex-col rounded-2xl border border-cream-300 bg-white shadow-sm">
        <div class="border-b border-cream-200 p-4 text-sm font-semibold text-bamboo-950">历史列表</div>
        <div v-if="loading" class="flex justify-center py-16">
          <Loader2 class="h-6 w-6 animate-spin text-bamboo-700" />
        </div>
        <div v-else class="min-h-0 flex-1 overflow-y-auto p-3">
          <button
            v-for="item in filtered"
            :key="item.id"
            class="mb-2 w-full rounded-xl border p-3 text-left transition hover:-translate-y-0.5"
            :class="selected?.id === item.id ? 'border-bamboo-400 bg-bamboo-50 shadow-sm' : 'border-cream-200 bg-white hover:border-bamboo-300'"
            @click="selected = item"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="rounded-full bg-cream-100 px-2 py-0.5 text-[10px] font-medium text-bamboo-800">{{ moduleLabel(item.moduleKey) }}</span>
              <span class="text-[10px] text-warm-400">{{ formatTime(item.createdAt) }}</span>
            </div>
            <div class="mt-2 line-clamp-1 text-sm font-semibold text-bamboo-950">{{ historyTitle(item) }}</div>
            <p class="mt-1 line-clamp-2 break-words text-xs leading-5 text-warm-500">{{ promptText(item) || resultText(item) || (imageUrl(item) ? '图片生成完成' : '暂无结果内容') }}</p>
            <div class="mt-2 flex items-center justify-between text-[10px] text-warm-400">
              <span>{{ item.status === 'success' ? '成功' : item.status }}</span>
              <span>{{ item.costCredits || 0 }} 算力</span>
            </div>
          </button>

          <div v-if="filtered.length === 0" class="rounded-xl border border-dashed border-cream-300 bg-cream-50 py-14 text-center">
            <FileText class="mx-auto h-8 w-8 text-warm-300" />
            <p class="mt-3 text-sm text-warm-500">暂无生成历史</p>
          </div>
        </div>
      </div>

      <div class="min-h-0 rounded-2xl border border-cream-300 bg-white shadow-sm">
        <div v-if="!selected" class="flex h-full min-h-[520px] flex-col items-center justify-center text-warm-400">
          <FileText class="h-10 w-10 opacity-40" />
          <p class="mt-3 text-sm">选择一条历史记录查看详情</p>
        </div>

        <div v-else class="flex h-full min-h-0 flex-col">
          <div class="border-b border-cream-200 p-5">
            <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <span class="rounded-full bg-bamboo-100 px-2 py-0.5 text-[10px] font-medium text-bamboo-800">{{ moduleLabel(selected.moduleKey) }}</span>
                <h2 class="mt-3 text-lg font-semibold text-bamboo-950">{{ selected.title || '生成详情' }}</h2>
                <p class="mt-1 text-xs text-warm-500">{{ formatTime(selected.createdAt) }} · 消耗 {{ selected.costCredits || 0 }} 算力</p>
              </div>
              <div class="flex gap-2">
                <button class="detail-action" @click="router.push({ path: `/history/${selected.moduleKey}/${selected.id}`, query: { from: '/history' } })">
                  <ExternalLink class="h-3.5 w-3.5" />
                  查看详细页
                </button>
                <button class="detail-action" @click="copyContent">
                  <Copy class="h-3.5 w-3.5" />
                  复制结果
                </button>
                <button class="detail-action primary" @click="reuse(selected)">
                  <ExternalLink class="h-3.5 w-3.5" />
                  复用配置
                </button>
              </div>
            </div>
          </div>

          <div class="grid min-h-0 min-w-0 flex-1 gap-4 overflow-hidden p-5 lg:grid-cols-[minmax(0,1fr)_minmax(260px,320px)]">
            <div class="min-w-0">
              <div class="mb-2 text-xs font-semibold text-warm-500">生成结果</div>
              <div class="h-[calc(100vh-430px)] min-h-[420px] overflow-y-auto rounded-xl border border-cream-200 bg-cream-50 p-4 text-sm leading-7 text-bamboo-950">
                <div v-if="selected.moduleKey === 'wechat' && wechatHistorySlots(selected).length" class="wechat-history-preview">
                  <article v-for="slot in wechatHistorySlots(selected)" :key="slot.key" class="wechat-history-card">
                    <div class="wechat-history-head">
                      <div>
                        <span>{{ slot.label }}</span>
                        <strong>{{ slot.time }}</strong>
                      </div>
                      <small v-if="slot.schedule">{{ slot.schedule }}</small>
                    </div>
                    <p class="wechat-history-copy">{{ slot.content }}</p>
                    <div v-if="slot.suggestion" class="wechat-history-tip">
                      <span>配图建议</span>
                      <p>{{ slot.suggestion.replace(/^\d+[:：、.)]\s*/, '') }}</p>
                    </div>
                  </article>
                </div>
                <div v-else-if="imageUrl(selected)" class="space-y-4">
                  <div class="relative mx-auto max-w-[520px] overflow-hidden rounded-2xl border border-cream-300 bg-white shadow-sm">
                    <img :src="imageUrl(selected)" class="block max-h-[640px] w-full object-contain" />
                    <a
                      :href="imageUrl(selected)"
                      target="_blank"
                      download
                      class="absolute right-3 top-3 inline-flex h-9 w-9 items-center justify-center rounded-full bg-bamboo-900 text-bamboo-50 shadow-lg transition hover:bg-bamboo-800"
                      title="下载图片"
                    >
                      <Download class="h-4 w-4" />
                    </a>
                  </div>
                  <div v-if="resultText(selected) && selected.moduleKey !== 'brain' && selected.moduleKey !== 'occupancy_image' && !isPlanOutput(selected)" class="whitespace-pre-wrap break-words rounded-xl border border-cream-200 bg-white p-4 text-sm leading-7 text-bamboo-950">
                    {{ resultText(selected) }}
                  </div>
                  <div v-else-if="resultText(selected)" class="rounded-xl border border-cream-200 bg-white p-4">
                    <AiGenerationPreview :item="selected" />
                  </div>
                </div>
                <AiGenerationPreview v-else-if="selected.moduleKey === 'brain' || selected.moduleKey === 'occupancy_image' || isPlanOutput(selected)" :item="selected" />
                <div v-else class="whitespace-pre-wrap break-words">
                  {{ resultText(selected) }}
                </div>
              </div>
            </div>
            <aside class="min-h-0 min-w-0 space-y-4 overflow-y-auto">
              <section>
                <div class="mb-2 text-xs font-semibold text-warm-500">当时的问题/提示词</div>
                <div class="rounded-xl border border-cream-200 bg-cream-50 p-4 text-sm leading-7 text-bamboo-950">
                  {{ promptText(selected) || '未记录提示词' }}
                </div>
              </section>

              <section>
                <div class="mb-2 text-xs font-semibold text-warm-500">当时配置</div>
                <div class="rounded-xl border border-cream-200 bg-white p-4">
                  <dl v-if="friendlyConfig(selected).length" class="space-y-3">
                    <div v-for="row in friendlyConfig(selected)" :key="row.label" class="border-b border-cream-100 pb-3 last:border-b-0 last:pb-0">
                      <dt class="text-[11px] font-semibold text-warm-500">{{ row.label }}</dt>
                      <dd class="mt-1 break-words text-sm leading-6 text-bamboo-950">{{ row.value }}</dd>
                    </div>
                  </dl>
                  <div v-else class="text-sm text-warm-500">这条历史没有可复用配置。</div>
                </div>
              </section>
            </aside>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-action {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  border-radius: 0.5rem;
  border: 1px solid #e7dfd0;
  padding: 0.5rem 0.75rem;
  color: #6f6252;
  font-size: 0.75rem;
  font-weight: 600;
}
.detail-action:hover {
  border-color: #6b7d4d;
  color: #40522c;
}
.detail-action.primary {
  background: #31451f;
  border-color: #31451f;
  color: #fff8e8;
}

.wechat-history-preview {
  display: grid;
  gap: 0.85rem;
}

.wechat-history-card {
  border: 1px solid #eadfce;
  border-radius: 0.9rem;
  background: #fff;
  padding: 1rem;
}

.wechat-history-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  border-bottom: 1px solid #f0e5d4;
  padding-bottom: 0.65rem;
}

.wechat-history-head span {
  display: inline-flex;
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.2rem 0.6rem;
  color: #234d32;
  font-size: 0.72rem;
  font-weight: 800;
}

.wechat-history-head strong {
  margin-left: 0.5rem;
  color: #b3741f;
  font-size: 0.78rem;
}

.wechat-history-head small {
  max-width: 48%;
  color: #9a7b55;
  font-size: 0.7rem;
  line-height: 1.55;
}

.wechat-history-copy {
  margin-top: 0.75rem;
  white-space: pre-line;
  color: #173826;
  font-size: 0.9rem;
  line-height: 1.75;
}

.wechat-history-tip {
  margin-top: 0.8rem;
  border-radius: 0.75rem;
  background: #f7faf4;
  padding: 0.65rem 0.75rem;
}

.wechat-history-tip span {
  color: #315b37;
  font-size: 0.72rem;
  font-weight: 800;
}

.wechat-history-tip p {
  margin-top: 0.25rem;
  color: #6f6252;
  font-size: 0.78rem;
  line-height: 1.65;
}
.toast-enter-active,
.toast-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
