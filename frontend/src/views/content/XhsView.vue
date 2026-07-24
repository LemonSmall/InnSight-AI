<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Instagram, Zap, Building2, FileText,
  Sparkles, CheckCircle2, Loader2,
  Copy, Image, Download
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { useCreditsStore } from '@/stores/credits'
import { collectStreamContent, generateContent, getTaskResult } from '@/api/content'
import { getGenerationHistory, getGenerationHistoryDetail } from '@/api/history'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { copyTextToClipboard } from '@/utils/clipboard'
import { buildContentAiParams } from '@/utils/aiContextParams'
import { extractImageUrl, normalizeImageUrl, toPlainTextWithoutImageMarkdown } from '@/utils/aiContentRender'
import { imageUrl as historyImageUrl, type HistoryItem } from '@/utils/generationHistory'
import AiPolishControl from '@/components/ai/AiPolishControl.vue'

const hotel = useHotelStore()
const credits = useCreditsStore()
const router = useRouter()
const pageStateKey = 'xhs'

onMounted(async () => {
  restoreState()
  if (xhsTaskId.value && generating.value) {
    try {
      await pollXhsTaskUntilDone()
    } catch (error: any) {
      xhsImageDebugError.value = error?.message || 'xhs task polling failed'
    } finally {
      generating.value = false
      generated.value = Boolean(title.value || body.value || displayXhsImageUrl.value)
      persistState()
      reportXhsImageState('mounted:after-task-resume')
      credits.loadFromApi().catch(() => {})
    }
    return
  }
  if (isTemporaryUnsignedImageUrl(xhsHistoryImageUrl.value)) {
    xhsHistoryImageUrl.value = ''
    xhsLastImageUrl.value = ''
  }
  reportXhsImageState('mounted:after-restore')
  if (withImage.value) {
    if (xhsGenerationId.value) {
      const restored = await syncXhsImageFromGeneration(xhsGenerationId.value)
      if (!restored) {
        await syncLatestXhsImageFromHistory()
      }
    } else {
      await syncLatestXhsImageFromHistory()
    }
  }
  reportXhsImageState('mounted:after-sync')
  credits.loadFromApi().catch(() => {})
})

// ====== 状态 ======
const topics = [
  { val: 'rain', label: '🌧️ 雨天竹林' },
  { val: 'festival', label: '🎋 节日氛围' },
  { val: 'couple', label: '💑 情侣度假' },
  { val: 'family', label: '👨‍👩‍👧 亲子出行' },
  { val: 'hotspring', label: '♨️ 私汤温泉' },
  { val: 'breakfast', label: '🍳 有机早餐' },
  { val: 'vlog', label: '📸 日常 vlog' },
  { val: 'escape', label: '🌿 周末逃离' },
]

const tones = [
  { val: 'emotional', label: '情绪种草' },
  { val: 'guide', label: '攻略干货' },
  { val: 'deal', label: '限时特惠' },
  { val: 'review', label: '探店测评' },
]

const selectedTopics = ref<Set<string>>(new Set(['rain']))
const selectedTone = ref('emotional')
const style = ref('warm')
const note = ref('')
const withImage = ref(true)
const imageSize = ref('3:4')
const imageCount = ref(6)
const generating = ref(false)
const generated = ref(false)
const loadingStep = ref(0)
const xhsImageUrl = ref('')
const xhsLastImageUrl = ref('')
const xhsHistoryImageUrl = ref('')
const xhsHistoryItem = ref<HistoryItem | null>(null)
const xhsGenerationId = ref<number | null>(null)
const xhsTaskId = ref<number | null>(null)
const xhsRequestStartedAt = ref<number | null>(null)
const xhsImageDebugStage = ref('')
const xhsImageDebugItemId = ref<number | null>(null)
const xhsImageDebugError = ref('')
const toast = ref('')
const customTopic = ref('')
let loadingTimer: ReturnType<typeof setInterval> | null = null
let xhsPollTimer: ReturnType<typeof window.setTimeout> | null = null

watch(
  () => ({
    selectedTopics: [...selectedTopics.value],
    selectedTone: selectedTone.value,
    style: style.value,
    note: note.value,
    withImage: withImage.value,
    imageSize: imageSize.value,
    imageCount: imageCount.value,
    customTopic: customTopic.value,
  }),
  () => persistState(),
  { deep: true },
)

// ====== 输出内容 ======
const title = ref('')
const titleOptions = ref<string[]>([])
const body = ref('')
const tags = ref<string[]>([])
const coverText = ref('')
const imageSuggestions = ref<string[]>([])
const publishTips = ref('')

const bodyCount = computed(() => body.value.length)
const debugXhsImageState = computed(() => ({
  generationId: xhsGenerationId.value,
  detailItemId: xhsImageDebugItemId.value,
  stage: xhsImageDebugStage.value,
  error: xhsImageDebugError.value,
  historyItemImageUrl: historyImageUrl(xhsHistoryItem.value),
  historyImageUrl: xhsHistoryImageUrl.value,
  streamImageUrl: xhsImageUrl.value,
  previewUrl: displayXhsPreviewUrl.value,
}))

function reportXhsImageState(stage: string, extra: Partial<{ itemId: number | null; error: string }> = {}) {
  xhsImageDebugStage.value = stage
  if (extra.itemId !== undefined) xhsImageDebugItemId.value = extra.itemId
  if (extra.error !== undefined) {
    const keepsSpecificError = xhsImageDebugError.value && /no usable signed image/i.test(extra.error || '')
    if (!keepsSpecificError) xhsImageDebugError.value = extra.error
  }
  const snapshot = {
    stage,
    generationId: xhsGenerationId.value,
    detailItemId: xhsImageDebugItemId.value,
    error: xhsImageDebugError.value,
    historyItemImageUrl: historyImageUrl(xhsHistoryItem.value),
    historyImageUrl: xhsHistoryImageUrl.value,
    streamImageUrl: xhsImageUrl.value,
    previewUrl: displayXhsPreviewUrl.value,
  }
  console.info('[xhs-image-debug]', snapshot)
}

