<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Image, Sparkles, Zap, Upload, Download, Copy, FileText,
  ImagePlus, Wand2, Loader2, X
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { collectStreamContent, generateContent, getTaskResult } from '@/api/content'
import { getGenerationHistoryDetail } from '@/api/history'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { extractImageUrl } from '@/utils/aiContentRender'
import { copyTextToClipboard } from '@/utils/clipboard'
import { buildContentAiParams } from '@/utils/aiContextParams'
import { imageUrl as historyImageUrl, resultText as historyResultText, type HistoryItem } from '@/utils/generationHistory'

const hotel = useHotelStore()
const router = useRouter()
const pageStateKey = 'poster'

const mode = ref<'text2img' | 'beautify'>('text2img')
const isText2imgMode = computed(() => mode.value === 'text2img')
const isBeautifyMode = computed(() => mode.value === 'beautify')

// ====== 文生图 ======
const t2iTheme = ref('')
const t2iContent = ref('')
const t2iStyle = ref('conversion')
const t2iScene = ref('promo')
const t2iPlatform = ref('xhs')
const t2iAudience = ref('family')
const t2iTextDensity = ref('balanced')
const t2iCta = ref('')
const t2iSellingPoint = ref('')
const t2iGenerating = ref(false)
const t2iGenerated = ref(false)
const t2iImageUrl = ref('')
const t2iResultText = ref('')
const t2iError = ref('')
const t2iTaskId = ref<number | null>(null)
const t2iGenerationId = ref<number | null>(null)
const t2iHistoryItem = ref<HistoryItem | null>(null)
let t2iPollTimer: number | null = null
const t2iPreviewImageUrl = computed(() => extractImageUrl(t2iImageUrl.value) || t2iImageUrl.value)
const t2iHasOutput = computed(() => Boolean(t2iPreviewImageUrl.value || t2iResultText.value))
const t2iDisplayText = computed(() => t2iResultText.value)

const t2iStyles = [
  { v: 'conversion', label: '转化促销' },
  { v: 'brand', label: '品牌质感' },
  { v: 'lifestyle', label: '生活方式' },
  { v: 'festival', label: '节日活动' },
  { v: 'premium', label: '高端静奢' },
  { v: 'warm', label: '温暖治愈' },
]

const t2iScenes = [
  { v: 'promo', label: '活动促销' },
  { v: 'room', label: '房型推广' },
  { v: 'season', label: '节气假期' },
  { v: 'brand', label: '品牌形象' },
]

const t2iPlatforms = [
  { v: 'xhs', label: '小红书' },
  { v: 'wechat', label: '朋友圈' },
  { v: 'mp', label: '公众号头图' },
  { v: 'ota', label: 'OTA物料' },
]

const t2iAudiences = [
  { v: 'family', label: '亲子家庭' },
  { v: 'couple', label: '情侣度假' },
  { v: 'business', label: '商务客' },
  { v: 'friends', label: '朋友出游' },
]

const t2iTextDensities = [
  { v: 'minimal', label: '少文字' },
  { v: 'balanced', label: '标题+卖点' },
  { v: 'full', label: '信息完整' },
]

const t2iSizes = [
  { v: '3:4', label: '3:4 竖版' },
  { v: '1:1', label: '1:1 方形' },
  { v: '16:9', label: '16:9 横版' },
]
const t2iSize = ref('3:4')

function sizeToDimensions(size: string) {
  const map: Record<string, { width: number; height: number }> = {
    '1:1': { width: 1024, height: 1024 },
    '3:4': { width: 768, height: 1024 },
    '4:3': { width: 1024, height: 768 },
    '9:16': { width: 768, height: 1344 },
    '16:9': { width: 1344, height: 768 },
  }
  return map[size] || map['3:4']
}

// ====== AI 润色 ======
const polishOpen = ref(false)
const polishGenerating = ref(false)
const polishTarget = ref<'theme' | 'content' | 'beautify'>('content')
const polishSource = ref('')
const polishDraft = ref('')
const polishError = ref('')

// ====== 图片美化 ======
const beautifyDone = ref(false)
const uploadedFile = ref<File | null>(null)
const uploadedPreview = ref('')
const beautifyDesc = ref('')
const beautifySize = ref('3:4')
const beautifyGenerating = ref(false)
const beautifyResult = ref('')

