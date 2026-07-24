<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Mic, Zap, Copy, Loader2, CheckCircle2, FileText,
  Sparkles
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { collectStreamContent } from '@/api/content'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { buildContentAiParams } from '@/utils/aiContextParams'
import { extractDisplayText, parseJsonLike } from '@/utils/aiContentRender'
import AiPolishControl from '@/components/ai/AiPolishControl.vue'

const hotel = useHotelStore()
const router = useRouter()
const pageStateKey = 'video'

// ====== 状态 ======
const generating = ref(false)
const generated = ref(false)
const loadingStep = ref(0)
const toast = ref('')
let loadingTimer: ReturnType<typeof setInterval> | null = null

// ====== 配置 ======
// 创作视角
const selectedView = ref('商家老板')
const views = [
  { val: '商家老板', hint: '用老板本人口吻介绍自家店铺，文案适合「门店账号」或「老板个人账号」' },
  { val: '探店达人', hint: '以探店博主视角真实体验分享，内容更有可信度，适合达人合作或账号种草' },
  { val: '消费者', hint: '以真实住客口吻分享体验，强调真实感，适合UGC式内容和好评截图' },
  { val: '其他', hint: '自定义视角，AI将根据卖点自动匹配最适合的叙述角度' },
]

const viewHint = computed(() => views.find(v => v.val === selectedView.value)?.hint || '')

// 卖点
const sellingPoints = ref('')
const pointCount = computed(() => sellingPoints.value.length)

// 文案风格
const selectedStyle = ref('沉浸式体验')
const styles = [
  { icon: '🌿', val: '沉浸式体验' },
  { icon: '👯', val: '闺蜜式分享' },
  { icon: '📚', val: '知识分享' },
  { icon: '😄', val: '幽默搞笑' },
  { icon: '🔥', val: '薅羊毛型' },
  { icon: '🎯', val: '避坑指南' },
  { icon: '🎥', val: '轻松Vlog式' },
  { icon: '💫', val: '情绪共鸣' },
]

// 营销目的
const selectedGoal = ref('引流涨粉')
const goals = [
  { icon: '📈', val: '引流涨粉', desc: '增加主页关注，扩大账号影响力' },
  { icon: '🛒', val: '直接转化预订', desc: '引导私信/评论，推动立即下单' },
  { icon: '🌱', val: '品牌种草', desc: '提升民宿知名度，建立品牌形象' },
  { icon: '🎁', val: '活动推广', desc: '推广当前正在执行的限时活动' },
]

// 视频时长
const selectedDuration = ref('30')
const durations = [
  { val: '15', label: '⚡ 15秒', sub: '约60字' },
  { val: '30', label: '🎯 30秒', sub: '约150字' },
  { val: '60', label: '📖 60秒', sub: '约300字' },
]

// 生成条数
const generateCount = ref(3)

watch(
  () => ({
    selectedView: selectedView.value,
    sellingPoints: sellingPoints.value,
    selectedStyle: selectedStyle.value,
    selectedGoal: selectedGoal.value,
    selectedDuration: selectedDuration.value,
    generateCount: generateCount.value,
  }),
  () => persistState(),
  { deep: true },
)

// ====== 输出内容 ======
interface ScriptVersion {
  num: number
  label: string
  badge: string
  badgeClass: string
  text: string
  script?: VideoScriptData
  shots?: VideoShotData[]
  publishTips?: string
  bgm?: string
}

interface VideoScriptData {
  title: string
  hook: string
  spokenScript: string
  cta: string
  verifiedFacts: string[]
  toConfirm: string[]
}

interface VideoShotData {
  scene: string
  shot: string
  visual: string
  caption: string
  durationHint: string
}

interface VideoResultData {
  scripts: VideoScriptData[]
  shots: VideoShotData[]
  publishTips: string
  bgm: string
}

const versions = ref<ScriptVersion[]>([])

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function unwrapVideoPayload(value: unknown, depth = 0): unknown {
  if (depth > 5) return value
  if (typeof value === 'string') {
    const parsed = parseJsonLike(value)
    if (parsed && parsed !== value) return unwrapVideoPayload(parsed, depth + 1)
    return value
  }
  if (!isRecord(value)) return value

  if (Array.isArray(value.scripts) || value.script || value.text || value.content) {
    return value
  }

  const candidates = [value.data, value.outputs, value.result, value.output, value.content, value.text, value.answer]
  for (const candidate of candidates) {
    if (!candidate) continue
    const unwrapped = unwrapVideoPayload(candidate, depth + 1)
    if (unwrapped) return unwrapped
  }
  return value
}