// 正文+标签合并，一键复制用
const bodyWithTags = computed(() => {
  if (!tags.value.length) return body.value
  const tagStr = tags.value.map(t => '#' + t).join(' ')
  return body.value + '\n\n' + tagStr
})

const displayXhsImageUrl = computed(() => firstUsableImageUrl(
  historyImageUrl(xhsHistoryItem.value),
  xhsHistoryImageUrl.value,
))
const displayXhsPreviewUrl = computed(() => toRenderableImageUrl(displayXhsImageUrl.value))

function firstUsableImageUrl(...urls: string[]) {
  for (const url of urls) {
    const normalized = normalizeImageUrl(url)
    if (normalized && isUsableSignedImageUrl(normalized)) return normalized
  }
  return ''
}

function resolveAssetUrl(url: string) {
  if (!url) return ''
  if (/^(https?:|data:|blob:)/i.test(url)) return url
  return new URL(url, import.meta.env.VITE_API_BASE || window.location.origin).toString()
}

function toRenderableImageUrl(url: string) {
  const normalized = normalizeImageUrl(url)
  if (!normalized) return ''
  if (/^(https?:|data:|blob:|\/)/i.test(normalized)) return normalized
  if (/^(api|uploads?|files?|storage)\//i.test(normalized)) {
    return `/${normalized.replace(/^\/+/, '')}`
  }
  return resolveAssetUrl(normalized)
}

function isTemporaryUnsignedImageUrl(url: string) {
  const normalized = normalizeImageUrl(url)
  return isSiliconFlowTemporaryImageUrl(normalized) && !/[?&]X-Amz-Signature=/i.test(normalized)
}

function isSiliconFlowTemporaryImageUrl(url: string) {
  return /s3\.siliconflow\.cn\/(?:temporary|t)\/outputs\//i.test(url)
}