function persistState() {
  saveAiPageState(pageStateKey, {
    mode: mode.value,
    t2iTheme: t2iTheme.value,
    t2iContent: t2iContent.value,
    t2iStyle: t2iStyle.value,
    t2iScene: t2iScene.value,
    t2iPlatform: t2iPlatform.value,
    t2iAudience: t2iAudience.value,
    t2iTextDensity: t2iTextDensity.value,
    t2iCta: t2iCta.value,
    t2iSellingPoint: t2iSellingPoint.value,
    t2iSize: t2iSize.value,
    t2iGenerated: t2iGenerated.value,
    t2iImageUrl: t2iImageUrl.value,
    t2iResultText: t2iResultText.value,
    t2iGenerationId: t2iGenerationId.value,
    beautifyDesc: beautifyDesc.value,
    beautifySize: beautifySize.value,
    beautifyDone: beautifyDone.value,
    beautifyResult: beautifyResult.value,
    t2iTaskId: t2iTaskId.value,
    t2iGenerating: t2iGenerating.value,
  })
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (restored) {
    mode.value = restored.mode || mode.value
    t2iTheme.value = restored.t2iTheme || t2iTheme.value
    t2iContent.value = restored.t2iContent || t2iContent.value
    t2iStyle.value = restored.t2iStyle || t2iStyle.value
    t2iScene.value = restored.t2iScene || t2iScene.value
    t2iPlatform.value = restored.t2iPlatform || t2iPlatform.value
    t2iAudience.value = restored.t2iAudience || t2iAudience.value
    t2iTextDensity.value = restored.t2iTextDensity || t2iTextDensity.value
    t2iCta.value = restored.t2iCta || t2iCta.value
    t2iSellingPoint.value = restored.t2iSellingPoint || t2iSellingPoint.value
    t2iSize.value = restored.t2iSize || t2iSize.value
    t2iImageUrl.value = restored.t2iImageUrl || ''
    t2iResultText.value = restored.t2iResultText || ''
    if (isGenericFailureText(t2iResultText.value) && !t2iImageUrl.value) {
      t2iResultText.value = ''
    }
    t2iTaskId.value = restored.t2iTaskId || null
    t2iGenerationId.value = restored.t2iGenerationId || null
    t2iGenerating.value = Boolean(restored.t2iGenerating && t2iTaskId.value && !t2iImageUrl.value && !t2iResultText.value)
    t2iGenerated.value = Boolean(restored.t2iGenerated && (t2iPreviewImageUrl.value || t2iResultText.value))
    beautifyDesc.value = restored.beautifyDesc || beautifyDesc.value
    beautifySize.value = restored.beautifySize || beautifySize.value
    beautifyResult.value = restored.beautifyResult || ''
    beautifyDone.value = Boolean(restored.beautifyDone && beautifyResult.value)
  }
  applyStrategyReuseDraft()
}

function applyStrategyReuseDraft() {
  const draft = loadAiPageState<any>('strategy-reuse:poster')
  if (!draft?.content) return
  mode.value = 'text2img'
  t2iTheme.value = draft.title || '策略营销海报'
  t2iContent.value = draft.content
  t2iStyle.value = 'conversion'
  t2iScene.value = 'promo'
  persistState()
}

function selectT2iStyle(v: string) {
  t2iStyle.value = v
  persistState()
}

function hasPosterOutput() {
  return t2iHasOutput.value
}

// ====== 文生图 ======
async function generateT2I() {
  if (!t2iTheme.value.trim()) return
  t2iGenerating.value = true
  t2iGenerated.value = false
  t2iImageUrl.value = ''
  t2iResultText.value = ''
  t2iError.value = ''
  t2iTaskId.value = null
  t2iGenerationId.value = null
  t2iHistoryItem.value = null
  persistState()

  try {
    const dimensions = sizeToDimensions(t2iSize.value)
    const { data: res } = await generateContent('poster', buildContentAiParams(hotel, 'poster', {
      mode: 'text2img',
      theme: t2iTheme.value,
      message: t2iContent.value || t2iTheme.value,
      poster_theme: t2iTheme.value,
      content: t2iContent.value,
      style: t2iStyle.value,
      visual_style: t2iStyle.value,
      scene: t2iScene.value,
      posterScene: t2iScene.value,
      platform: t2iPlatform.value,
      targetAudience: t2iAudience.value,
      audience: t2iAudience.value,
      textDensity: t2iTextDensity.value,
      cta: t2iCta.value,
      sellingPoint: t2iSellingPoint.value,
      size: t2iSize.value,
      imageSize: t2iSize.value,
      width: dimensions.width,
      height: dimensions.height,
      outputFormat: 'json',
      schema: {
        imageUrl: '生成后的图片链接',
        prompt: '实际生图提示词',
      },
    }))
    const payload = res.data || res
    if (applyDirectPosterPayload(payload)) {
      return
    }
    t2iTaskId.value = Number(payload.taskId || payload.id)
    t2iGenerationId.value = Number(payload.generationId || payload.generation_id || 0) || null
    if (!t2iTaskId.value) throw new Error('生成任务创建失败')
    persistState()
    pollT2iTask()
  } catch (err: any) {
    if (tryApplyPosterResultFromError(err)) return
    if (await syncPosterHistory()) return
    t2iError.value = err.message || '生成失败'
    t2iGenerating.value = false
    persistState()
  }
}