function normalizeTextList(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.map(item => extractDisplayText(typeof item === 'string' ? item : JSON.stringify(item || ''))).filter(Boolean)
}

function cleanLooseValue(value: string) {
  return extractDisplayText(
    String(value || '')
      .replace(/\\"/g, '"')
      .replace(/\\n/g, '\n')
      .trim(),
  )
}

function extractLooseField(source: string, key: string) {
  const patterns = [
    new RegExp(`"${key}"\\s*:\\s*"([\\s\\S]*?)"\\s*,\\s*"`, 'i'),
    new RegExp(`"${key}"\\s*:\\s*"([\\s\\S]*?)"\\s*\\}`, 'i'),
    new RegExp(`"${key}"\\s*:\\s*"([\\s\\S]*?)"\\s*\\]`, 'i'),
  ]

  for (const pattern of patterns) {
    const match = source.match(pattern)
    if (match?.[1]) return cleanLooseValue(match[1])
  }
  return ''
}

function extractLooseArray(source: string, key: string) {
  const match = source.match(new RegExp(`"${key}"\\s*:\\s*\\[([\\s\\S]*?)\\]`, 'i'))
  if (!match?.[1]) return []
  return [...match[1].matchAll(/"([^"]+)"/g)]
    .map(item => cleanLooseValue(item[1]))
    .filter(Boolean)
}

function normalizeScriptData(item: unknown): VideoScriptData | null {
  if (typeof item === 'string') {
    const text = extractDisplayText(item)
    if (!text) return null
    return {
      title: '',
      hook: '',
      spokenScript: text,
      cta: '',
      verifiedFacts: [],
      toConfirm: [],
    }
  }
  if (!isRecord(item)) return null

  const script = {
    title: String(item.title || '').trim(),
    hook: String(item.hook || '').trim(),
    spokenScript: String(item.spokenScript || item.script || item.content || item.text || '').trim(),
    cta: String(item.cta || '').trim(),
    verifiedFacts: normalizeTextList(item.verifiedFacts),
    toConfirm: normalizeTextList(item.toConfirm),
  }

  if (!script.title && !script.hook && !script.spokenScript && !script.cta && !script.verifiedFacts.length && !script.toConfirm.length) {
    return null
  }
  return script
}

function normalizeShotData(item: unknown): VideoShotData | null {
  if (!isRecord(item)) return null
  const shot = {
    scene: String(item.scene || '').trim(),
    shot: String(item.shot || '').trim(),
    visual: String(item.visual || '').trim(),
    caption: String(item.caption || '').trim(),
    durationHint: String(item.durationHint || item.duration || '').trim(),
  }
  if (!shot.scene && !shot.shot && !shot.visual && !shot.caption && !shot.durationHint) return null
  return shot
}

function buildScriptText(script: VideoScriptData): string {
  return [
    script.title,
    script.hook,
    script.spokenScript,
    script.cta,
    script.verifiedFacts.length ? `已核实信息：\n${script.verifiedFacts.map(line => `- ${line}`).join('\n')}` : '',
    script.toConfirm.length ? `发布前确认：\n${script.toConfirm.map(line => `- ${line}`).join('\n')}` : '',
  ].filter(Boolean).join('\n\n').trim()
}

function parseVideoResultFromLooseJson(raw: string): VideoResultData | null {
  const source = String(raw || '')
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .trim()
  const fenced = source.match(/```(?:json)?\s*([\s\S]*?)```/i)?.[1]
  const cleaned = String(fenced || source)
    .replace(/^\s*```(?:json)?\s*/i, '')
    .replace(/\s*```\s*$/i, '')
    .trim()

  if (!/"scripts"\s*:/.test(cleaned) && !/"spokenScript"\s*:/.test(cleaned)) return null

  const script = normalizeScriptData({
    title: extractLooseField(cleaned, 'title'),
    hook: extractLooseField(cleaned, 'hook'),
    spokenScript: extractLooseField(cleaned, 'spokenScript'),
    cta: extractLooseField(cleaned, 'cta'),
    verifiedFacts: extractLooseArray(cleaned, 'verifiedFacts'),
    toConfirm: extractLooseArray(cleaned, 'toConfirm'),
  })

  const shotsSection = cleaned.match(/"shots"\s*:\s*\[([\s\S]*?)\]\s*,\s*"publishTips"/i)?.[1] || cleaned.match(/"shots"\s*:\s*\[([\s\S]*?)\]/i)?.[1] || ''
  const shotMatches = shotsSection ? [...shotsSection.matchAll(/\{([\s\S]*?)\}/g)] : []
  const shots = shotMatches.map(match => normalizeShotData({
    scene: extractLooseField(match[0], 'scene'),
    shot: extractLooseField(match[0], 'shot'),
    visual: extractLooseField(match[0], 'visual'),
    caption: extractLooseField(match[0], 'caption'),
    durationHint: extractLooseField(match[0], 'durationHint'),
  })).filter(Boolean) as VideoShotData[]

  const result = {
    scripts: script ? [script] : [],
    shots,
    publishTips: extractLooseField(cleaned, 'publishTips'),
    bgm: extractLooseField(cleaned, 'bgm'),
  }

  return result.scripts.length || result.shots.length || result.publishTips || result.bgm ? result : null
}

function parseVideoResult(raw: string): VideoResultData {
  const payload = unwrapVideoPayload(parseJsonLike(raw) || raw)
  const empty = { scripts: [], shots: [], publishTips: '', bgm: '' }

  if (Array.isArray(payload)) {
    return {
      ...empty,
      scripts: payload.map(normalizeScriptData).filter(Boolean) as VideoScriptData[],
    }
  }

  if (isRecord(payload)) {
    const scripts = Array.isArray(payload.scripts)
      ? payload.scripts.map(normalizeScriptData).filter(Boolean) as VideoScriptData[]
      : []
    const shots = Array.isArray(payload.shots)
      ? payload.shots.map(normalizeShotData).filter(Boolean) as VideoShotData[]
      : []
    const single = normalizeScriptData(payload)

    if (scripts.length || shots.length || single || payload.publishTips || payload.bgm) {
      return {
        scripts: scripts.length ? scripts : (single ? [single] : []),
        shots,
        publishTips: extractDisplayText(String(payload.publishTips || '')),
        bgm: extractDisplayText(String(payload.bgm || '')),
      }
    }
  }

  const looseResult = parseVideoResultFromLooseJson(raw)
  if (looseResult) return looseResult

  const plain = extractDisplayText(raw)
  if (!plain) return empty

  return {
    ...empty,
    scripts: [{
      title: '',
      hook: '',
      spokenScript: plain,
      cta: '',
      verifiedFacts: [],
      toConfirm: [],
    }],
  }
}

function buildVersion(script: VideoScriptData, result: VideoResultData, index: number, streaming = false): ScriptVersion {
  return {
    num: index + 1,
    label: streaming ? (index === 0 ? 'AI生成中' : `生成中版本${index + 1}`) : (index === 0 ? 'AI生成版' : `版本${index + 1}`),
    badge: streaming ? '流式输出' : (index === 0 ? '推荐' : '备选'),
    badgeClass: streaming ? 'bg-amber-50 text-amber-700' : (index === 0 ? 'bg-bamboo-50 text-bamboo-700' : 'bg-blue-50 text-blue-600'),
    text: buildScriptText(script),
    script,
    shots: result.shots,
    publishTips: result.publishTips,
    bgm: result.bgm,
  }
}

function looksLikeRawVideoPayload(value: string) {
  return /```(?:json)?|"\s*scripts\s*"|"\s*spokenScript\s*"|"\s*verifiedFacts\s*"/i.test(String(value || ''))
}

function hydrateVersion(version: ScriptVersion): ScriptVersion {
  const source = String(version.text || version.script?.spokenScript || '')
  if (!looksLikeRawVideoPayload(source)) return version

  const parsed = parseVideoResult(source)
  const script = parsed.scripts[0]
  if (!script) return version

  return {
    ...buildVersion(script, parsed, Math.max(0, Number(version.num || 1) - 1), version.badge === '流式输出'),
    num: version.num,
    label: version.label,
    badge: version.badge,
    badgeClass: version.badgeClass,
  }
}

function persistState() {
  saveAiPageState(pageStateKey, {
    selectedView: selectedView.value,
    sellingPoints: sellingPoints.value,
    selectedStyle: selectedStyle.value,
    selectedGoal: selectedGoal.value,
    selectedDuration: selectedDuration.value,
    generateCount: generateCount.value,
    generated: generated.value,
    versions: versions.value,
  })
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (restored) {
    selectedView.value = restored.selectedView || selectedView.value
    sellingPoints.value = restored.sellingPoints || sellingPoints.value
    selectedStyle.value = restored.selectedStyle || selectedStyle.value
    selectedGoal.value = restored.selectedGoal || selectedGoal.value
    selectedDuration.value = restored.selectedDuration || selectedDuration.value
    generateCount.value = restored.generateCount || generateCount.value
  }
  if (Array.isArray(restored?.versions)) {
    versions.value = restored.versions.map(hydrateVersion)
  }
  generated.value = Boolean(restored?.generated && versions.value.length)
  applyStrategyReuseDraft()
  if (versions.value.length) persistState()
}

function applyStrategyReuseDraft() {
  const draft = loadAiPageState<any>('strategy-reuse:video')
  if (!draft?.content) return
  sellingPoints.value = draft.content
  selectedView.value = '商家老板'
  selectedStyle.value = '沉浸式体验'
  selectedGoal.value = '直接转化预订'
  selectedDuration.value = '30'
  generateCount.value = 3
  persistState()
}

// ====== 生成 ======
const progressSteps = ['理解卖点与创作视角', '匹配最佳文案风格', '生成多版本口播脚本', '推荐BGM与发布建议']

async function generate() {
  if (!sellingPoints.value.trim()) return

  generating.value = true
  generated.value = false
  loadingStep.value = 0
  versions.value = []
  persistState()

  loadingTimer = setInterval(() => {
    if (loadingStep.value < progressSteps.length) {
      loadingStep.value++
    } else { if (loadingTimer) clearInterval(loadingTimer) }
  }, 700)
  let failed = false

  try {
    const content = await collectStreamContent('video', buildContentAiParams(hotel, 'video', {
      sellingPoints: sellingPoints.value,
      theme: sellingPoints.value,
      message: sellingPoints.value,
      view: selectedView.value,
      style: selectedStyle.value,
      goal: selectedGoal.value,
      duration: selectedDuration.value,
      count: generateCount.value,
      outputFormat: 'json',
      schema: {
        scripts: ['口播脚本版本'],
        shots: ['镜头建议'],
        publishTips: '发布建议',
        bgm: 'BGM建议',
      },
    }), {
      onChunk(_chunk, text) {
        const result = parseVideoResult(text)
        if (!result.scripts.length) return
        versions.value = result.scripts.slice(0, generateCount.value).map((script, index) => buildVersion(script, result, index, true))
        generated.value = true
        persistState()
      },
    })
    const result = parseVideoResult(content)

    versions.value = []
    result.scripts.slice(0, generateCount.value).forEach((script, i) => {
      versions.value.push(buildVersion(script, result, i))
    })
    persistState()
  } catch {
    failed = true
    flashToast('AI调用失败，请稍后重试')
  }

  if (loadingTimer) clearInterval(loadingTimer)
  loadingStep.value = progressSteps.length
  generating.value = false
  generated.value = versions.value.length > 0
  persistState()
  if (failed || !generated.value) {
    generated.value = false
    persistState()
    flashToast('AI调用失败，请稍后重试')
  }
}

// ====== 复制 ======
function copyScript(text: string) {
  navigator.clipboard.writeText(text).then(() => flashToast('已复制'))
    .catch(() => flashToast('复制失败'))
}

function copyAll() {
  const all = versions.value.map(v => `【${v.label}】\n${v.text}`).join('\n\n---\n\n')
  navigator.clipboard.writeText(all).then(() => flashToast('已复制全部版本'))
    .catch(() => flashToast('复制失败'))
}

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1500)
}

onMounted(() => {
  restoreState()
})
</script>

<template>
  <div class="h-full flex flex-col">
    <!-- Toast -->
    <div v-if="toast" class="fixed top-6 right-6 z-50 bg-bamboo-800 text-white px-5 py-3 rounded-lg shadow-lg text-sm">
      {{ toast }}
    </div>

    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-start gap-3">
        <Mic class="w-5 h-5 text-bamboo-800 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">短视频口播文案</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">多视角 · 多风格 · 多目的，AI 生成专属口播脚本</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <button @click="router.push('/history/video')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <FileText class="w-3 h-3" />生成记录
        </button>
        <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <Zap class="w-3 h-3" />算力流水
        </button>
        <span class="text-[10px] font-medium bg-bamboo-50 text-bamboo-700 px-2 py-1 rounded-full">预计消耗 6 算力 / 次</span>
      </div>
    </div>

    <!-- Body: Two Columns -->
    <div class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- ========== 左栏：配置 ========== -->
      <div class="border-r border-cream-200/60 overflow-y-auto flex flex-col">
        <!-- 创作视角 -->
        <div class="px-4 pt-4">
          <div class="text-[12px] font-medium text-bamboo-950 mb-2">创作视角</div>
          <div class="flex gap-1.5 flex-wrap">
            <button
              v-for="v in views" :key="v.val"
              @click="selectedView = v.val"
              :class="[
                'px-3 py-1.5 rounded-full text-[11px] transition-colors border',
                selectedView === v.val
                  ? 'bg-bamboo-800 text-white border-bamboo-800'
                  : 'bg-white text-warm-600 border-cream-200 hover:border-bamboo-300'
              ]"
            >{{ v.val }}</button>
          </div>
          <div class="text-[10px] text-warm-500 bg-cream-50 rounded-lg px-3 py-2 mt-2 leading-relaxed min-h-[40px] transition-all">
            {{ viewHint }}
          </div>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 商家/卖点 -->
        <div class="px-4">
          <div class="mb-2 flex items-center justify-between gap-2">
            <div class="text-[12px] font-medium text-bamboo-950">商家 / 卖点 <span class="text-rose-400">*</span><span class="ml-1 text-[10px] font-normal text-warm-500">（填写越详细，生成效果越好）</span></div>
            <AiPolishControl :source-text="sellingPoints" scene="video" field="sellingPoints" @accept="sellingPoints = $event" />
          </div>
          <div class="border border-cream-200 rounded-lg overflow-hidden focus-within:border-bamboo-400 transition-colors">
            <textarea
              v-model="sellingPoints"
              rows="5"
              maxlength="1000"
              class="w-full text-[12px] leading-relaxed px-3 py-2.5 border-0 bg-white text-bamboo-950 resize-none focus:outline-none"
              placeholder="所在城市：&#10;行业类别：&#10;主推产品：&#10;产品卖点："
            />
            <div class="flex justify-end px-3 py-1.5 bg-cream-50 text-[10px] text-warm-500">
              {{ pointCount }}/1000
            </div>
          </div>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 文案风格 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-bamboo-950 mb-2">文案风格</div>
          <select v-model="selectedStyle" class="w-full text-[12px] px-2.5 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400">
            <option v-for="s in styles" :key="s.val" :value="s.val">{{ s.icon }} {{ s.val }}</option>
          </select>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 营销目的 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-bamboo-950 mb-2">营销目的</div>
          <select v-model="selectedGoal" class="w-full text-[12px] px-2.5 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400">
            <option v-for="g in goals" :key="g.val" :value="g.val">{{ g.icon }} {{ g.val }} - {{ g.desc }}</option>
          </select>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 视频时长 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-bamboo-950 mb-2">内容字数 / 时长</div>
          <div class="flex gap-2">
            <button
              v-for="d in durations" :key="d.val"
              @click="selectedDuration = d.val"
              :class="[
                'flex-1 py-2.5 rounded-lg text-[11px] border text-center leading-tight transition-colors',
                selectedDuration === d.val
                  ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-700 font-medium'
                  : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-200'
              ]"
            >
              {{ d.label }}<br><span class="text-[9px] opacity-60">{{ d.sub }}</span>
            </button>
          </div>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 生成条数 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-bamboo-950 mb-2">生成条数</div>
          <div class="flex gap-2">
            <button v-for="n in [1,2,3]" :key="n"
              @click="generateCount = n"
              :class="['flex-1 py-2 rounded-lg text-[11px] border text-center transition-colors', generateCount === n ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-700 font-medium' : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-200']">
              {{ n }} 条
            </button>
          </div>
        </div>

        <!-- 底部按钮 -->
        <div class="mt-auto px-4 py-3 border-t border-cream-200 bg-white flex-shrink-0 flex gap-2">
          <button @click="generate" :disabled="generating || !sellingPoints.trim()"
            class="flex-1 py-2.5 rounded-lg bg-bamboo-800 text-white text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors">
            <Sparkles class="w-4 h-4" />立即生成
          </button>
        </div>
      </div>

      <!-- ========== 右栏：输出 ========== -->
      <div class="bg-cream-50 overflow-y-auto flex flex-col min-h-0">
        <!-- 空状态 -->
        <div v-if="!generating && !generated" class="flex flex-col items-center justify-center flex-1 min-h-[400px] gap-3 text-warm-500">
          <Mic class="w-10 h-10 opacity-20" />
          <p class="text-[13px]">填写卖点并选择风格</p>
          <p class="text-[11px] opacity-70 text-center">点击「立即生成」获取专属口播脚本<br>AI 将结合酒店信息生成多版本脚本 + BGM 建议</p>
        </div>

        <!-- Loading -->
        <div v-if="generating && !generated" class="video-generation-loading">
          <div class="video-loading-orb">
            <Loader2 class="w-8 h-8 text-bamboo-800 animate-spin" />
          </div>
          <p class="text-sm font-semibold text-bamboo-900">AI 正在生成短视频脚本</p>
          <div class="space-y-2">
            <div
              v-for="(s, i) in progressSteps" :key="i"
              :class="[
                'text-[11px] flex items-center gap-2.5 transition-colors',
                i < loadingStep ? 'text-bamboo-400' : i === loadingStep ? 'text-bamboo-800 font-medium' : 'text-warm-400'
              ]"
            >
              <CheckCircle2 v-if="i < loadingStep" class="w-3.5 h-3.5 text-bamboo-400" />
              <Loader2 v-else-if="i === loadingStep" class="w-3.5 h-3.5 animate-spin" />
              <span v-else class="w-3.5 h-3.5 rounded-full border border-cream-300" />
              {{ s }}
            </div>
          </div>
        </div>

        <!-- 输出区 -->
        <div v-if="generated" class="flex-1 overflow-auto p-4 space-y-3">
          <!-- 多版本脚本 -->
          <div v-for="v in versions" :key="v.num" class="bg-white border border-cream-200 rounded-lg overflow-hidden">
            <div class="flex items-center justify-between px-4 py-2.5 border-b border-cream-100">
              <div class="flex items-center gap-2">
                <span class="w-5 h-5 rounded-full bg-bamboo-100 text-bamboo-700 flex items-center justify-center text-[10px] font-bold">{{ v.num }}</span>
                <span class="text-[11px] font-medium text-bamboo-950">{{ v.label }}</span>
                <span :class="['text-[9px] px-1.5 py-0.5 rounded-full font-medium', v.badgeClass]">{{ v.badge }}</span>
              </div>
              <button @click="copyScript(v.text)" class="text-[10px] px-2 py-1 rounded-md border border-cream-200 bg-cream-50 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-700 hover:border-bamboo-300 transition-colors flex items-center gap-0.5">
                <Copy class="w-3 h-3" />复制
              </button>
            </div>
            <div class="px-4 py-4 space-y-4">
              <div class="grid gap-3 md:grid-cols-2">
                <div class="rounded-lg border border-cream-200 bg-cream-50 px-3 py-3">
                  <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">脚本标题</div>
                  <div class="mt-1 text-[13px] font-medium text-bamboo-950">{{ v.script?.title || 'AI 自动生成标题' }}</div>
                </div>
                <div class="rounded-lg border border-cream-200 bg-cream-50 px-3 py-3">
                  <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">开场钩子</div>
                  <div class="mt-1 text-[13px] leading-relaxed text-bamboo-900">{{ v.script?.hook || '待生成' }}</div>
                </div>
              </div>

              <div class="rounded-lg border border-cream-200 bg-white px-3 py-3">
                <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">口播正文</div>
                <div class="mt-2 whitespace-pre-wrap text-[13px] leading-7 text-bamboo-950">{{ v.script?.spokenScript || v.text }}</div>
              </div>

              <div v-if="v.script?.cta" class="rounded-lg border border-bamboo-200 bg-bamboo-50/60 px-3 py-3">
                <div class="text-[10px] font-semibold uppercase tracking-wider text-bamboo-700">行动引导</div>
                <div class="mt-1 text-[13px] leading-relaxed text-bamboo-900">{{ v.script.cta }}</div>
              </div>

              <div class="grid gap-3 md:grid-cols-2">
                <div class="rounded-lg border border-cream-200 bg-cream-50 px-3 py-3">
                  <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">已核实信息</div>
                  <div v-if="v.script?.verifiedFacts?.length" class="mt-2 space-y-1.5">
                    <div v-for="fact in v.script.verifiedFacts" :key="fact" class="text-[12px] leading-6 text-bamboo-900">
                      {{ fact }}
                    </div>
                  </div>
                  <div v-else class="mt-2 text-[12px] text-warm-500">暂无</div>
                </div>
                <div class="rounded-lg border border-cream-200 bg-cream-50 px-3 py-3">
                  <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">发布前确认</div>
                  <div v-if="v.script?.toConfirm?.length" class="mt-2 space-y-1.5">
                    <div v-for="item in v.script.toConfirm" :key="item" class="text-[12px] leading-6 text-bamboo-900">
                      {{ item }}
                    </div>
                  </div>
                  <div v-else class="mt-2 text-[12px] text-warm-500">暂无</div>
                </div>
              </div>

              <div v-if="v.shots?.length" class="rounded-lg border border-cream-200 bg-white px-3 py-3">
                <div class="flex items-center justify-between gap-3">
                  <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">分镜建议</div>
                  <div class="text-[10px] text-warm-500">{{ v.shots.length }} 个镜头</div>
                </div>
                <div class="mt-3 space-y-2.5">
                  <div v-for="(shot, shotIndex) in v.shots" :key="`${v.num}-${shotIndex}`" class="rounded-lg border border-cream-200 bg-cream-50 px-3 py-3">
                    <div class="flex items-center justify-between gap-3">
                      <div class="text-[12px] font-medium text-bamboo-950">{{ shotIndex + 1 }}. {{ shot.scene || '镜头场景' }}</div>
                      <div v-if="shot.durationHint" class="text-[10px] text-warm-500">{{ shot.durationHint }}</div>
                    </div>
                    <div v-if="shot.shot" class="mt-2 text-[12px] leading-6 text-bamboo-900">{{ shot.shot }}</div>
                    <div v-if="shot.visual" class="mt-1 text-[12px] leading-6 text-warm-600">画面：{{ shot.visual }}</div>
                    <div v-if="shot.caption" class="mt-1 text-[12px] leading-6 text-warm-600">字幕：{{ shot.caption }}</div>
                  </div>
                </div>
              </div>

              <div class="grid gap-3 md:grid-cols-2">
                <div class="rounded-lg border border-cream-200 bg-cream-50 px-3 py-3">
                  <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">发布建议</div>
                  <div class="mt-2 whitespace-pre-wrap text-[12px] leading-6 text-bamboo-900">{{ v.publishTips || '暂无' }}</div>
                </div>
                <div class="rounded-lg border border-cream-200 bg-cream-50 px-3 py-3">
                  <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">BGM 建议</div>
                  <div class="mt-2 whitespace-pre-wrap text-[12px] leading-6 text-bamboo-900">{{ v.bgm || '暂无' }}</div>
                </div>
              </div>

              <div class="flex justify-between mt-1.5">
                <span class="text-[10px] text-warm-500">{{ v.text.length }} 字</span>
                <span class="text-[10px] text-warm-500">约 {{ Math.round(v.text.length / 4) }} 秒</span>
              </div>
            </div>
          </div>

          <!-- 底部操作栏 -->
          <div class="bg-white border border-cream-200 rounded-lg px-4 py-3 flex items-center justify-between gap-3">
            <span class="text-[11px] text-warm-600">已生成 {{ versions.length }} 个版本 · 可直接复制使用</span>
            <button @click="copyAll" class="text-[12px] px-3 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-700 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
              <Copy class="w-3.5 h-3.5" />复制全部
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.video-generation-loading {
  display: flex;
  min-height: 520px;
  height: 100%;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.95rem;
  text-align: center;
}

.video-loading-orb {
  display: flex;
  height: 4rem;
  width: 4rem;
  align-items: center;
  justify-content: center;
  border-radius: 1rem;
  background: #f2f8ee;
  box-shadow: inset 0 0 0 1px #d9e7ce;
}
</style>
