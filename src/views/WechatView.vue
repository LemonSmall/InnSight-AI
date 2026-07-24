<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  MessageCircleHeart,
  Copy,
  RefreshCw,
  Zap,
  Building2,
  Sparkles,
  CheckCircle2,
  Clock,
  FileText,
  Loader2,
  Image,
  Download,
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { useCreditsStore } from '@/stores/credits'
import { collectStreamContent } from '@/api/content'
import { getGenerationHistory, getGenerationHistoryDetail } from '@/api/history'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { buildContentAiParams } from '@/utils/aiContextParams'
import { extractDisplayText, extractImageUrl, normalizeImageUrl as normalizeRenderableImageUrl } from '@/utils/aiContentRender'
import { imageUrl as historyImageUrl } from '@/utils/generationHistory'
import AiPolishControl from '@/components/ai/AiPolishControl.vue'

const hotel = useHotelStore()
const credits = useCreditsStore()
const router = useRouter()
const pageStateKey = 'wechat'

onMounted(() => {
  restoreState()
  if (withImage.value && !outputs.value.some(o => displayImageUrl(o))) {
    syncWechatImageFromHistoryWithRetry().catch(() => {})
  }
  credits.loadFromApi().catch(() => {})
})

interface SlotOutput {
  id: 'morning' | 'noon' | 'evening'
  label: string
  time: string
  typeLabel: string
  typeClass: string
  content: string
  imageSuggestion?: string
  imageUrl?: string
  lastImageUrl?: string
}

const baseOutputs = (): SlotOutput[] => [
  { id: 'morning', label: '早间', time: '07:30-08:00', typeLabel: '种草引流', typeClass: 'bg-amber-50 text-amber-700', content: '' },
  { id: 'noon', label: '午间', time: '12:00-12:30', typeLabel: '互动留客', typeClass: 'bg-blue-50 text-blue-600', content: '' },
  { id: 'evening', label: '晚间', time: '20:00-21:00', typeLabel: '转化收口', typeClass: 'bg-purple-50 text-purple-700', content: '' },
]

const slots = reactive({
  morning: true,
  noon: true,
  evening: true,
})

const style = ref('auto')
const length = ref('mid')
const withImage = ref(true)
const imageSize = ref('1:1')
const note = ref('')
const generating = ref(false)
const generated = ref(false)
const loadingStep = ref(0)
const imageSyncing = ref(false)
const toast = ref('')
let loadingTimer: ReturnType<typeof setInterval> | null = null
const brokenWechatImages = new Set<string>()

const outputs = ref<SlotOutput[]>(baseOutputs())
const visibleLoadingSteps = computed(() => withImage.value
  ? ['读取已确认酒店资料', '匹配朋友圈发布策略', '生成朋友圈文案', '同步内容历史配图']
  : ['读取已确认酒店资料', '匹配朋友圈发布策略', '生成朋友圈文案'],
)

watch(
  () => ({
    slots: { ...slots },
    style: style.value,
    length: length.value,
    withImage: withImage.value,
    imageSize: imageSize.value,
    note: note.value,
    times: outputs.value.map(item => ({ id: item.id, time: item.time })),
  }),
  () => persistState(),
  { deep: true },
)

function persistState() {
  saveAiPageState(pageStateKey, {
    slots: { ...slots },
    style: style.value,
    length: length.value,
    withImage: withImage.value,
    imageSize: imageSize.value,
    note: note.value,
    generated: generated.value,
    outputs: outputs.value,
  })
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (restored?.slots && typeof restored.slots === 'object') {
    slots.morning = restored.slots.morning ?? slots.morning
    slots.noon = restored.slots.noon ?? slots.noon
    slots.evening = restored.slots.evening ?? slots.evening
  }
  if (restored) {
    style.value = restored.style || style.value
    length.value = restored.length || length.value
    withImage.value = restored.withImage ?? withImage.value
    imageSize.value = restored.imageSize || imageSize.value
    note.value = restored.note || note.value
  }
  if (Array.isArray(restored?.outputs)) {
    const restoredOutputs = restored.outputs as Array<Partial<SlotOutput> & { id?: SlotOutput['id'] }>
    const restoredMap = new Map(restoredOutputs.map(item => [item?.id, item]))
    outputs.value = baseOutputs().map(item => {
      const restoredItem = restoredMap.get(item.id)
      return restoredItem
        ? {
            ...item,
            content: typeof restoredItem.content === 'string' ? restoredItem.content : '',
            imageSuggestion: typeof restoredItem.imageSuggestion === 'string' ? restoredItem.imageSuggestion : '',
            imageUrl: typeof restoredItem.imageUrl === 'string' ? restoredItem.imageUrl : '',
            lastImageUrl: typeof restoredItem.lastImageUrl === 'string' ? restoredItem.lastImageUrl : '',
            time: normalizePublishTime(restoredItem.time, item.time),
          }
        : item
    })
  }
  generated.value = Boolean(restored?.generated && outputs.value.some(o => o.content))
  applyStrategyReuseDraft()
}