async function requestPolish(target: 'theme' | 'content' | 'beautify') {
  const source = target === 'theme'
    ? t2iTheme.value
    : target === 'beautify'
      ? beautifyDesc.value
      : t2iContent.value
  if (!source.trim()) {
    flashToast('先输入需要润色的内容')
    return
  }
  polishTarget.value = target
  polishSource.value = source
  polishDraft.value = ''
  polishError.value = ''
  polishOpen.value = true
  polishGenerating.value = true

  try {
    const content = await collectStreamContent('polish', buildContentAiParams(hotel, 'polish', {
      scene: target === 'beautify' ? 'poster_beautify' : 'poster_text2img',
      field: target,
      sourceText: source,
      message: source,
      theme: t2iTheme.value,
      platform: t2iPlatform.value,
      targetAudience: t2iAudience.value,
      style: t2iStyle.value,
      textDensity: t2iTextDensity.value,
      purpose: '润色为更适合酒店营销海报的中文表达，只返回润色后的文本',
    }), {
      onChunk(_chunk, text) {
        polishDraft.value = stripThink(text)
      },
    })
    polishDraft.value = stripThink(content)
  } catch (err: any) {
    if (tryApplyPosterResultFromError(err)) return
    polishError.value = err?.message || '润色失败'
  } finally {
    polishGenerating.value = false
  }
}

function acceptPolish() {
  if (!polishDraft.value.trim()) return
  if (polishTarget.value === 'theme') {
    t2iTheme.value = polishDraft.value.trim()
  } else if (polishTarget.value === 'beautify') {
    beautifyDesc.value = polishDraft.value.trim()
  } else {
    t2iContent.value = polishDraft.value.trim()
  }
  polishOpen.value = false
  persistState()
}

function closePolish() {
  polishOpen.value = false
}

async function pollT2iTask() {
  if (!t2iTaskId.value) return
  if (t2iPollTimer) clearTimeout(t2iPollTimer)
  t2iGenerating.value = true
  t2iGenerated.value = false
  t2iError.value = ''
  persistState()

  try {
    const { data: res } = await getTaskResult(t2iTaskId.value)
    const task = res.data || res
    if (task?.generationId || task?.generation_id) {
      t2iGenerationId.value = Number(task.generationId || task.generation_id) || t2iGenerationId.value
    }
    const resultContent = taskPosterContent(task)
    const resultIsFailureText = isGenericFailureText(resultContent)
    if (resultContent && !resultIsFailureText) {
      applyPosterResult(resultContent)
      if (hasPosterOutput()) {
        t2iGenerating.value = false
        t2iGenerated.value = true
        t2iTaskId.value = null
        t2iError.value = ''
        persistState()
        return
      }
    }
    if (isPosterTaskDone(task.status)) {
      if (await syncPosterHistory(task) && t2iPreviewImageUrl.value) {
        return
      }
      if (resultContent && !resultIsFailureText) {
        applyPosterResult(resultContent)
      }
      if (!hasPosterOutput() && await syncPosterHistory(task)) {
        return
      }
      t2iGenerating.value = false
      t2iGenerated.value = hasPosterOutput()
      t2iTaskId.value = null
      t2iError.value = t2iGenerated.value ? '' : '未识别到海报图片，请到生成记录查看'
      persistState()
      return
    }
    if (isPosterTaskFailed(task.status)) {
      if (await syncPosterHistory(task)) return
      throw new Error('生成失败，请稍后重试')
    }
    t2iPollTimer = window.setTimeout(pollT2iTask, 1800)
  } catch (err: any) {
    if (tryApplyPosterResultFromError(err)) return
    if (await syncPosterHistory()) return
    t2iGenerating.value = false
    t2iError.value = '生成失败，请稍后重试'
    persistState()
  }
}

async function syncPosterHistory(task?: any) {
  const generationId = Number(task?.generationId || task?.generation_id || t2iGenerationId.value || 0)
  if (!generationId) return false
  try {
    const { data } = await getGenerationHistoryDetail(generationId, 'poster')
    const history = data?.data || data
    return applyPosterHistory(history)
  } catch {
    return false
  }
}

function applyPosterHistory(history: any) {
  if (!history || history.moduleKey !== 'poster') return false
  const image = historyImageUrl(history)
  const text = historyResultText(history)
  if (!image && (!text || isGenericFailureText(text))) return false
  t2iHistoryItem.value = history
  if (image) {
    t2iImageUrl.value = image
    t2iResultText.value = ''
  } else {
    t2iImageUrl.value = ''
    t2iResultText.value = text
  }
  t2iGenerating.value = false
  t2iGenerated.value = true
  t2iTaskId.value = null
  t2iGenerationId.value = Number(history.id || 0) || t2iGenerationId.value
  t2iError.value = ''
  persistState()
  return true
}
function isGenericFailureText(value?: string) {
  return /(?:AI\s*)?(调用失败|生成失败)|请稍后重试|timeout|Network Error/i.test(String(value || ''))
}