function isUsableSignedImageUrl(url: string) {
  const normalized = normalizeImageUrl(url)
  if (!normalized) return false
  if (!isSiliconFlowTemporaryImageUrl(normalized)) return true
  return /[?&]X-Amz-Signature=[0-9a-f]{32,}/i.test(normalized)
    && /[?&]X-Amz-Security-Token=[^&#\s]+/i.test(normalized)
    && /[?&]X-Amz-Credential=[^&#\s]*(?:aws4_request|aws4%5[fF]request)/i.test(normalized)
}

function shouldFetchWithAuth(_url: string) {
  return false
}


function persistState() {
  saveAiPageState(pageStateKey, {
    selectedTopics: [...selectedTopics.value],
    selectedTone: selectedTone.value,
    style: style.value,
    note: note.value,
    withImage: withImage.value,
    imageSize: imageSize.value,
    imageCount: imageCount.value,
    customTopic: customTopic.value,
    generating: generating.value,
    generated: generated.value,
    title: title.value,
    titleOptions: titleOptions.value,
    body: body.value,
    tags: tags.value,
    coverText: coverText.value,
    imageSuggestions: imageSuggestions.value,
    publishTips: publishTips.value,
    xhsImageUrl: xhsImageUrl.value,
    xhsLastImageUrl: xhsLastImageUrl.value,
    xhsHistoryImageUrl: xhsHistoryImageUrl.value,
    xhsGenerationId: xhsGenerationId.value,
    xhsTaskId: xhsTaskId.value,
  })
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (restored && Array.isArray(restored.selectedTopics)) {
    selectedTopics.value = new Set(restored.selectedTopics)
  }
  if (restored) {
    selectedTone.value = restored.selectedTone || selectedTone.value
    style.value = restored.style || style.value
    note.value = restored.note || note.value
    withImage.value = restored.withImage ?? withImage.value
    imageSize.value = restored.imageSize || imageSize.value
    imageCount.value = restored.imageCount || imageCount.value
    customTopic.value = restored.customTopic || customTopic.value
    generating.value = Boolean(restored.generating && restored.xhsTaskId)
    title.value = restored.title || ''
    titleOptions.value = Array.isArray(restored.titleOptions) ? restored.titleOptions : []
    body.value = restored.body || ''
    tags.value = Array.isArray(restored.tags) ? restored.tags : []
    coverText.value = restored.coverText || ''
    imageSuggestions.value = Array.isArray(restored.imageSuggestions) ? restored.imageSuggestions : []
    publishTips.value = restored.publishTips || ''
    xhsImageUrl.value = ''
    xhsLastImageUrl.value = ''
    xhsHistoryImageUrl.value = isUsableSignedImageUrl(restored.xhsHistoryImageUrl)
      ? (restored.xhsHistoryImageUrl || '')
      : ''
    xhsGenerationId.value = Number(restored.xhsGenerationId || 0) || null
    xhsTaskId.value = Number(restored.xhsTaskId || 0) || null
    generated.value = Boolean(restored.generated && (title.value || body.value || displayXhsImageUrl.value))
  }
  applyStrategyReuseDraft()
}

function applyStrategyReuseDraft() {
  const draft = loadAiPageState<any>('strategy-reuse:xhs')
  if (!draft?.content) return
  customTopic.value = draft.title || customTopic.value || '策略复用'
  note.value = draft.content
  selectedTopics.value = new Set(['escape'])
  selectedTone.value = 'deal'
  persistState()
}

function toggleTopic(v: string) {
  const s = new Set(selectedTopics.value)
  if (s.has(v)) s.delete(v); else s.add(v)
  selectedTopics.value = s
  persistState()
}

function isTopicOn(v: string) { return selectedTopics.value.has(v) }

// ====== 封面建议 ======
const coverAdvice = computed(() => {
  const vals = [...selectedTopics.value]
  if (vals.includes('rain')) return '雨天竹林大片（趁薄雾拍）'
  if (vals.includes('festival')) return '节日氛围布景图'
  if (vals.includes('couple')) return '情侣打卡双人照'
  return '民宿环境全景图'
})

const pubTip = computed(() => {
  const vals = [...selectedTopics.value]
  if (vals.includes('rain')) return '雨天主题适合突出室内体验、环境氛围和真实可用服务，发布前请核对天气。'
  if (vals.includes('festival')) return '🎋 已选择节日氛围，请结合实际活动时间、价格和库存发布。'
  return '建议根据账号历史活跃时段安排发布，并使用 ' + imageCount.value + ' 张真实门店图片完整呈现体验。'
})

// ====== 生成 ======
async function generate() {
  if (!selectedTopics.value.size) return

  if (xhsPollTimer) {
    clearTimeout(xhsPollTimer)
    xhsPollTimer = null
  }
  generating.value = true
  generated.value = false
  xhsRequestStartedAt.value = Date.now()
  loadingStep.value = 0
  title.value = ''
  titleOptions.value = []
  body.value = ''
  tags.value = []
  coverText.value = ''
  imageSuggestions.value = []
  publishTips.value = ''
  xhsImageUrl.value = ''
  xhsLastImageUrl.value = ''
  xhsHistoryImageUrl.value = ''
  xhsHistoryItem.value = null
  xhsGenerationId.value = null
  xhsTaskId.value = null
  xhsImageDebugItemId.value = null
  xhsImageDebugError.value = ''
  reportXhsImageState('generate:start', { itemId: null, error: '' })
  persistState()

  const steps = ['读取已确认酒店资料', '匹配内容主题与方向', '撰写小红书图文']
  loadingTimer = setInterval(() => {
    if (loadingStep.value < steps.length) {
      loadingStep.value++
    } else { if (loadingTimer) clearInterval(loadingTimer) }
  }, 800)
  let failed = false
  let generationId: number | null = null

  try {
    const vals = [...selectedTopics.value]
    if (withImage.value) {
      generationId = await generateXhsWithHistoryTask(vals)
    } else {
    const content = await collectStreamContent('xhs', buildContentAiParams(hotel, 'xhs', {
      topics: vals.join(','),
      theme: vals.join(','),
      message: customTopic.value || vals.join(','),
      customTopic: customTopic.value,
      tone: selectedTone.value,
      style: style.value,
      note: note.value,
      withImage: withImage.value,
      imageSize: imageSize.value,
      imageCount: imageCount.value,
      outputFormat: 'json',
      schema: {
        titles: ['标题1', '标题2', '标题3'],
        body: '正文',
        tags: ['话题标签'],
        coverText: '封面文字',
        imageSuggestions: ['配图建议'],
        publishTips: '发布建议',
      },
    }), {
      onDone(payload) {
        const id = resolveGenerationId(payload)
        generationId = Number.isFinite(id) && id > 0 ? id : null
        xhsGenerationId.value = generationId
        persistState()
      },
      onChunk(_chunk, content) {
        if (applyXhsResult(content, true)) {
          generated.value = true
          persistState()
        }
      },
    })

    applyXhsResult(content)
    if (generationId) {
      xhsGenerationId.value = generationId
      await syncXhsImageFromGeneration(generationId)
      reportXhsImageState('generate:after-detail-sync')
    }
    if (!displayXhsImageUrl.value) {
      await syncLatestXhsImageFromHistory()
      reportXhsImageState('generate:after-latest-sync')
    }
    }
    if (!String(body.value || title.value || displayXhsImageUrl.value).trim()) {
      failed = true
    }
    persistState()
  } catch {
    failed = true
    flashToast('AI调用失败，请稍后重试')
  }

  if (loadingTimer) clearInterval(loadingTimer)
  loadingStep.value = steps.length
  generating.value = false
  generated.value = Boolean(title.value || body.value || displayXhsImageUrl.value)
  persistState()
  if (failed || !generated.value) {
    const recovered = generationId
      ? await syncXhsImageFromGeneration(generationId)
      : await syncLatestXhsImageFromHistory()
    if (!recovered && !displayXhsImageUrl.value) {
      await syncLatestXhsImageFromHistory()
      reportXhsImageState('generate:recovery-latest-sync')
    }
    generated.value = Boolean(title.value || body.value || displayXhsImageUrl.value)
    persistState()
    if (!generated.value) {
      flashToast('AI调用失败，请稍后重试')
    }
  }
}

function buildXhsGenerateParams(vals = [...selectedTopics.value]) {
  return buildContentAiParams(hotel, 'xhs', {
    topics: vals.join(','),
    theme: vals.join(','),
    message: customTopic.value || vals.join(','),
    customTopic: customTopic.value,
    tone: selectedTone.value,
    style: style.value,
    note: note.value,
    withImage: withImage.value,
    imageSize: imageSize.value,
    imageCount: imageCount.value,
    outputFormat: 'json',
    schema: {
      titles: ['标题1', '标题2', '标题3'],
      body: '正文',
      tags: ['话题标签'],
      coverText: '封面文字',
      imageSuggestions: ['配图建议'],
      publishTips: '发布建议',
    },
  })
}

async function generateXhsWithHistoryTask(vals: string[]) {
  const { data: res } = await generateContent('xhs', buildXhsGenerateParams(vals))
  const payload = res?.data || res
  const directId = resolveGenerationId(payload)
  xhsTaskId.value = Number(payload?.taskId || payload?.task_id || payload?.id || 0) || null
  xhsGenerationId.value = Number.isFinite(directId) && directId > 0 ? directId : null
  persistState()

  if (!xhsTaskId.value) {
    if (xhsGenerationId.value && await syncXhsImageFromGeneration(xhsGenerationId.value)) {
      reportXhsImageState('task:direct-history-sync')
      return xhsGenerationId.value
    }
    throw new Error('xhs task id missing')
  }

  await pollXhsTaskUntilDone()
  return xhsGenerationId.value
}

async function pollXhsTaskUntilDone() {
  const startedAt = Date.now()
  while (xhsTaskId.value && Date.now() - startedAt < 5 * 60 * 1000) {
    const done = await pollXhsTaskOnce()
    if (done) return true
    await sleep(1800)
  }
  throw new Error('xhs task timeout')
}

async function pollXhsTaskOnce() {
  if (!xhsTaskId.value) return true
  const { data: res } = await getTaskResult(xhsTaskId.value)
  const task = res?.data || res
  const id = resolveGenerationId(task)
  if (Number.isFinite(id) && id > 0) {
    xhsGenerationId.value = id
  }

  const content = xhsTaskContent(task)
  if (content && !isGenericFailureText(content)) {
    applyXhsResult(content)
  }

  if (isTaskDone(task?.status)) {
    if (xhsGenerationId.value && await syncXhsImageFromGeneration(xhsGenerationId.value)) {
      xhsTaskId.value = null
      reportXhsImageState('task:history-detail-sync')
      return true
    }
    if (await syncLatestXhsImageFromHistory()) {
      xhsTaskId.value = null
      reportXhsImageState('task:history-list-sync')
      return true
    }
    xhsTaskId.value = null
    reportXhsImageState('task:done-no-image', { itemId: xhsGenerationId.value, error: 'task done but history detail has no image' })
    return true
  }

  if (isTaskFailed(task?.status)) {
    if (xhsGenerationId.value && await syncXhsImageFromGeneration(xhsGenerationId.value)) {
      xhsTaskId.value = null
      return true
    }
    throw new Error(task?.errorMsg || task?.error || 'xhs task failed')
  }

  persistState()
  return false
}

function xhsTaskContent(task: any) {
  return String(
    task?.content
      || task?.result
      || task?.text
      || task?.answer
      || task?.output
      || task?.data?.content
      || task?.data?.result
      || task?.data?.text
      || task?.data?.answer
      || ''
  )
}

function isTaskDone(status: unknown) {
  return ['done', 'success', 'succeeded', 'completed', 'complete'].includes(String(status || '').toLowerCase())
}

function isTaskFailed(status: unknown) {
  return ['failed', 'fail', 'error'].includes(String(status || '').toLowerCase())
}

function isGenericFailureText(value?: string) {
  return /(?:AI\s*)?(调用失败|生成失败)|请稍后重试|timeout|Network Error/i.test(String(value || ''))
}

function sleep(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

function applyXhsResult(raw: string, streaming = false) {
  if (streaming && isPartialJsonStream(raw)) return false
  const parsed = parseXhsResult(raw)
  if (parsed.title) title.value = parsed.title
  titleOptions.value = parsed.titles
  body.value = parsed.body
  tags.value = parsed.tags
  coverText.value = parsed.coverText
  imageSuggestions.value = parsed.imageSuggestions
  publishTips.value = parsed.publishTips
  if (parsed.imageError) {
    xhsImageDebugError.value = parsed.imageError
  }
  return Boolean(parsed.title || parsed.body || parsed.tags.length || parsed.imageUrl || parsed.imageSuggestions.length)
}

function resolveGenerationId(payload: Record<string, any>) {
  return Number(
    payload?.generationId
      || payload?.historyId
      || payload?.id
      || payload?.data?.generationId
      || payload?.data?.historyId
      || payload?.data?.id
      || payload?.outputs?.generationId
      || payload?.outputs?.historyId
      || payload?.outputs?.id
      || 0
  )
}

function parseXhsResult(raw: string) {
  const imageUrl = extractImageUrl(raw)
  const payload = parseJsonLike(raw)
  const unwrapped = unwrapPayload(payload)
  if (typeof unwrapped === 'string') {
    return normalizeXhsText(unwrapped, [], imageUrl)
  }
  const source = isRecord(unwrapped) ? unwrapped : null

  if (!source) {
    return normalizeXhsText(raw, [], imageUrl)
  }

  const titles = Array.isArray(source.titles) ? source.titles.map(String).filter(Boolean) : []
  const titleText = String(source.title || source.headline || titles[0] || '').trim()
  const bodyText = String(source.body || source.text || source.content || source.answer || source.output || source.summary || '').trim()
  const explicitTags = Array.isArray(source.tags) ? source.tags.map(String) : []
  const explicitImage = firstImageUrl(source) || imageUrl
  const promptSuggestion = String(source.image_prompt || source.imagePrompt || source.imageSuggestion || source.image_suggestion || '').trim()
  const explicitSuggestions = Array.isArray(source.imageSuggestions)
    ? source.imageSuggestions.map(String).filter(Boolean)
    : Array.isArray(source.image_suggestions)
      ? source.image_suggestions.map(String).filter(Boolean)
      : (promptSuggestion ? [promptSuggestion] : [])

  return {
    ...normalizeXhsText(bodyText || raw, explicitTags, explicitImage),
    title: titleText,
    titles,
    coverText: String(source.coverText || source.cover_text || '').trim(),
    imageSuggestions: explicitSuggestions,
    publishTips: String(source.publishTips || source.publish_tips || '').trim(),
    imageError: String(source.imageError || source.image_error || source.imageErrorCode || source.image_error_code || '').trim(),
  }
}

function normalizeXhsText(raw: string, explicitTags: string[], imageUrl: string) {
  let text = toPlainTextWithoutImageMarkdown(raw)
  if (imageUrl) {
    text = text.split(imageUrl).join('')
  }
  text = text
    .replace(/"imageUrl"\s*:\s*"[^"]*"/gi, '')
    .replace(/"imageUrls"\s*:\s*\[[\s\S]*?\]/gi, '')
    .trim()

  const trailingTags = [...text.matchAll(/#[^\s#]+/g)].map(match => match[0].replace(/^#/, ''))
  text = text.replace(/(?:\s*#[^\s#]+)+\s*$/g, '').trim()

  return {
    title: '',
    titles: [],
    body: text,
    tags: uniqueTags([...explicitTags, ...trailingTags]),
    imageUrl,
    coverText: '',
    imageSuggestions: [],
    publishTips: '',
    imageError: '',
  }
}

function uniqueTags(values: string[]) {
  const seen = new Set<string>()
  const result: string[] = []
  for (const value of values) {
    const tag = String(value || '').replace(/^#+/, '').trim()
    if (!tag || seen.has(tag)) continue
    seen.add(tag)
    result.push(tag)
  }
  return result
}

function parseJsonLike(raw: string): unknown {
  const value = String(raw || '').trim()
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch {
    const match = value.match(/\{[\s\S]*\}/)
    if (!match) return null
    try {
      return JSON.parse(match[0])
    } catch {
      return null
    }
  }
}

function unwrapPayload(value: unknown, depth = 0): unknown {
  if (!isRecord(value) || depth > 5) return value
  if (hasXhsFields(value)) return value

  const candidates = [
    value.data,
    isRecord(value.data) ? value.data.outputs : null,
    value.outputs,
    value.result,
    value.output,
    value.content,
    value.text,
    value.answer,
  ]

  for (const candidate of candidates) {
    if (!candidate) continue
    const parsed = typeof candidate === 'string' ? parseJsonLike(candidate) || candidate : candidate
    const unwrapped = unwrapPayload(parsed, depth + 1)
    if (hasXhsFields(unwrapped) || typeof unwrapped === 'string') return unwrapped
  }

  return value
}

function hasXhsFields(value: unknown) {
  return isRecord(value) && (
    'titles' in value
    || 'title' in value
    || 'body' in value
    || 'tags' in value
    || 'coverText' in value
    || 'cover_text' in value
    || 'imageUrl' in value
    || 'imageUrls' in value
    || 'image_url' in value
    || 'image_urls' in value
    || 'image_prompt' in value
    || 'publishTips' in value
    || 'publish_tips' in value
  )
}

function firstImageUrl(value: unknown) {
  if (isRecord(value) && Array.isArray(value.imageUrls)) {
    for (const item of value.imageUrls) {
      const found = extractImageUrl(String(item || ''))
      if (found) return found
    }
  }
  if (isRecord(value) && Array.isArray(value.image_urls)) {
    for (const item of value.image_urls) {
      const found = extractImageUrl(String(item || ''))
      if (found) return found
    }
  }
  if (isRecord(value) && typeof value.image_url === 'string') {
    const found = extractImageUrl(value.image_url)
    if (found) return found
  }
  return extractImageUrl(typeof value === 'string' ? value : JSON.stringify(value || ''))
}

function isPartialJsonStream(raw: string) {
  const value = String(raw || '').trim()
  if (!value.startsWith('{') && !value.startsWith('```json')) return false
  return !parseJsonLike(value)
}

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

// ====== 复制 ======
async function syncXhsImageFromGeneration(generationId: number) {
  try {
    const { data } = await getGenerationHistoryDetail(generationId, 'xhs')
    const item = (data?.data || data) as HistoryItem
    if (!item) {
      reportXhsImageState('history-detail-empty', { itemId: generationId, error: 'detail response empty' })
      return false
    }
    xhsHistoryItem.value = item
    xhsGenerationId.value = item.id || generationId
    xhsImageDebugItemId.value = item.id || generationId

    if (item.outputContent) {
      applyXhsResult(item.outputContent)
    }
    const image = historyImageUrl(item)
    if (image && !isTemporaryUnsignedImageUrl(image)) {
      xhsLastImageUrl.value = image
      xhsHistoryImageUrl.value = image
      generated.value = Boolean(title.value || body.value || image)
      persistState()
      reportXhsImageState('history-detail-sync', { itemId: item.id || generationId, error: '' })
      return true
    }
    reportXhsImageState('history-detail-no-image', {
      itemId: item.id || generationId,
      error: image ? 'detail image is temporary unsigned url' : 'detail has no image',
    })
  } catch (error: any) {
    reportXhsImageState('history-detail-error', {
      itemId: generationId,
      error: error?.response?.data?.message || error?.message || 'detail request failed',
    })
    // Fall back to the recent-history lookup below.
  }
  return false
}

async function syncLatestXhsImageFromHistory() {
  try {
    const { data } = await getGenerationHistory('xhs', 20)
    const items = (data?.data || data || []) as HistoryItem[]
    const preferred = items.filter(item => isUsableXhsHistory(item, true))
    const fallback = items.filter(item => isUsableXhsHistory(item, false))
    const candidates = [...preferred, ...fallback.filter(item => !preferred.some(preferredItem => preferredItem.id === item.id))]

    for (const candidate of candidates) {
      if (!candidate?.id) continue
      reportXhsImageState('history-list-match', { itemId: candidate.id, error: '' })
      const restored = await syncXhsImageFromGeneration(candidate.id)
      if (restored) return true

      const image = historyImageUrl(candidate)
      if (image && !isTemporaryUnsignedImageUrl(image)) {
        xhsHistoryItem.value = candidate
        xhsGenerationId.value = candidate.id
        xhsLastImageUrl.value = image
        xhsHistoryImageUrl.value = image
        generated.value = Boolean(title.value || body.value || image)
        persistState()
        reportXhsImageState('history-list-sync', { itemId: candidate.id, error: '' })
        return true
      }
    }
    reportXhsImageState('history-list-no-image', { itemId: null, error: 'no usable signed image found in recent history' })
  } catch (error: any) {
    reportXhsImageState('history-list-error', {
      itemId: null,
      error: error?.response?.data?.message || error?.message || 'list request failed',
    })
    // Ignore history sync failure and keep current preview.
  }
  return false
}

function isUsableXhsHistory(item?: HistoryItem | null, preferCurrent = false) {
  if (!item || item.moduleKey !== 'xhs') return false
  const status = String(item.status || '').toLowerCase()
  if (!['success', 'done', 'completed', 'complete'].includes(status)) return false
  if (preferCurrent && xhsRequestStartedAt.value && historyTime(item.createdAt) < xhsRequestStartedAt.value - 10000) return false
  return Boolean(item.id)
}

function historyTime(value?: string) {
  if (!value) return 0
  const parsed = Date.parse(String(value).replace(' ', 'T'))
  return Number.isFinite(parsed) ? parsed : 0
}

async function copyBodyWithTags() {
  const ok = await copyTextToClipboard(bodyWithTags.value)
  flashToast(ok ? '文案已复制' : '复制失败')
}
async function copyComplete() {
  const full = `${title.value}\n\n${bodyWithTags.value}`
  const ok = await copyTextToClipboard(full)
  flashToast(ok ? '完整图文已复制' : '复制失败')
}

async function openXhsImage() {
  const imageUrl = displayXhsImageUrl.value
  if (!imageUrl) return

  try {
    window.open(imageUrl, '_blank', 'noopener,noreferrer')
    return
    const resolved = resolveAssetUrl(imageUrl)
    if (!shouldFetchWithAuth(resolved)) {
      window.open(resolved, '_blank', 'noopener,noreferrer')
      return
    }

    const token = localStorage.getItem('hotel_access_token')
    const response = await fetch(resolved, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || '打开图片失败')
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    window.open(blobUrl, '_blank', 'noopener,noreferrer')
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60000)
  } catch (error: any) {
    flashToast(error?.message || '打开图片失败')
  }
}

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1500)
}
</script>

<template>
  <div class="h-full flex flex-col">
    <!-- Toast -->
    <div v-if="toast" class="fixed top-6 right-6 z-50 flex items-center gap-2 rounded-xl border border-rose-200 bg-white/95 px-4 py-3 text-sm font-medium text-rose-600 shadow-2xl backdrop-blur">
      <CheckCircle2 class="h-4 w-4 shrink-0" />
      <span>{{ toast }}</span>
    </div>

    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-start gap-3">
        <Instagram class="w-5 h-5 text-rose-500 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">小红书图文</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">标题 × 3 · 正文 · 话题标签 · 发布建议，一次生成完整笔记</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <button @click="router.push('/history/xhs')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <FileText class="w-3 h-3" />生成记录
        </button>
        <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <Zap class="w-3 h-3" />算力流水
        </button>
        <span v-if="!credits.loading && !credits.error" class="text-[10px] font-medium bg-bamboo-50 text-bamboo-800 px-2.5 py-1 rounded-full flex items-center gap-1">
          <Zap class="w-3 h-3" />{{ credits.currentBalance }} 算力
        </span>
        <span v-else class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2 py-1 rounded-full">
          算力余额不可用
        </span>
      </div>
    </div>

    <!-- Body: Two Columns -->
    <div class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- ========== 左栏：配置 ========== -->
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto flex flex-col gap-3">
        <!-- 上下文卡片 -->
        <div class="bg-cream-50 rounded-lg px-3 py-2.5 border border-cream-200/60">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">本店资料</div>
          <div class="space-y-1.5">
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500 flex items-center gap-1"><Building2 class="w-3 h-3" />酒店</span>
              <span class="font-medium text-bamboo-950">{{ hotel.config.name }}</span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500">资料范围</span>
              <span class="font-medium text-bamboo-700">{{ hotel.config.city || '未设置城市' }} · {{ hotel.roomTypes.length }} 个房型</span>
            </div>
          </div>
        </div>

        <!-- 内容主题 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">内容主题（可多选）</div>
          <div class="grid grid-cols-2 gap-1.5">
            <button
              v-for="t in topics" :key="t.val"
              @click="toggleTopic(t.val)"
              :class="[
                'px-2 py-1.5 rounded-md text-[11px] transition-colors border text-center',
                isTopicOn(t.val)
                  ? 'bg-rose-50 border-rose-400 text-rose-600 font-medium'
                  : 'bg-white border-cream-200 text-warm-600 hover:border-rose-300 hover:text-rose-500'
              ]"
            >
              {{ t.label }}
            </button>
          </div>
          <div class="mt-1.5 rounded-lg border border-cream-300 bg-white focus-within:border-rose-400">
            <div class="flex justify-end border-b border-cream-200 px-1.5 py-0.5"><AiPolishControl :source-text="customTopic" scene="xhs" field="customTopic" @accept="customTopic = $event" /></div>
            <input v-model="customTopic" type="text" placeholder="或自定义输入主题..." class="w-full bg-transparent px-2.5 py-2 text-[12px] text-bamboo-950 outline-none placeholder:text-warm-400" />
          </div>
        </div>

        <!-- 内容方向 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">内容方向</div>
          <div class="flex gap-1.5 flex-wrap">
            <button
              v-for="t in tones" :key="t.val"
              @click="selectedTone = t.val"
              :class="[
                'px-2.5 py-1 rounded-full text-[11px] transition-colors border',
                selectedTone === t.val
                  ? 'bg-rose-50 border-rose-400 text-rose-600 font-medium'
                  : 'bg-white border-cream-200 text-warm-600 hover:border-rose-300'
              ]"
            >
              {{ t.label }}
            </button>
          </div>
        </div>

        <!-- 写作风格 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">写作风格</div>
          <select v-model="style" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-rose-400">
            <option value="warm">治愈温暖（主流种草风）</option>
            <option value="young">活泼元气（年轻客群）</option>
            <option value="luxury">轻奢精致（高端调性）</option>
            <option value="story">故事叙事（沉浸体验）</option>
          </select>
        </div>

        <!-- 是否配图 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">是否配图</div>
          <div class="flex gap-1.5">
            <button @click="withImage = true" :class="['px-2.5 py-1 rounded-full text-[11px] transition-colors border', withImage ? 'bg-rose-400 text-white border-rose-400' : 'bg-white text-warm-500 border-cream-300 hover:border-rose-300']">
              <Image class="w-3 h-3 inline mr-1" />配图
            </button>
            <button @click="withImage = false" :class="['px-2.5 py-1 rounded-full text-[11px] transition-colors border', !withImage ? 'bg-rose-400 text-white border-rose-400' : 'bg-white text-warm-500 border-cream-300 hover:border-rose-300']">
              纯文字
            </button>
          </div>
        </div>

        <!-- 图片尺寸 -->
        <div v-if="withImage">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">图片尺寸</div>
          <div class="flex gap-1.5 flex-wrap">
            <button v-for="s in [{v:'1:1',l:'1:1 方形'},{v:'4:3',l:'4:3 横图'},{v:'3:4',l:'3:4 竖图'},{v:'16:9',l:'16:9 宽屏'}]" :key="s.v"
              @click="imageSize = s.v"
              :class="['px-2.5 py-1 rounded-full text-[11px] transition-colors border', imageSize === s.v ? 'bg-rose-400 text-white border-rose-400' : 'bg-white text-warm-500 border-cream-300 hover:border-rose-300']">
              {{ s.l }}
            </button>
          </div>
        </div>

        <!-- 图片数量 -->
        <div v-if="withImage">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">图片数量</div>
          <div class="flex gap-1.5">
            <button v-for="n in [1,3,6,9]" :key="n"
              @click="imageCount = n"
              :class="['px-3 py-1 rounded-full text-[11px] transition-colors border', imageCount === n ? 'bg-rose-400 text-white border-rose-400' : 'bg-white text-warm-500 border-cream-300 hover:border-rose-300']">
              {{ n }} 张
            </button>
          </div>
        </div>

        <!-- 额外备注 -->
        <div>
          <div class="mb-1.5 flex items-center justify-between">
            <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">额外备注 <span class="font-normal normal-case text-warm-400">（选填）</span></div>
            <AiPolishControl :source-text="note" scene="xhs" field="note" @accept="note = $event" />
          </div>
          <textarea v-model="note" rows="2" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-rose-400 resize-none" placeholder="今天竹林有薄雾效果很好、刚换了新早餐菜单..." />
        </div>

        <!-- 提示 -->
        <div class="border-l-3 border-rose-400 bg-rose-50 rounded-r-lg p-2.5 text-[11px] text-rose-800 leading-relaxed">
          <strong>发布检查：</strong>优先使用真实门店图片；标题、正文和标签保持一致；涉及设施、价格、距离和活动时以已确认资料为准。
        </div>

        <!-- 生成按钮 -->
        <button @click="generate" :disabled="generating || !selectedTopics.size"
          class="w-full py-2.5 rounded-lg bg-rose-400 text-white text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-rose-500 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors mt-auto">
          <Sparkles class="w-4 h-4" />AI 生成小红书图文
        </button>
      </div>

      <!-- ========== 右栏：输出 ========== -->
      <div class="bg-cream-50 overflow-y-auto flex flex-col">
        <!-- 空状态 -->
        <div v-if="!generating && !generated" class="flex flex-col items-center justify-center flex-1 min-h-[400px] gap-3 text-warm-500">
          <Instagram class="w-9 h-9 opacity-25" />
          <p class="text-[13px]">选择主题后点击生成</p>
          <p class="text-[11px] opacity-70 text-center">AI 将结合已录入酒店资料、已确认知识和主题设置<br>一次生成完整图文笔记</p>
        </div>

        <!-- Loading -->
        <div v-if="generating && !generated" class="xhs-generation-loading">
          <div class="xhs-loading-orb">
            <Loader2 class="w-7 h-7 text-rose-400 animate-spin" />
          </div>
          <p class="text-sm font-semibold text-bamboo-900">AI 正在生成小红书图文</p>
          <div class="space-y-1.5">
            <div v-for="(s, i) in ['读取已确认酒店资料','匹配内容主题与方向','撰写小红书图文']" :key="i"
              :class="['text-[11px] flex items-center gap-2 transition-colors', i < loadingStep ? 'text-rose-400' : i === loadingStep ? 'text-rose-500' : 'text-warm-400']">
              <CheckCircle2 v-if="i < loadingStep" class="w-3 h-3 text-rose-400" />
              <Loader2 v-else-if="i === loadingStep" class="w-3 h-3 animate-spin" />
              <span v-else class="w-3 h-3 rounded-full border border-cream-300" />
              {{ s }}
            </div>
          </div>
        </div>

        <!-- 输出区 -->
        <div v-if="generated" class="flex-1 overflow-auto p-4">
          <div class="grid min-h-[calc(100vh-250px)] items-stretch gap-3 xl:grid-cols-[minmax(0,1fr)_320px]">
            <div class="flex min-w-0 flex-col gap-3">
              <div class="rounded-lg border border-cream-200 bg-white p-3">
                <div class="mb-2 flex items-center justify-between gap-3">
                  <div class="min-w-0">
                    <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">标题</div>
                    <div class="mt-1 truncate text-[15px] font-semibold text-bamboo-950">{{ title || '未生成标题' }}</div>
                  </div>
                  <button @click="copyComplete" class="flex shrink-0 items-center gap-1.5 rounded-lg bg-rose-400 px-3 py-1.5 text-[12px] text-white transition-colors hover:bg-rose-500">
                    <Copy class="h-3.5 w-3.5" />复制完整图文
                  </button>
                </div>
                <div v-if="titleOptions.length > 1" class="flex flex-wrap gap-1.5">
                  <button
                    v-for="option in titleOptions"
                    :key="option"
                    @click="title = option; persistState()"
                    :class="['max-w-full truncate rounded-full border px-2 py-0.5 text-[10px] transition-colors', option === title ? 'border-rose-300 bg-rose-50 text-rose-600' : 'border-cream-200 bg-cream-50 text-warm-600 hover:border-rose-200']"
                  >{{ option }}</button>
                </div>
              </div>

              <div class="flex flex-1 flex-col rounded-lg border border-cream-200 bg-white p-3">
                <div class="mb-2 flex items-center justify-between gap-3">
                  <div>
                    <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">正文</div>
                    <div class="mt-0.5 text-[10px] text-warm-500">{{ bodyCount }} 字，标签已单独拆分</div>
                  </div>
                  <button @click="copyBodyWithTags" class="flex items-center gap-1 rounded-md border border-cream-300 bg-cream-50 px-2 py-1 text-[11px] text-warm-600 transition-colors hover:border-rose-300 hover:bg-rose-50 hover:text-rose-600">
                    <Copy class="h-3 w-3" />复制正文
                  </button>
                </div>
                <textarea
                  v-model="body"
                  rows="22"
                  readonly
                  class="min-h-[680px] w-full flex-1 resize-none rounded-lg border border-cream-200 bg-cream-50 px-3 py-2.5 text-[13px] leading-7 text-bamboo-950 outline-none"
                />
                <div v-if="tags.length" class="mt-2 flex flex-wrap gap-1.5">
                  <span v-for="tag in tags" :key="tag" class="rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-[10px] text-rose-500">#{{ tag }}</span>
                </div>
              </div>
            </div>

            <aside class="min-w-0 space-y-3">
              <div v-if="withImage" class="rounded-lg border border-cream-200 bg-white p-3">
                <div class="mb-2 flex items-center justify-between">
                  <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">配图预览</div>
                  <span class="text-[10px] text-warm-500">{{ imageSize }}</span>
                </div>
                <div :class="[
                  'mx-auto overflow-hidden rounded-lg border border-cream-200 bg-cream-100',
                  imageSize === '1:1' ? 'aspect-square max-w-[260px]' :
                  imageSize === '4:3' ? 'aspect-[4/3] max-w-[300px]' :
                  imageSize === '3:4' ? 'aspect-[3/4] max-w-[240px]' :
                  'aspect-video max-w-[300px]'
                ]">
                  <img v-if="displayXhsPreviewUrl" :src="displayXhsPreviewUrl" class="h-full w-full object-cover" />
                  <div v-else class="flex h-full w-full flex-col items-center justify-center gap-1 text-warm-400">
                    <Image class="h-6 w-6" />
                    <span class="text-[10px]">暂未返回图片</span>
                  </div>
                </div>
                <button v-if="displayXhsImageUrl" @click="openXhsImage" class="mt-2 flex w-full items-center justify-center gap-1 rounded-lg border border-cream-300 bg-cream-50 px-2 py-1.5 text-[11px] text-warm-600 hover:border-rose-300 hover:bg-rose-50 hover:text-rose-600">
                  <Download class="h-3 w-3" />打开图片
                </button>
              </div>

              <div class="rounded-lg border border-cream-200 bg-white p-3">
                <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">封面文字</div>
                <div class="rounded-lg bg-cream-50 p-2 text-[12px] leading-5 text-bamboo-950">{{ coverText || '暂无封面文字' }}</div>
              </div>

              <div class="rounded-lg border border-cream-200 bg-white p-3">
                <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">配图建议</div>
                <div v-if="imageSuggestions.length" class="space-y-1.5">
                  <div v-for="(item, index) in imageSuggestions" :key="index" class="rounded-md bg-cream-50 px-2 py-1.5 text-[11px] leading-5 text-warm-700">{{ item }}</div>
                </div>
                <div v-else class="rounded-md bg-cream-50 px-2 py-1.5 text-[11px] text-warm-500">暂无配图建议</div>
              </div>

              <div class="rounded-lg border border-cream-200 bg-white p-3">
                <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">发布建议</div>
                <div class="rounded-lg bg-cream-50 p-2 text-[11px] leading-5 text-warm-700">{{ publishTips || pubTip }}</div>
              </div>
            </aside>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.xhs-generation-loading {
  display: flex;
  min-height: 520px;
  height: 100%;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.85rem;
  text-align: center;
}

.xhs-loading-orb {
  display: flex;
  height: 3.8rem;
  width: 3.8rem;
  align-items: center;
  justify-content: center;
  border-radius: 1rem;
  background: #fff1f2;
  box-shadow: inset 0 0 0 1px #fecdd3;
}
</style>