function applyStrategyReuseDraft() {
  const draft = loadAiPageState<any>('strategy-reuse:wechat')
  if (!draft?.content) return
  slots.morning = true
  slots.noon = true
  slots.evening = true
  style.value = 'auto'
  length.value = 'mid'
  note.value = draft.content
  persistState()
}

const enabledSlots = computed(() => outputs.value.filter(o => slots[o.id]))
const activeSlots = computed(() => Object.keys(slots).filter(k => slots[k as keyof typeof slots]))

const styleLabels: Record<string, string> = {
  auto: '综合本店资料',
  grass: '种草引流',
  interact: '互动留客',
  flex: '品牌展示',
  holiday: '节日特惠',
  weather: '天气借势',
}

const slotMeta: Record<string, Pick<SlotOutput, 'typeClass' | 'typeLabel'>> = {
  morning: { typeLabel: '种草引流', typeClass: 'bg-amber-50 text-amber-700' },
  noon: { typeLabel: '互动留客', typeClass: 'bg-blue-50 text-blue-600' },
  evening: { typeLabel: '转化收口', typeClass: 'bg-purple-50 text-purple-700' },
}

async function generate() {
  if (!activeSlots.value.length || generating.value) return

  generating.value = true
  generated.value = false
  loadingStep.value = 0
  imageSyncing.value = false
  outputs.value.forEach(o => {
    o.content = ''
    o.imageSuggestion = ''
    o.imageUrl = ''
  })
  persistState()

  const steps = [...visibleLoadingSteps.value]
  loadingTimer = setInterval(() => {
    if (loadingStep.value < steps.length) loadingStep.value++
  }, 600)

  let failed = false
  let currentGenerationId: number | null = null
  let imageReady = !withImage.value
  try {
    const params = buildContentAiParams(hotel, 'wechat', {
      slots: activeSlots.value,
      style: style.value,
      length: length.value,
      publishTimes: Object.fromEntries(outputs.value.map(item => [item.id, item.time])),
      theme: `朋友圈${activeSlots.value.join('/')}文案`,
      message: note.value || `生成${activeSlots.value.join('/')}朋友圈文案`,
      note: note.value,
      withImage: withImage.value,
      imageSize: imageSize.value,
      outputFormat: 'json',
      schema: {
        morning: '早间朋友圈文案',
        noon: '午间朋友圈文案',
        evening: '晚间朋友圈文案',
        imageSuggestions: ['配图建议'],
      },
    })
    const content = await collectStreamContent('wechat', params, {
      onChunk(_chunk, text) {
        applyWechatResult(text)
        generated.value = !withImage.value && outputs.value.some(o => Boolean(o.content))
        persistState()
      },
      onDone(payload) {
        currentGenerationId = extractGenerationId(payload)
      },
    })
    applyWechatResult(content)
    if (withImage.value) {
      imageSyncing.value = true
      loadingStep.value = steps.length - 1
      imageReady = await syncWechatImageFromHistoryWithRetry(true, currentGenerationId)
      if (!imageReady) flashToast('没有从生成记录取到配图，请重新生成或查看生成记录')
    }
    persistState()
  } catch (e: any) {
    failed = true
    flashToast(e?.message || 'AI 调用失败，请稍后重试')
  } finally {
    if (loadingTimer) clearInterval(loadingTimer)
    loadingStep.value = steps.length
    imageSyncing.value = false
    generating.value = false
    generated.value = outputs.value.some(o => Boolean(o.content)) && imageReady
    persistState()
    if (failed || !generated.value) {
      generated.value = false
      persistState()
    }
  }
}