function isPosterTaskDone(status: unknown) {
  return ['done', 'success', 'succeeded', 'completed', 'complete'].includes(String(status || '').toLowerCase())
}

function isPosterTaskFailed(status: unknown) {
  return ['failed', 'fail', 'error'].includes(String(status || '').toLowerCase())
}

function taskPosterContent(task: any) {
  const currentImageUrl = extractImageUrl(typeof task === 'string' ? task : JSON.stringify(task || ''))
  if (currentImageUrl) return currentImageUrl

  const direct = task?.content
    || task?.text
    || task?.answer
    || task?.outputContent
    || task?.output_content
    || task?.data?.content
    || task?.data?.text
    || task?.data?.answer
    || task?.data?.outputs?.output
    || task?.data?.outputs?.content
    || task?.outputs?.output
    || task?.outputs?.content
    || task?.outputAssets
    || task?.output_assets
    || task?.assets
    || task?.result
    || task?.data
  if (typeof direct === 'string' && direct.trim()) return direct
  if (direct && typeof direct === 'object') return JSON.stringify(direct)
  return ''
}

function applyDirectPosterPayload(payload: any) {
  const content = taskPosterContent(payload)
  if (!content) return false
  applyPosterResult(content)
  if (!hasPosterOutput()) return false
  t2iGenerating.value = false
  t2iGenerated.value = true
  t2iTaskId.value = null
  t2iError.value = ''
  persistState()
  return true
}

function tryApplyPosterResultFromError(err: any) {
  const message = err?.response?.data?.message
    || err?.response?.data?.data?.message
    || err?.response?.data?.content
    || err?.response?.data?.data?.content
    || err?.message
    || ''
  const payload = message || err?.response?.data || err
  if (!payload) return false
  const raw = typeof payload === 'string' ? payload : JSON.stringify(payload)
  if (isGenericFailureText(raw) && !extractImageUrl(raw)) return false
  applyPosterResult(raw)
  if (isGenericFailureText(t2iResultText.value) && !t2iPreviewImageUrl.value) {
    t2iResultText.value = ''
    t2iGenerated.value = false
    return false
  }
  if (hasPosterOutput()) {
    t2iGenerating.value = false
    t2iGenerated.value = true
    t2iTaskId.value = null
    t2iError.value = ''
    persistState()
    return true
  }
  return false
}

function applyPosterResult(raw: string) {
  const cleaned = stripThink(raw)
  let parsed: any = null
  try {
    parsed = JSON.parse(cleaned)
  } catch {
    const match = cleaned.match(/\{[\s\S]*\}/)
    if (match) {
      try { parsed = JSON.parse(match[0]) } catch { parsed = null }
    }
  }

  const candidate = typeof parsed === 'object' && parsed
    ? parsed.imageUrl
      || parsed.image_url
      || parsed.url
      || parsed.posterUrl
      || parsed.poster_url
      || parsed.output_image
      || parsed.outputImage
      || parsed.image
      || parsed.markdown
      || parsed.content
      || parsed.body
      || parsed.text
      || parsed.output
    : cleaned

  const imageUrl = extractImageUrl(typeof candidate === 'string' ? candidate : JSON.stringify(candidate || parsed || cleaned))
    || extractImageUrl(typeof parsed === 'object' && parsed ? JSON.stringify(parsed) : cleaned)
  if (imageUrl) {
    t2iHistoryItem.value = null
    t2iImageUrl.value = imageUrl
    t2iResultText.value = ''
    t2iGenerated.value = true
    t2iError.value = ''
    persistState()
    return
  }

  const resultText = typeof parsed === 'object' && parsed
    ? JSON.stringify(parsed, null, 2)
    : cleaned
  if (isGenericFailureText(resultText)) {
    t2iImageUrl.value = ''
    t2iResultText.value = ''
    t2iGenerated.value = false
    t2iError.value = resultText || '生成失败，请稍后重试'
    persistState()
    return
  }
  t2iImageUrl.value = ''
  t2iResultText.value = resultText
  t2iGenerated.value = Boolean(resultText)
  if (resultText) {
    t2iError.value = ''
  }
  persistState()
}

function stripThink(text: string): string {
  return String(text || '')
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/^\s*```(?:json)?/i, '')
    .replace(/```\s*$/i, '')
    .trim()
}

// ====== 图片上传 ======
function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadedFile.value = file
  beautifyDone.value = false
  beautifyResult.value = ''
  persistState()

  const reader = new FileReader()
  reader.onload = (ev) => {
    uploadedPreview.value = ev.target?.result as string
  }
  reader.readAsDataURL(file)
}