function applyWechatResult(raw: string) {
  const cleaned = stripThink(raw)
  const parsed = normalizeWechatPayload(cleaned)
  const hasStructuredSlots = Boolean(parsed && ['morning', 'noon', 'evening'].some(key => isNonEmptyText(parsed[key])))
  const imageSuggestions = normalizeSlotList(parsed?.imageSuggestions || parsed?.imageSuggestion || parsed?.image_suggestions || parsed?.image_suggestion || parsed?.imagePrompts || parsed?.image_prompts)
  const imageUrls = normalizeSlotList(parsed?.imageUrls || parsed?.imageUrl || parsed?.image_urls || parsed?.image_url || parsed?.images || parsed?.image || parsed?.pictures || parsed?.materials || parsed?.imageList || parsed?.image_list)
  const fallbackImageUrl = normalizeImageUrl(extractImageUrl(cleaned))
  outputs.value.forEach((out, index) => {
    if (!slots[out.id]) return
    const value = parsed?.[out.id]
    if (isNonEmptyText(value)) {
      out.content = cleanSlotText(value)
    } else if (!hasStructuredSlots && cleaned && !looksLikeJsonShell(cleaned)) {
      out.content = extractDisplayText(cleaned)
    }
    out.imageSuggestion = normalizeSlotText(imageSuggestions[index] || parsed?.[`${out.id}ImageSuggestion`] || parsed?.[`${out.id}_image_suggestion`] || parsed?.[out.id]?.imageSuggestion || parsed?.[out.id]?.image_suggestion || parsed?.[out.id]?.imagePrompt || parsed?.[out.id]?.image_prompt)
    const slotImage = imageUrls[index]
      || parsed?.[`${out.id}ImageUrl`]
      || parsed?.[`${out.id}_image_url`]
      || parsed?.[out.id]?.imageUrl
      || parsed?.[out.id]?.image_url
      || parsed?.[out.id]?.image
      || parsed?.[out.id]?.url
      || extractImageUrl(out.imageSuggestion)
      || fallbackImageUrl
    out.imageUrl = normalizeImageUrl(slotImage)
    if (out.imageUrl) {
      out.lastImageUrl = out.imageUrl
    }
    // 发布时间由左侧配置决定，不再用 AI 输出覆盖。
    out.typeLabel = style.value === 'auto' ? slotMeta[out.id].typeLabel : styleLabels[style.value] || 'AI 生成'
    out.typeClass = slotMeta[out.id].typeClass
  })
}

function displayImageUrl(slot: SlotOutput) {
  return String(slot.imageUrl || slot.lastImageUrl || '').trim()
    || normalizeImageUrl(extractImageUrl(slot.content))
    || normalizeImageUrl(extractImageUrl(slot.imageSuggestion))
}

function handleImageError(slotId: SlotOutput['id']) {
  const out = outputs.value.find(item => item.id === slotId)
  if (!out) return
  const failedUrl = displayImageUrl(out)
  if (failedUrl) brokenWechatImages.add(failedUrl)
  if (normalizeImageUrl(out.imageUrl) === failedUrl) out.imageUrl = ''
  if (normalizeImageUrl(out.lastImageUrl) === failedUrl) out.lastImageUrl = ''
  persistState()
  syncWechatImageFromHistoryWithRetry(true).catch(() => {})
}

function stripThink(text: string): string {
  return String(text || '')
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/^\s*```(?:json)?/i, '')
    .replace(/```\s*$/i, '')
    .trim()
}

function parseWechatJson(text: string): any | null {
  try {
    return JSON.parse(text)
  } catch {
    const match = balancedJsonObject(text)
    if (!match) return null
    try {
      return JSON.parse(match)
    } catch {
      return null
    }
  }
}

function normalizeWechatPayload(text: string): any | null {
  let current: any = text
  for (let i = 0; i < 4; i++) {
    if (typeof current !== 'string') break
    const parsed = parseWechatJson(stripCodeFence(current))
    if (!parsed) break
    current = parsed
    const nested = parsed.content || parsed.data?.content || parsed.outputs?.content || parsed.result
    if (typeof nested === 'string' && nested.trim()) {
      current = nested
      continue
    }
    break
  }
  if (typeof current === 'string') return parseWechatJson(stripCodeFence(current))
  return current && typeof current === 'object' ? current : null
}

function stripCodeFence(text: string) {
  return String(text || '')
    .replace(/^\s*```(?:json)?\s*/i, '')
    .replace(/\s*```\s*$/i, '')
    .trim()
}

function balancedJsonObject(text: string) {
  const source = stripCodeFence(text)
  const start = source.indexOf('{')
  if (start < 0) return ''
  let depth = 0
  let inString = false
  let escape = false
  for (let i = start; i < source.length; i++) {
    const ch = source[i]
    if (escape) {
      escape = false
      continue
    }
    if (ch === '\\') {
      escape = true
      continue
    }
    if (ch === '"') {
      inString = !inString
      continue
    }
    if (inString) continue
    if (ch === '{') depth++
    if (ch === '}') {
      depth--
      if (depth === 0) return source.slice(start, i + 1)
    }
  }
  return ''
}