function triggerUpload() {
  document.getElementById('beautify-file-input')?.click()
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  const file = e.dataTransfer?.files?.[0]
  if (!file) return
  const input = document.getElementById('beautify-file-input') as HTMLInputElement
  if (!input) return
  const dt = new DataTransfer()
  dt.items.add(file)
  input.files = dt.files
  handleFileChange({ target: input } as unknown as Event)
}

// ====== 开始美化 ======
async function startBeautify() {
  if (!uploadedFile.value || !uploadedPreview.value) return
  beautifyGenerating.value = true
  beautifyResult.value = ''

  try {
    const dimensions = sizeToDimensions(beautifySize.value)
    const content = await collectStreamContent('poster', buildContentAiParams(hotel, 'poster', {
      mode: 'beautify',
      imageData: uploadedPreview.value,
      prompt: beautifyDesc.value,
      theme: beautifyDesc.value || '图片美化',
      message: beautifyDesc.value || '根据上传图片进行营销图片美化',
      content: beautifyDesc.value,
      size: beautifySize.value,
      imageSize: beautifySize.value,
      width: dimensions.width,
      height: dimensions.height,
      outputFormat: 'json',
      schema: {
        imageUrl: '美化后的图片链接',
        prompt: '美化提示词',
      },
    }), {
      onChunk(_chunk, text) {
        beautifyResult.value = text
        beautifyDone.value = true
        persistState()
      },
    })
    beautifyResult.value = stripThink(content)
    beautifyDone.value = true
    persistState()
  } catch (err: any) {
    flashToast('生成失败，请稍后重试')
    beautifyDone.value = false
  } finally {
    beautifyGenerating.value = false
    persistState()
  }
}

// ====== 复制/下载 ======
const toast = ref('')

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1500)
}

async function copyT2iResult() {
  if (!t2iDisplayText.value) return
  await copyTextToClipboard(t2iDisplayText.value)
  flashToast('已复制')
}

async function copyT2iImageUrl() {
  if (!t2iPreviewImageUrl.value) return
  await copyTextToClipboard(t2iPreviewImageUrl.value)
  flashToast('图片链接已复制')
}

onMounted(() => {
  restoreState()
  if (t2iTaskId.value && t2iGenerating.value) {
    pollT2iTask()
  }
})
</script>