function normalizeSlotList(value: any): string[] {
  if (Array.isArray(value)) return value.map(normalizeSlotText).filter(Boolean)
  if (value && typeof value === 'object') {
    return ['morning', 'noon', 'evening'].map(key => normalizeSlotText(value[key])).filter(Boolean)
  }
  const text = normalizeSlotText(value)
  return text ? [text] : []
}

function normalizeSlotText(value: any) {
  if (typeof value === 'string') return value.trim()
  if (value && typeof value === 'object') return String(value.text || value.content || value.prompt || value.url || '').trim()
  return ''
}

function cleanSlotText(value: any) {
  if (typeof value === 'string') return stripCodeFence(value).trim()
  if (value && typeof value === 'object') return normalizeSlotText(value)
  return ''
}

function normalizeImageUrl(value: any) {
  const text = normalizeSlotText(value)
  if (!text) return ''
  const markdown = text.match(/!\[[^\]]*]\((https?:\/\/[^)\s"'<>]+)(?:\s+["'][^"']*["'])?\)/i)
  if (markdown?.[1]) return normalizeRenderableImageUrl(markdown[1])
  const match = text.match(/https?:\/\/[^\s"'<>，]+|data:image\/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=]+/)
  return normalizeRenderableImageUrl(match?.[0] || text)
}

function firstUsableImageUrl(values: any[]) {
  for (const value of sortPreferredImageUrls(values.map(normalizeImageUrl).filter(Boolean))) {
    const url = normalizeImageUrl(value)
    if (url && !brokenWechatImages.has(url)) return url
  }
  return ''
}

function historyDisplayImageUrls(item: any) {
  const fromWechatHistory = extractWechatImagesFromHistory(item)
  if (fromWechatHistory.length) return fromWechatHistory
  const fromAssets = parseHistoryOutputAssets(item?.outputAssets ?? item?.output_assets)
  if (fromAssets.length) return fromAssets
  return collectImageUrls(item?.outputContent ?? item?.output_content)
}

function parseHistoryOutputAssets(value: any) {
  if (Array.isArray(value)) return value.filter(item => typeof item === 'string' && item.trim())
  if (!value) return []
  if (typeof value === 'object') return collectImageUrls(value)
  try {
    const parsed = JSON.parse(String(value))
    if (Array.isArray(parsed)) return parsed.filter(item => typeof item === 'string' && item.trim())
    return collectImageUrls(parsed)
  } catch {
    return collectImageUrls(value)
  }
}

function extractGenerationId(payload: any) {
  const candidates = [
    payload?.generationId,
    payload?.generation_id,
    payload?.id,
    payload?.data?.generationId,
    payload?.data?.generation_id,
    payload?.data?.id,
  ]
  for (const value of candidates) {
    const id = Number(value)
    if (Number.isFinite(id) && id > 0) return id
  }
  return null
}

function sortPreferredImageUrls(values: string[]) {
  return uniqueStrings(values).sort((a, b) => imageUrlPriority(a) - imageUrlPriority(b))
}

function imageUrlPriority(url: string) {
  if (/^data:image\//i.test(url)) return 0
  if (/\/api\/public\/ai-images\//i.test(url)) return 1
  if (/\/uploads?\//i.test(url) || /\/files?\//i.test(url)) return 2
  if (/s3\.siliconflow\.cn/i.test(url)) return 9
  return 4
}

function normalizePublishTime(value: any, fallback: string) {
  const text = normalizeSlotText(value)
  if (!text) return fallback
  const range = text.match(/\d{1,2}[:：]\d{2}\s*[-~至到]\s*\d{1,2}[:：]\d{2}/)
  if (range?.[0]) return range[0].replace(/：/g, ':').replace(/\s+/g, '')
  const single = text.match(/\d{1,2}[:：]\d{2}/)
  if (single?.[0]) return single[0].replace(/：/g, ':')
  return fallback
}

function isNonEmptyText(value: any) {
  return Boolean(cleanSlotText(value))
}

function looksLikeJsonShell(text: string) {
  const value = stripCodeFence(text)
  return /^\s*\{/.test(value) || /^\s*\[/.test(value)
}

async function regen() {
  await generate()
}

async function copySlot(slot: string) {
  const out = outputs.value.find(o => o.id === slot)
  if (!out?.content) return
  try {
    await navigator.clipboard.writeText(out.content)
    flashToast('已复制')
  } catch {
    flashToast('复制失败')
  }
}

async function copyAll() {
  const texts = enabledSlots.value.map(o => `${o.label} ${o.time}\n${o.content}`).filter(Boolean).join('\n\n---\n\n')
  if (!texts) return
  try {
    await navigator.clipboard.writeText(texts)
    flashToast('已复制全部文案')
  } catch {
    flashToast('复制失败')
  }
}

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1600)
}

function countChars(slot: string): number {
  return outputs.value.find(o => o.id === slot)?.content?.length || 0
}

function onContentInput(slot: string, e: Event) {
  const out = outputs.value.find(o => o.id === slot)
  if (out) {
    out.content = (e.target as HTMLTextAreaElement).value
    persistState()
  }
}

async function syncWechatImageFromDetail(generationId: number, force = false) {
  try {
    const { data: detailData } = await getGenerationHistoryDetail(generationId, 'wechat')
    const detail = detailData?.data || detailData || null
    const images = historyDisplayImageUrls(detail)
    if (!images.length) return false
    applyWechatImages(images, force)
    return true
  } catch {
    return false
  }
}

function applyWechatImages(images: string[], force = false) {
  const usableImages = sortPreferredImageUrls(images)
  outputs.value.forEach((out, index) => {
    if (!slots[out.id]) return
    const image = firstUsableImageUrl([usableImages[index], usableImages[0], ...usableImages])
    if (!image) return
    if (force || !out.imageUrl) out.imageUrl = image
    if (force || !out.lastImageUrl) out.lastImageUrl = image
  })
  persistState()
}

async function syncLatestWechatImageFromHistory(force = false, generationId: number | null = null) {
  try {
    if (generationId && await syncWechatImageFromDetail(generationId, force)) {
      return true
    }

    const { data } = await getGenerationHistory('wechat', 12)
    const list = data?.data || data || []
    const records = Array.isArray(list)
      ? [...list].filter((item: any) => item?.status === 'success' || item?.status === 'done')
      : []

    for (const record of records) {
      if (!record?.id) continue
      if (generationId && Number(record.id) !== generationId) continue
      const { data: detailData } = await getGenerationHistoryDetail(Number(record.id), 'wechat')
      const detail = detailData?.data || detailData || null
      const images = historyDisplayImageUrls(detail)
      if (!images.length) continue

      applyWechatImages(images, force)
      return true
    }
    return false
  } catch {
    return false
  }
}

async function syncWechatImageFromHistoryWithRetry(force = false, generationId: number | null = null) {
  for (let attempt = 0; attempt < 120; attempt++) {
    if (await syncLatestWechatImageFromHistory(force, generationId)) return true
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  return false
}

function extractWechatImagesFromHistory(detail: any) {
  const parsed = parseWechatHistoryPayload(detail?.outputContent)
  const fromSlots = ['morning', 'noon', 'evening']
    .map((key, index) => {
      const nested = parsed?.[key]
      const list = parsed?.imageUrls || parsed?.image_urls || parsed?.images || parsed?.imageList || parsed?.image_list || []
      return normalizeImageUrl(
        nested?.imageUrl
        || nested?.image_url
        || nested?.image
        || nested?.url
        || (Array.isArray(list) ? list[index] : list?.[key])
        || extractImageUrl(JSON.stringify(nested || ''))
      )
    })
    .filter(Boolean)
  const fromStableRecord = [
    historyImageUrl(detail),
    extractImageUrl(JSON.stringify(detail?.outputAssets || '')),
  ].map(normalizeImageUrl).filter(Boolean)
  const fromRecord = [
    extractImageUrl(detail?.outputContent),
    extractImageUrl(JSON.stringify(detail || '')),
  ].map(normalizeImageUrl).filter(Boolean)
  const fromAllFields = collectImageUrls(detail)
  return sortPreferredImageUrls([...fromStableRecord, ...fromAllFields, ...fromSlots, ...fromRecord])
}

function collectImageUrls(value: any) {
  const raw = typeof value === 'string' ? value : JSON.stringify(value || '')
  const matches = raw.match(/https?:\/\/[^\s"'<>\\，]+|\/api\/public\/ai-images\/[^\s"'<>\\，]+|data:image\/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=]+/gi) || []
  return uniqueStrings(matches.map(normalizeImageUrl).filter(Boolean))
}

function parseWechatHistoryPayload(value: any) {
  const text = String(value || '').trim()
  if (!text) return null
  return normalizeWechatPayload(text)
}

function uniqueStrings(values: string[]) {
  const seen = new Set<string>()
  return values.filter(value => {
    const key = String(value || '').trim()
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}
</script>

<template>
  <div class="h-full flex flex-col">
    <div v-if="toast" class="fixed top-6 right-6 z-50 bg-bamboo-800 text-cream-100 px-5 py-3 rounded-lg shadow-lg text-sm">
      {{ toast }}
    </div>

    <div class="flex items-center justify-between mb-4">
      <div class="flex items-start gap-3">
        <MessageCircleHeart class="w-5 h-5 text-bamboo-700 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">朋友圈文案</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">按早 / 中 / 晚三档生成，可编辑后直接发布</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <button @click="router.push('/history/wechat')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <FileText class="w-3 h-3" />生成记录
        </button>
        <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <Zap class="w-3 h-3" />算力流水
        </button>
        <span v-if="!credits.loading && !credits.error" class="text-[10px] font-medium bg-bamboo-50 text-bamboo-800 px-2.5 py-1 rounded-full flex items-center gap-1">
          <Zap class="w-3 h-3" />
          {{ credits.currentBalance }} 算力
        </span>
        <span v-else class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2.5 py-1 rounded-full">
          算力余额不可用
        </span>
      </div>
    </div>

    <div class="wechat-workspace flex-1 min-h-0 grid grid-cols-[340px_minmax(0,1fr)] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <div class="wechat-settings border-r border-cream-200/60 p-4 overflow-y-auto">
        <div class="bg-cream-50 rounded-lg px-3 py-2.5 mb-4 border border-cream-200/60">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">本店资料</div>
          <div class="space-y-1.5">
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500 flex items-center gap-1"><Building2 class="w-3 h-3" />酒店</span>
              <span class="font-medium text-bamboo-950">{{ hotel.config.name || '未设置' }}</span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500">资料范围</span>
              <span class="font-medium text-bamboo-700">{{ hotel.config.city || '未设置城市' }} · {{ hotel.roomTypes.length }} 个房型</span>
            </div>
          </div>
        </div>

        <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">生成设置</div>

        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">发布时段</div>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="o in outputs"
              :key="o.id"
              @click="slots[o.id] = !slots[o.id]"
              :class="[
                'min-w-[92px] flex-1 px-2.5 py-2 rounded-2xl text-[11px] leading-5 text-center transition-colors border whitespace-normal break-words',
                slots[o.id] ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400',
              ]"
            >
              <span class="block font-medium">{{ o.label }}</span>
            </button>
          </div>
          <div class="mt-2 grid gap-1.5">
            <label
              v-for="o in outputs"
              v-show="slots[o.id]"
              :key="`${o.id}-time`"
              class="flex items-center gap-2 rounded-lg border border-cream-200 bg-cream-50 px-2 py-1.5"
            >
              <span class="w-10 shrink-0 text-[11px] font-medium text-warm-600">{{ o.label }}</span>
              <input
                v-model="o.time"
                type="text"
                class="min-w-0 flex-1 rounded-md border border-cream-300 bg-white px-2 py-1 text-[11px] text-bamboo-950 outline-none focus:border-bamboo-400"
                placeholder="例如 20:00-21:00"
              />
            </label>
          </div>
        </div>

        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">内容风格</div>
          <select v-model="style" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400">
            <option value="auto">综合本店资料</option>
            <option value="grass">种草引流</option>
            <option value="interact">互动留客</option>
            <option value="flex">品牌展示</option>
            <option value="holiday">节日特惠</option>
            <option value="weather">天气借势</option>
          </select>
        </div>

        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">文案长度</div>
          <select v-model="length" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400">
            <option value="short">短文案，60 字以内</option>
            <option value="mid">适中，80-120 字</option>
            <option value="long">详细，150 字以内</option>
          </select>
        </div>

        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">配图</div>
          <div class="flex gap-1.5">
            <button
              @click="withImage = true"
              :class="['px-3 py-1 rounded-full text-[11px] transition-colors border', withImage ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']"
            >
              <Image class="w-3 h-3 inline mr-1" />需要配图建议
            </button>
            <button
              @click="withImage = false"
              :class="['px-3 py-1 rounded-full text-[11px] transition-colors border', !withImage ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']"
            >
              只要文案
            </button>
          </div>
        </div>

        <div v-if="withImage" class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">图片尺寸</div>
          <div class="flex gap-1.5 flex-wrap">
            <button
              v-for="size in ['1:1', '4:3', '3:4', '16:9']"
              :key="size"
              @click="imageSize = size"
              :class="['px-2.5 py-1 rounded-full text-[11px] transition-colors border', imageSize === size ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']"
            >
              {{ size }}
            </button>
          </div>
        </div>

        <div class="mb-3">
          <div class="mb-1.5 flex items-center justify-between">
            <div class="text-[11px] font-medium text-warm-600">额外备注</div>
            <AiPolishControl :source-text="note" scene="wechat" field="note" @accept="note = $event" />
          </div>
          <textarea
            v-model="note"
            rows="3"
            class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400 resize-none"
            placeholder="例如：今天主推亲子房、下午有手作活动、早餐更新了菜单..."
          />
        </div>

        <div class="border-l-3 border-amber-400 bg-amber-50 rounded-r-lg p-2.5 mb-3 text-[11px] text-amber-800 leading-relaxed">
          内容只会引用本店已填写资料和本次备注。涉及价格、活动与权益时，请发布前核对。
        </div>

        <button
          @click="generate"
          :disabled="generating || !activeSlots.length"
          class="w-full py-2.5 rounded-lg bg-bamboo-800 text-bamboo-100 text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors"
        >
          <Sparkles class="w-4 h-4" />
          AI 生成朋友圈文案
        </button>
      </div>

      <div class="bg-cream-50 p-4 overflow-y-auto">
        <div v-if="!generating && !generated" class="flex flex-col items-center justify-center h-full min-h-[360px] gap-3 text-warm-500">
          <MessageCircleHeart class="w-8 h-8 opacity-30" />
          <p class="text-[13px]">配置参数后开始生成</p>
          <p class="text-[11px] opacity-70">结果会按早间、午间、晚间分开展示</p>
        </div>

        <div v-if="generating && !generated" class="generation-loading">
          <div class="loading-orb">
            <Loader2 class="w-7 h-7 text-bamboo-800 animate-spin" />
          </div>
          <p class="text-sm font-semibold text-bamboo-900">
            {{ imageSyncing ? '正在同步内容历史配图' : 'AI 正在生成朋友圈文案' }}
          </p>
          <div class="loading-steps">
            <div
              v-for="(s, i) in visibleLoadingSteps"
              :key="s"
              :class="['text-[11px] flex items-center gap-2 transition-colors', i < loadingStep ? 'text-bamboo-600' : i === loadingStep ? 'text-bamboo-800' : 'text-warm-400']"
            >
              <CheckCircle2 v-if="i < loadingStep" class="w-3 h-3 text-bamboo-600" />
              <Loader2 v-else-if="i === loadingStep" class="w-3 h-3 animate-spin" />
              <span v-else class="w-3 h-3 rounded-full border border-cream-300" />
              {{ s }}
            </div>
          </div>
        </div>

        <div v-if="generated" class="space-y-3">
          <template v-for="o in enabledSlots" :key="o.id">
            <div v-if="o.content" class="wechat-result-card bg-white border border-cream-300/60 rounded-lg p-4">
              <div class="flex items-center justify-between mb-3">
                <div class="flex items-center gap-2">
                  <span class="text-xs font-medium text-bamboo-950">{{ o.label }} {{ o.time }}</span>
                  <span :class="['text-[10px] px-2 py-0.5 rounded-full font-medium', o.typeClass]">
                    {{ o.typeLabel }}
                  </span>
                </div>
                <div class="flex gap-1.5">
                  <button @click="regen" class="text-[11px] px-2 py-1 rounded-md border border-cream-300 bg-cream-50 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 transition-colors flex items-center gap-1">
                    <RefreshCw class="w-3 h-3" />换一版
                  </button>
                  <button @click="copySlot(o.id)" class="text-[11px] px-2 py-1 rounded-md border border-cream-300 bg-cream-50 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 transition-colors flex items-center gap-1">
                    <Copy class="w-3 h-3" />复制
                  </button>
                </div>
              </div>

              <textarea
                :value="o.content"
                @input="(e) => onContentInput(o.id, e)"
                rows="5"
                class="wechat-copy-editor w-full text-[13px] leading-relaxed px-3 py-2 rounded-lg border border-cream-200 bg-cream-50 text-bamboo-950 resize-none focus:outline-none focus:border-bamboo-400 focus:bg-white"
              />

              <div v-if="withImage" class="wechat-image-panel mt-3 border border-cream-200 rounded-lg bg-cream-100 p-3 text-[11px] text-warm-600">
                <div class="wechat-image-preview" :class="{ 'has-image': displayImageUrl(o) }">
                  <img v-if="displayImageUrl(o)" :src="displayImageUrl(o)" alt="朋友圈配图" @error="handleImageError(o.id)" />
                  <div v-else class="image-placeholder">
                    <Image class="w-6 h-6" />
                    <span>{{ imageSize }} 配图位</span>
                  </div>
                </div>
                <div class="wechat-image-copy">
                  <div class="flex items-center gap-1.5 font-medium text-warm-700">
                    <Image class="w-3.5 h-3.5" />
                    配图建议 {{ imageSize }}
                  </div>
                  <p>{{ o.imageSuggestion || '建议使用真实客房、公共区域或当日服务场景照片。' }}</p>
                  <a
                    v-if="displayImageUrl(o)"
                    :href="displayImageUrl(o)"
                    target="_blank"
                    download
                    class="wechat-download-btn"
                  >
                    <Download class="w-3.5 h-3.5" />
                    下载配图
                  </a>
                </div>
              </div>

              <div class="flex items-center justify-between mt-2">
                <span class="text-[10px] text-warm-500">{{ countChars(o.id) }} 字</span>
                <span class="text-[10px] text-warm-500 flex items-center gap-1">
                  <Clock class="w-3 h-3" />
                  建议 {{ o.time }} 发布
                </span>
              </div>
            </div>
          </template>

          <div class="bg-white border border-cream-300/60 rounded-lg px-4 py-3 flex items-center justify-between gap-3">
            <span class="text-[11px] text-warm-600">已生成 {{ enabledSlots.filter(o => o.content).length }} 档文案，可编辑后发布</span>
            <div class="flex gap-2">
              <button @click="copyAll" class="text-[12px] px-3 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-700 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
                <Copy class="w-3.5 h-3.5" />复制全部
              </button>
              <button @click="generate" class="text-[12px] px-3 py-1.5 rounded-lg bg-bamboo-800 text-bamboo-100 hover:bg-bamboo-900 transition-colors flex items-center gap-1.5">
                <RefreshCw class="w-3.5 h-3.5" />重新生成
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wechat-workspace {
  align-items: stretch;
}

.wechat-settings {
  position: relative;
  z-index: 1;
  min-width: 0;
  background: #fff;
}

.wechat-settings button {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.wechat-result-card {
  overflow: hidden;
}

.wechat-result-card > .flex:first-child {
  align-items: flex-start;
  gap: 0.75rem;
}

.wechat-result-card > .flex:first-child > .flex:last-child {
  flex-shrink: 0;
}

.wechat-copy-editor {
  min-height: 150px;
  max-height: 260px;
  overflow-y: auto;
}

.wechat-image-panel {
  display: grid;
  grid-template-columns: minmax(120px, 180px) minmax(0, 1fr);
  gap: 0.75rem;
  align-items: start;
}

.wechat-image-preview {
  display: flex;
  aspect-ratio: 1 / 1;
  width: 100%;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 0.75rem;
  border: 1px solid #eadfce;
  background: #fbf8f3;
}

.wechat-image-preview img {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.image-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  color: #9b8976;
  font-size: 0.72rem;
  font-weight: 600;
}

.wechat-image-copy {
  align-self: stretch;
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 0.55rem;
  min-width: 0;
}

.wechat-image-copy p {
  margin: 0;
  color: #7d6958;
  font-size: 0.78rem;
  line-height: 1.75;
}

.wechat-download-btn {
  margin-top: auto;
  justify-self: start;
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 0.35rem;
  border-radius: 999px;
  background: #234d32;
  padding: 0.45rem 0.8rem;
  color: #f7faf4;
  font-size: 0.7rem;
  font-weight: 700;
  transition: 150ms ease;
}

.wechat-download-btn:hover {
  background: #183b26;
}

.generation-loading {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.85rem;
  text-align: center;
}

.loading-orb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3.6rem;
  height: 3.6rem;
  border-radius: 1rem;
  background: #f2f8ee;
  box-shadow: inset 0 0 0 1px #d9e7ce;
}

.loading-steps {
  display: grid;
  gap: 0.35rem;
}

@media (max-width: 1180px) {
  .wechat-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .wechat-settings {
    border-right: 0;
    border-bottom: 1px solid #f0e7dc;
  }
}

@media (max-width: 760px) {
  .wechat-image-panel {
    grid-template-columns: 1fr;
  }
}
</style>