<template>
  <div class="h-full flex flex-col">
    <div v-if="toast" class="fixed top-6 right-6 z-50 bg-bamboo-800 text-cream-100 px-5 py-3 rounded-lg shadow-lg text-sm">
      {{ toast }}
    </div>

    <div class="flex items-center justify-between mb-4">
      <div class="flex items-start gap-3">
        <Image class="w-5 h-5 text-bamboo-700 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">营销海报</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">文生图 / 图片美化</p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <button @click="router.push('/history/poster')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <FileText class="w-3 h-3" />生成记录
        </button>
        <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <Zap class="w-3 h-3" />算力流水
        </button>
      </div>
    </div>

    <div class="flex items-center gap-2 mb-4">
      <div class="flex gap-1 bg-cream-100 rounded-lg p-0.5 self-start">
        <button @click="mode = 'text2img'"
          :class="['flex items-center gap-1.5 px-4 py-1.5 rounded-md text-xs font-medium transition-colors', isText2imgMode ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-warm-700']">
          <Sparkles class="w-3.5 h-3.5" />文生图
        </button>
        <button @click="mode = 'beautify'"
          :class="['flex items-center gap-1.5 px-4 py-1.5 rounded-md text-xs font-medium transition-colors', isBeautifyMode ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-warm-700']">
          <Wand2 class="w-3.5 h-3.5" />图片美化
        </button>
      </div>
      <span v-if="isText2imgMode" class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2 py-1 rounded-full">消耗 30 算力 / 次</span>
      <span v-if="isBeautifyMode" class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2 py-1 rounded-full">消耗 20 算力 / 次</span>
    </div>

    <div v-if="isText2imgMode" class="poster-workspace flex-1 min-h-0 grid gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto space-y-4">
        <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase">生成设置</div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">用途场景</div>
          <div class="grid grid-cols-2 gap-1.5">
            <button v-for="s in t2iScenes" :key="s.v" @click="t2iScene = s.v; persistState()"
              :class="['px-3 py-2 rounded-lg text-[11px] border text-center transition-colors', t2iScene === s.v ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-800 font-medium' : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-300']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">投放平台</div>
          <div class="grid grid-cols-2 gap-1.5">
            <button v-for="s in t2iPlatforms" :key="s.v" @click="t2iPlatform = s.v; persistState()"
              :class="['px-3 py-2 rounded-lg text-[11px] border text-center transition-colors', t2iPlatform === s.v ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-800 font-medium' : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-300']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">目标客群</div>
          <div class="grid grid-cols-2 gap-1.5">
            <button v-for="s in t2iAudiences" :key="s.v" @click="t2iAudience = s.v; persistState()"
              :class="['px-3 py-2 rounded-lg text-[11px] border text-center transition-colors', t2iAudience === s.v ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-800 font-medium' : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-300']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">视觉方向</div>
          <div class="grid grid-cols-2 gap-1.5">
            <button v-for="s in t2iStyles" :key="s.v" @click="selectT2iStyle(s.v)"
              :class="['px-3 py-2 rounded-lg text-[11px] border text-center transition-colors', t2iStyle === s.v ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-800 font-medium' : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-300']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">文字密度</div>
          <div class="grid grid-cols-3 gap-1.5">
            <button v-for="s in t2iTextDensities" :key="s.v" @click="t2iTextDensity = s.v; persistState()"
              :class="['px-2 py-1.5 rounded-lg text-[11px] border transition-colors', t2iTextDensity === s.v ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">图片尺寸</div>
          <div class="flex flex-wrap gap-1.5">
            <button v-for="s in t2iSizes" :key="s.v" @click="t2iSize = s.v; persistState()"
              :class="['px-3 py-1 rounded-full text-[11px] border transition-colors', t2iSize === s.v ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <div class="flex items-center justify-between mb-1.5">
            <div class="text-[11px] font-medium text-warm-600">海报主题</div>
            <button @click="requestPolish('theme')" class="text-[10px] px-2 py-0.5 rounded-full border border-cream-300 bg-white text-bamboo-700 hover:bg-bamboo-50 transition-colors flex items-center gap-1">
              <Wand2 class="w-3 h-3" />AI 润色
            </button>
          </div>
          <input v-model="t2iTheme" type="text" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400" placeholder="输入海报主题，如：周末亲子房限时预订" @blur="persistState" />
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">核心卖点</div>
          <input v-model="t2iSellingPoint" type="text" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400" placeholder="如：近景区、亲子活动、山景房、免费早餐" @blur="persistState" />
        </div>

        <div>
          <div class="flex items-center justify-between mb-1.5">
            <div class="text-[11px] font-medium text-warm-600">海报内容</div>
            <button @click="requestPolish('content')" class="text-[10px] px-2 py-0.5 rounded-full border border-cream-300 bg-white text-bamboo-700 hover:bg-bamboo-50 transition-colors flex items-center gap-1">
              <Wand2 class="w-3 h-3" />AI 润色
            </button>
          </div>
          <textarea v-model="t2iContent" rows="4" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400 resize-none" placeholder="输入海报副标题、活动规则、氛围或限制条件..." @blur="persistState" />
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">行动按钮文案</div>
          <input v-model="t2iCta" type="text" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400" placeholder="如：立即预订 / 私信领取 / 周末出发" @blur="persistState" />
        </div>

        <button @click="generateT2I" :disabled="t2iGenerating || !t2iTheme.trim()" class="w-full py-2.5 rounded-lg bg-bamboo-800 text-bamboo-100 text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors">
          <Sparkles class="w-4 h-4" />AI 生成海报
        </button>
      </div>

      <div class="poster-preview-pane bg-cream-50 p-4 overflow-y-auto flex items-center justify-center">
        <div v-if="t2iGenerating" class="poster-generation-loading">
          <div class="poster-loading-orb">
            <Loader2 class="w-7 h-7 text-bamboo-800 animate-spin" />
          </div>
          <p class="text-[12px] text-warm-500">AI 正在生成海报...</p>
          <p class="text-[10px] text-warm-400">图片生成约需 30-90 秒，请耐心等待</p>
        </div>

        <div v-else-if="t2iPreviewImageUrl" class="w-full max-w-[min(760px,100%)]">
          <div class="relative group mx-auto flex max-h-[calc(100vh-220px)] min-h-[360px] w-full items-center justify-center overflow-hidden rounded-xl border-2 border-cream-300 bg-white p-4 shadow-lg">
            <img :src="t2iPreviewImageUrl" class="block max-h-[calc(100vh-260px)] max-w-full rounded-lg object-contain" />
            <div class="absolute right-3 top-3 flex items-center gap-2">
              <button @click="copyT2iImageUrl" class="h-9 w-9 rounded-full bg-white/90 text-bamboo-900 shadow-lg border border-cream-200 backdrop-blur flex items-center justify-center hover:bg-white transition" title="复制图片链接">
                <Copy class="w-4 h-4" />
              </button>
              <a :href="t2iPreviewImageUrl" target="_blank" download class="h-9 w-9 rounded-full bg-bamboo-800 text-bamboo-100 shadow-lg border border-bamboo-700 flex items-center justify-center hover:bg-bamboo-900 transition" title="下载图片">
                <Download class="w-4 h-4" />
              </a>
            </div>
          </div>
        </div>

        <div v-else-if="t2iDisplayText" class="w-full max-w-2xl">
          <div class="rounded-xl border border-cream-300 bg-white p-4 shadow-sm">
            <div class="flex items-center justify-between gap-3 mb-3">
              <div>
                <div class="text-[13px] font-semibold text-bamboo-900">海报生成结果</div>
                <div class="text-[11px] text-warm-500 mt-0.5">当前返回的是文案或提示词，暂未识别到图片链接</div>
              </div>
              <button @click="copyT2iResult" class="text-[12px] px-3 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-700 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
                <Copy class="w-3.5 h-3.5" />复制
              </button>
            </div>
            <pre class="whitespace-pre-wrap text-[12px] leading-6 text-bamboo-950 bg-cream-50 border border-cream-200 rounded-lg p-3 max-h-[520px] overflow-auto">{{ t2iDisplayText }}</pre>
          </div>
        </div>

        <div v-else-if="t2iError" class="flex flex-col items-center gap-3 text-rose-500">
          <p class="text-[13px]">{{ t2iError }}</p>
          <button @click="generateT2I" class="text-[12px] px-4 py-1.5 rounded-lg border border-rose-200 text-rose-600 hover:bg-rose-50 transition-colors">重新生成</button>
        </div>

        <div v-else class="flex flex-col items-center gap-3 text-warm-400">
          <Image class="w-10 h-10 opacity-25" />
          <p class="text-[13px]">选择风格、输入主题后点击生成</p>
        </div>
      </div>
    </div>

    <div v-if="isBeautifyMode" class="poster-workspace flex-1 min-h-0 grid gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto space-y-4">
        <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase">美化设置</div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">上传图片</div>
          <input id="beautify-file-input" type="file" accept="image/*" class="hidden" @change="handleFileChange" />
          <div @click="triggerUpload" @dragover.prevent @drop="onDrop"
            :class="['border-2 border-dashed rounded-lg p-6 text-center cursor-pointer transition-colors', uploadedPreview ? 'border-bamboo-400 bg-bamboo-50/30' : 'border-cream-300 bg-cream-50 hover:border-bamboo-300 hover:bg-bamboo-50/20']">
            <div v-if="!uploadedPreview" class="flex flex-col items-center gap-2 text-warm-400">
              <Upload class="w-6 h-6" />
              <span class="text-[11px]">拖拽图片到此处</span>
              <span class="text-[10px]">或点击上传</span>
            </div>
            <div v-else class="flex items-center gap-3">
              <img :src="uploadedPreview" class="w-16 h-16 object-cover rounded-lg" />
              <div class="text-left flex-1 min-w-0">
                <div class="text-[11px] font-medium text-bamboo-800 truncate">{{ uploadedFile?.name }}</div>
                <div class="text-[10px] text-warm-500">已上传，可以开始美化</div>
              </div>
              <button @click.stop="uploadedPreview = ''; uploadedFile = null; beautifyDone = false" class="text-warm-400 hover:text-rose-500">
                <X class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        <div>
          <div class="flex items-center justify-between mb-1.5">
            <div class="text-[11px] font-medium text-warm-600">美化描述</div>
            <button @click="requestPolish('beautify')" class="text-[10px] px-2 py-0.5 rounded-full border border-cream-300 bg-white text-bamboo-700 hover:bg-bamboo-50 transition-colors flex items-center gap-1">
              <Wand2 class="w-3 h-3" />AI 润色
            </button>
          </div>
          <textarea v-model="beautifyDesc" rows="2" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400 resize-none" placeholder="描述想要的图片效果，如：提高亮度、增强绿色、暖色调..." />
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">图片尺寸</div>
          <div class="flex gap-1.5">
            <button v-for="s in t2iSizes" :key="s.v" @click="beautifySize = s.v"
              :class="['px-3 py-1 rounded-full text-[11px] border transition-colors', beautifySize === s.v ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <button @click="startBeautify" :disabled="beautifyGenerating || !uploadedFile" class="w-full py-2.5 rounded-lg bg-bamboo-800 text-bamboo-100 text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors">
          <Wand2 class="w-4 h-4" />AI 美化图片
        </button>
      </div>

      <div class="bg-cream-50 p-4 overflow-y-auto">
        <div v-if="beautifyGenerating && !beautifyDone" class="poster-generation-loading">
          <div class="poster-loading-orb">
            <Loader2 class="w-7 h-7 text-bamboo-800 animate-spin" />
          </div>
          <p class="text-[12px] text-warm-500">AI 正在美化图片...</p>
        </div>

        <div v-else-if="beautifyDone" class="space-y-4">
          <div>
            <div class="text-[10px] font-semibold text-warm-500 tracking-wider mb-2">原图</div>
            <img :src="uploadedPreview" class="w-full rounded-lg border border-cream-200" />
          </div>
          <div>
            <div class="text-[10px] font-semibold text-warm-500 tracking-wider mb-2">美化效果</div>
            <div class="relative rounded-lg border border-cream-200 overflow-hidden bg-white">
              <img :src="extractImageUrl(beautifyResult) || beautifyResult || uploadedPreview" class="w-full" />
            </div>
          </div>
          <div class="flex gap-2 justify-center">
            <button @click="flashToast('已复制')" class="text-[12px] px-3 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-700 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
              <Copy class="w-3.5 h-3.5" />复制图片
            </button>
            <a :href="extractImageUrl(beautifyResult) || beautifyResult || uploadedPreview" target="_blank" download class="text-[12px] px-3 py-1.5 rounded-lg bg-bamboo-800 text-bamboo-100 hover:bg-bamboo-900 transition-colors flex items-center gap-1.5">
              <Download class="w-3.5 h-3.5" />下载图片
            </a>
          </div>
        </div>

        <div v-else class="flex flex-col items-center justify-center h-full min-h-[360px] gap-3 text-warm-400">
          <ImagePlus class="w-10 h-10 opacity-25" />
          <p class="text-[13px]">上传图片后点击 AI 美化图片</p>
          <p class="text-[11px] opacity-70">AI 会根据描述优化色彩、构图与氛围</p>
        </div>
      </div>
    </div>

    <div v-if="polishOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-bamboo-950/35 px-4">
      <div class="w-full max-w-xl rounded-2xl border border-cream-200 bg-white shadow-2xl">
        <div class="flex items-center justify-between border-b border-cream-200 px-5 py-4">
          <div>
            <div class="text-[15px] font-semibold text-bamboo-950">AI 润色建议</div>
            <div class="mt-0.5 text-[11px] text-warm-500">确认后会替换当前输入框内容</div>
          </div>
          <button @click="closePolish" class="rounded-full p-1.5 text-warm-400 hover:bg-cream-100 hover:text-warm-700">
            <X class="h-4 w-4" />
          </button>
        </div>
        <div class="space-y-3 px-5 py-4">
          <div class="rounded-xl border border-cream-200 bg-cream-50 p-3">
            <div class="mb-1 text-[10px] font-semibold tracking-wider text-warm-500">原内容</div>
            <div class="max-h-28 overflow-auto whitespace-pre-wrap text-[12px] leading-5 text-warm-700">{{ polishSource }}</div>
          </div>
          <div class="rounded-xl border border-bamboo-100 bg-bamboo-50/40 p-3">
            <div class="mb-1 flex items-center justify-between">
              <div class="text-[10px] font-semibold tracking-wider text-bamboo-700">润色后</div>
              <Loader2 v-if="polishGenerating" class="h-3.5 w-3.5 animate-spin text-bamboo-700" />
            </div>
            <div v-if="polishError" class="text-[12px] text-rose-600">{{ polishError }}</div>
            <div v-else class="min-h-24 max-h-56 overflow-auto whitespace-pre-wrap text-[13px] leading-6 text-bamboo-950">
              {{ polishDraft || '正在润色...' }}
            </div>
          </div>
        </div>
        <div class="flex items-center justify-end gap-2 border-t border-cream-200 px-5 py-4">
          <button @click="closePolish" class="rounded-lg border border-cream-300 bg-white px-4 py-2 text-[12px] text-warm-700 hover:bg-cream-50">取消</button>
          <button @click="requestPolish(polishTarget)" :disabled="polishGenerating" class="rounded-lg border border-bamboo-200 bg-white px-4 py-2 text-[12px] text-bamboo-800 hover:bg-bamboo-50 disabled:opacity-60">重新生成</button>
          <button @click="acceptPolish" :disabled="polishGenerating || !polishDraft.trim()" class="rounded-lg bg-bamboo-800 px-4 py-2 text-[12px] font-medium text-bamboo-100 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400">采纳</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.poster-workspace {
  grid-template-columns: 340px minmax(0, 1fr);
}

.poster-preview-pane {
  min-width: 0;
}

.poster-generation-loading {
  display: flex;
  min-height: 520px;
  height: 100%;
  width: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.85rem;
  text-align: center;
}

.poster-loading-orb {
  display: flex;
  height: 3.8rem;
  width: 3.8rem;
  align-items: center;
  justify-content: center;
  border-radius: 1rem;
  background: #f2f8ee;
  box-shadow: inset 0 0 0 1px #d9e7ce;
}

@media (min-width: 1440px) {
  .poster-workspace {
    grid-template-columns: 380px minmax(0, 1fr);
  }
}

@media (max-width: 1180px) {
  .poster-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .poster-workspace > :first-child {
    border-right: 0;
    border-bottom: 1px solid #f0e7dc;
  }
}
</style>

