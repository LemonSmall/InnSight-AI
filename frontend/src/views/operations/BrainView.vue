<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useHotelStore } from '@/stores/hotel'
import { collectStreamContent } from '@/api/content'
import { getGenerationHistory } from '@/api/history'
import { buildContentAiParams } from '@/utils/aiContextParams'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { renderBrainMarkdown } from '@/utils/brainMarkdown'
import { copyTextToClipboard } from '@/utils/clipboard'
import { promptText, resultText, type HistoryItem } from '@/utils/generationHistory'
import {
  ArrowUp,
  Bot,
  Brain,
  Coins,
  Copy,
  Database,
  Globe2,
  History,
  Layers3,
  Lightbulb,
  Loader2,
  MapPin,
  Trash2,
  User,
  X,
} from 'lucide-vue-next'

interface Message {
  role: 'user' | 'ai'
  content: string
  streaming?: boolean
}

interface BrainPageState {
  input?: string
  messages?: Message[]
  enableWebSearch?: boolean
  generating?: boolean
  generatingStartedAt?: number
  pendingQuestion?: string
}

const router = useRouter()
const hotel = useHotelStore()
const input = ref('')
const sending = ref(false)
const recovering = ref(false)
const enableWebSearch = ref(false)
const clearDialogOpen = ref(false)
const chatContainer = ref<HTMLElement | null>(null)
const toast = ref('')
const pageStateKey = 'brain'
const GENERATING_STALE_MS = 5 * 60 * 1000
const RECOVER_TIMEOUT_MS = 4 * 60 * 1000
let recoverTimer: ReturnType<typeof window.setInterval> | null = null
const currentRequestStartedAt = ref(0)
const pendingQuestion = ref('')
const messages = ref<Message[]>([
  {
    role: 'ai',
    content: '你好，我是宿识家 AI 店长。你可以直接问经营问题，我会结合本店资料、房型和历史房态数据给出建议。',
  },
])

function persistState() {
  saveAiPageState<BrainPageState>(pageStateKey, {
    input: input.value,
    messages: messages.value.map(message => ({
      ...message,
      streaming: Boolean(message.streaming && (sending.value || recovering.value)),
    })),
    enableWebSearch: enableWebSearch.value,
    generating: sending.value || recovering.value,
    generatingStartedAt: sending.value || recovering.value ? (currentRequestStartedAt.value || Date.now()) : 0,
    pendingQuestion: pendingQuestion.value,
  })
}

function restoreState() {
  const restored = loadAiPageState<BrainPageState>(pageStateKey)
  const canRestoreGenerating = Boolean(restored?.generating && restored.generatingStartedAt && Date.now() - restored.generatingStartedAt < GENERATING_STALE_MS)
  currentRequestStartedAt.value = canRestoreGenerating ? Number(restored?.generatingStartedAt || 0) : 0
  pendingQuestion.value = canRestoreGenerating ? String(restored?.pendingQuestion || '') : ''
  if (restored?.messages?.length) {
    messages.value = restored.messages
      .map(message => ({
        role: message.role,
        content: String(message.content || ''),
        streaming: Boolean(message.streaming && canRestoreGenerating),
      }))
      .filter(message => message.role === 'user' || message.content.trim() || message.streaming)
  }
  input.value = typeof restored?.input === 'string' ? restored.input : ''
  enableWebSearch.value = Boolean(restored?.enableWebSearch)
  if (canRestoreGenerating && messages.value.some(message => message.role === 'ai' && message.streaming)) {
    recovering.value = true
    startRecoverPolling()
  }
}

watch([messages, input, enableWebSearch], persistState, { deep: true })

const hotelTags = computed(() => String(hotel.config.tags || '').split(/[,，、\s]+/).map(item => item.trim()).filter(Boolean).slice(0, 5))
const displayCity = computed(() => {
  if (hotel.config.poiCity) return hotel.config.poiCity
  const parts = String(hotel.config.city || '').split('/').map(item => item.trim()).filter(Boolean)
  return parts[1] || parts[0] || '-'
})
const contextCards = computed(() => [
  { label: '城市', value: displayCity.value, icon: MapPin },
  { label: '酒店类型', value: hotel.config.type || '-', icon: Bot },
  { label: '房型资料', value: `${hotel.roomTypes.length} 项`, icon: Layers3 },
])
const occupancyCard = computed(() => {
  const data = hotel.occupancyImport
  if (!data) {
    return {
      rate: '待上传',
      range: '基础信息页上传历史房态表后启用',
      summary: 'AI 店长会优先使用已上传的房型占用、剩余可售和出租率数据。',
    }
  }
  return {
    rate: `${Math.round(data.averageOccupancyRate * 100)}%`,
    range: data.dateRange || '已上传周期',
    summary: hotel.occupancySummaryText,
  }
})

const sideLinks = [
  { label: 'AI 店长历史', desc: '查看过往经营问答', route: '/history/brain', icon: History },
  { label: '知识库', desc: '维护本店资料', route: '/knowledge', icon: Database },
  { label: '营销策略', desc: '生成周期作战方案', route: '/strategy', icon: Lightbulb },
  { label: '定价建议', desc: '按条件生成调价方案', route: '/pricing', icon: Coins },
]

async function send() {
  const question = input.value.trim()
  if (!question || sending.value) return
  input.value = ''
  sending.value = true
  recovering.value = false
  currentRequestStartedAt.value = Date.now()
  pendingQuestion.value = question
  messages.value.push({ role: 'user', content: question })
  const aiIndex = messages.value.push({ role: 'ai', content: '', streaming: true }) - 1
  persistState()
  await scrollAfterRender()

  try {
    const params = buildContentAiParams(hotel, 'brain', {
      message: question,
      userQuestion: question,
      requireKnowledge: true,
      enableWebSearch: enableWebSearch.value,
      outputStyle: 'concise_actionable',
    })
    const content = await collectStreamContent('brain', params, {
      timeoutMs: 3 * 60 * 1000,
      onChunk(_chunk, streamedContent) {
        const target = messages.value[aiIndex]
        if (!target) return
        target.content = streamedContent
        persistState()
        scrollToBottom()
      },
    })
    messages.value[aiIndex] = { role: 'ai', content: content || 'AI 暂时没有返回内容，请稍后重试。' }
  } catch (error: any) {
    messages.value[aiIndex] = { role: 'ai', content: error?.message || 'AI 调用失败，请稍后重试。' }
  } finally {
    sending.value = false
    recovering.value = false
    pendingQuestion.value = ''
    currentRequestStartedAt.value = 0
    stopRecoverPolling()
    persistState()
    await scrollAfterRender()
  }
}

function startRecoverPolling() {
  stopRecoverPolling()
  void recoverLatestBrainResult()
  recoverTimer = window.setInterval(() => {
    void recoverLatestBrainResult()
  }, 2000)
}

function stopRecoverPolling() {
  if (!recoverTimer) return
  window.clearInterval(recoverTimer)
  recoverTimer = null
}

async function recoverLatestBrainResult() {
  if (!recovering.value) return
  if (currentRequestStartedAt.value && Date.now() - currentRequestStartedAt.value > RECOVER_TIMEOUT_MS) {
    finishRecoverWithFallback()
    return
  }
  try {
    const res = await getGenerationHistory('brain', 8)
    const list = unwrapHistoryList(res)
    const candidate = list.find(item => isRecoverCandidate(item))
    if (!candidate) return
    const content = resultText(candidate)
    if (!content || content === '暂无内容') return
    replaceStreamingMessage(content)
    recovering.value = false
    sending.value = false
    pendingQuestion.value = ''
    currentRequestStartedAt.value = 0
    stopRecoverPolling()
    persistState()
    await scrollAfterRender()
  } catch {
    // Keep polling until timeout; the history endpoint may briefly lag generation.
  }
}

function unwrapHistoryList(res: any): HistoryItem[] {
  const payload = res?.data?.data || res?.data || res
  const list = Array.isArray(payload) ? payload : Array.isArray(payload?.records) ? payload.records : []
  return list.filter((item: any) => (item?.moduleKey || item?.module_key) === 'brain')
}

function isRecoverCandidate(item: HistoryItem) {
  if (!/done|success|completed/i.test(String(item.status || ''))) return false
  const createdAt = historyTime(item.completedAt || item.createdAt)
  if (currentRequestStartedAt.value && createdAt && createdAt < currentRequestStartedAt.value - 15000) return false
  const question = pendingQuestion.value.trim()
  if (!question) return true
  const prompt = promptText(item)
  const normalizedPrompt = normalizeRecoverText(prompt)
  const normalizedQuestion = normalizeRecoverText(question)
  return Boolean(normalizedPrompt && normalizedQuestion && (normalizedPrompt.includes(normalizedQuestion) || normalizedQuestion.includes(normalizedPrompt)))
}

function replaceStreamingMessage(content: string) {
  const reversedIndex = [...messages.value].reverse().findIndex(message => message.role === 'ai' && message.streaming)
  const targetIndex = reversedIndex < 0 ? -1 : messages.value.length - 1 - reversedIndex
  if (targetIndex >= 0) {
    messages.value[targetIndex] = { role: 'ai', content }
  } else {
    messages.value.push({ role: 'ai', content })
  }
}

function finishRecoverWithFallback() {
  replaceStreamingMessage('这次生成还没有同步到结果。可以稍后在 AI 店长历史里查看，或者重新发送一次。')
  recovering.value = false
  sending.value = false
  pendingQuestion.value = ''
  currentRequestStartedAt.value = 0
  stopRecoverPolling()
  persistState()
}

function normalizeRecoverText(value: string) {
  return String(value || '').replace(/\s+/g, '').slice(0, 80)
}

function historyTime(value?: string) {
  if (!value) return 0
  const time = new Date(value.replace(' ', 'T')).getTime()
  return Number.isFinite(time) ? time : 0
}

function requestClear() {
  if (!sending.value) clearDialogOpen.value = true
}

function confirmClear() {
  messages.value = [{
    role: 'ai',
    content: '对话已清空。你可以继续问我经营、定价、营销或房型表现相关问题。',
  }]
  clearDialogOpen.value = false
  persistState()
  flash('对话已清空')
}

async function copyMessage(content: string) {
  const ok = await copyTextToClipboard(content)
  flash(ok ? '已复制' : '复制失败')
}

function renderMarkdown(content: string) {
  return renderBrainMarkdown(content)
}

function flash(message: string) {
  toast.value = message
  window.setTimeout(() => (toast.value = ''), 1500)
}

async function scrollAfterRender() {
  await nextTick()
  scrollToBottom()
}

async function scrollAfterRestore() {
  await nextTick()
  scrollToBottom()
  window.requestAnimationFrame(() => {
    scrollToBottom()
    window.setTimeout(scrollToBottom, 80)
  })
}

function scrollToBottom() {
  if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
}

onMounted(async () => {
  restoreState()
  if (!hotel.config.name) hotel.loadFromApi().catch(() => {})
  await scrollAfterRestore()
})

onBeforeUnmount(() => {
  stopRecoverPolling()
})
</script>

<template>
  <div class="h-[calc(100vh-116px)] min-h-[650px] overflow-hidden">
    <transition name="toast">
      <div v-if="toast" class="fixed right-6 top-6 z-50 rounded-xl bg-bamboo-900 px-4 py-2 text-sm text-bamboo-50 shadow-xl">
        {{ toast }}
      </div>
    </transition>

    <section class="brain-shell grid h-full min-h-0 gap-5">
      <aside class="brain-sidebar flex min-h-0 flex-col overflow-hidden rounded-[28px] border border-cream-300 bg-white shadow-sm">
        <div class="bg-bamboo-950 p-5 text-bamboo-50">
          <div class="flex items-center gap-4">
            <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-bamboo-100 text-bamboo-950">
              <Brain class="h-6 w-6" />
            </div>
            <div>
              <div class="text-xl font-semibold">AI 店长</div>
              <div class="mt-0.5 text-sm text-bamboo-100/70">经营问答工作台</div>
            </div>
          </div>
          <button class="mt-4 inline-flex items-center gap-2 rounded-2xl bg-bamboo-100 px-4 py-2.5 text-sm font-semibold text-bamboo-950 hover:bg-white" @click="router.push('/knowledge')">
            <Database class="h-4 w-4" />
            更新本店资料
          </button>
        </div>

        <div class="border-b border-cream-200 p-4">
          <h2 class="text-base font-semibold text-bamboo-950">本店上下文</h2>
          <div class="mt-3 rounded-2xl bg-cream-50 p-3">
            <div class="truncate text-sm font-semibold text-bamboo-950">{{ hotel.config.name || '未设置酒店名称' }}</div>
            <div class="mt-1 text-xs text-warm-500">{{ hotel.config.type || '未设置类型' }}</div>
          </div>
          <div class="mt-3 grid grid-cols-3 gap-2">
            <div v-for="card in contextCards" :key="card.label" class="rounded-2xl bg-cream-50 p-2.5">
              <component :is="card.icon" class="h-4 w-4 text-bamboo-700" />
              <div class="mt-1 truncate text-sm font-semibold text-bamboo-950">{{ card.value }}</div>
              <div class="text-[11px] text-warm-500">{{ card.label }}</div>
            </div>
          </div>
          <div class="mt-3 rounded-2xl border border-bamboo-100 bg-bamboo-50 p-3">
            <div class="flex items-center justify-between gap-3">
              <div class="flex items-center gap-2 text-xs font-semibold text-bamboo-900">
                <Layers3 class="h-3.5 w-3.5" />
                历史出租率
              </div>
              <span class="rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-bamboo-800">{{ occupancyCard.rate }}</span>
            </div>
            <div class="mt-2 text-[11px] text-warm-500">{{ occupancyCard.range }}</div>
            <p class="mt-1 line-clamp-2 text-xs leading-5 text-warm-600">{{ occupancyCard.summary }}</p>
          </div>
          <div class="mt-3 flex flex-wrap gap-1.5">
            <span v-for="tag in hotelTags" :key="tag" class="rounded-full bg-bamboo-100 px-2 py-0.5 text-[11px] font-medium text-bamboo-800">{{ tag }}</span>
            <span v-if="!hotelTags.length" class="text-xs text-warm-500">暂无标签</span>
          </div>
        </div>

        <div class="p-4">
          <h2 class="text-base font-semibold text-bamboo-950">常用入口</h2>
          <div class="mt-3 grid gap-2">
            <button v-for="link in sideLinks" :key="link.route" class="flex w-full min-w-0 items-center gap-3 rounded-2xl border border-cream-200 bg-white p-3 text-left transition hover:border-bamboo-300 hover:bg-bamboo-50" @click="router.push(link.route)">
              <span class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl bg-bamboo-50 text-bamboo-800">
                <component :is="link.icon" class="h-4 w-4" />
              </span>
              <span class="min-w-0">
                <span class="block truncate text-sm font-semibold text-bamboo-950">{{ link.label }}</span>
                <span class="block truncate text-[11px] text-warm-500">{{ link.desc }}</span>
              </span>
            </button>
          </div>
        </div>
      </aside>

      <section class="brain-chat flex min-h-0 flex-col overflow-hidden rounded-[28px] border border-cream-300 bg-white shadow-sm">
        <div class="flex flex-shrink-0 items-center justify-between gap-4 border-b border-cream-200 px-6 py-4">
          <div>
            <h1 class="text-xl font-semibold text-bamboo-950">经营问答</h1>
            <p class="mt-1 text-sm text-warm-500">把当前问题直接发给 AI 店长。</p>
          </div>
          <div class="flex items-center gap-2">
            <label class="inline-flex cursor-pointer items-center gap-2 rounded-xl border border-cream-300 bg-white px-3 py-2 text-xs font-semibold text-warm-600 hover:border-bamboo-300 hover:bg-bamboo-50">
              <input v-model="enableWebSearch" type="checkbox" class="h-4 w-4 accent-bamboo-700" :disabled="sending" />
              <Globe2 class="h-3.5 w-3.5" />
              <span>{{ enableWebSearch ? '联网搜索开' : '联网搜索关' }}</span>
            </label>
            <button class="inline-flex items-center gap-1.5 rounded-xl border border-cream-300 bg-white px-3 py-2 text-xs font-semibold text-warm-600 hover:border-red-200 hover:bg-red-50 hover:text-red-600 disabled:opacity-50" :disabled="sending" @click="requestClear">
              <Trash2 class="h-3.5 w-3.5" />
              清空
            </button>
          </div>
        </div>

        <div ref="chatContainer" class="min-h-0 flex-1 space-y-5 overflow-y-auto bg-cream-50/70 p-6">
          <div v-for="(msg, index) in messages" :key="index" :class="['flex gap-3', msg.role === 'user' ? 'justify-end' : 'justify-start']">
            <div v-if="msg.role === 'ai'" class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-2xl bg-bamboo-900 text-bamboo-50">
              <Bot class="h-5 w-5" />
            </div>
            <article :class="['max-w-[900px] rounded-[22px] px-5 py-4 text-[15px] leading-7 shadow-sm', msg.role === 'ai' ? 'border border-cream-200 bg-white text-bamboo-950' : 'bg-bamboo-900 text-bamboo-50']">
              <template v-if="msg.role === 'ai'">
                <div v-if="msg.content" v-html="renderMarkdown(msg.content)" />
                <div v-else-if="msg.streaming" class="brain-typing">
                  <span>{{ recovering ? '正在同步生成结果' : 'AI 店长正在生成回复' }}</span>
                  <i />
                  <i />
                  <i />
                </div>
                <div v-if="msg.streaming && msg.content" class="brain-streaming-note">
                  <span>{{ recovering ? '正在同步结果' : '生成中' }}</span>
                  <i />
                  <i />
                  <i />
                </div>
              </template>
              <div v-else class="whitespace-pre-wrap">{{ msg.content }}</div>
              <div v-if="msg.role === 'ai' && !msg.streaming" class="mt-4 flex flex-wrap items-center gap-2 border-t border-cream-200 pt-3">
                <button class="inline-flex items-center gap-1.5 rounded-full border border-cream-300 px-3 py-1.5 text-xs text-warm-600 hover:border-bamboo-300 hover:text-bamboo-800" @click="copyMessage(msg.content)">
                  <Copy class="h-3.5 w-3.5" />
                  复制
                </button>
              </div>
            </article>
            <div v-if="msg.role === 'user'" class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-2xl bg-white text-bamboo-900 shadow-sm">
              <User class="h-5 w-5" />
            </div>
          </div>
        </div>

        <div class="flex-shrink-0 border-t border-cream-200 bg-white p-4">
          <div class="flex items-end gap-3 rounded-2xl border border-cream-300 bg-cream-50 p-3">
            <textarea v-model="input" rows="2" placeholder="直接问：本店卖点怎么表达？周末活动怎么做？这条差评怎么回复？" class="min-h-[56px] flex-1 resize-none bg-transparent px-1 py-1 text-[15px] leading-6 text-bamboo-950 outline-none placeholder:text-warm-400" :disabled="sending" @keydown.enter.exact.prevent="send" />
            <button class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-2xl bg-bamboo-900 text-bamboo-50 hover:bg-bamboo-800 disabled:cursor-not-allowed disabled:bg-cream-300 disabled:text-warm-500" :disabled="sending || !input.trim()" @click="send">
              <Loader2 v-if="sending" class="h-5 w-5 animate-spin" />
              <ArrowUp v-else class="h-5 w-5" />
            </button>
          </div>
        </div>
      </section>
    </section>

    <div v-if="clearDialogOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-bamboo-950/45 p-4" @click.self="clearDialogOpen = false">
      <section class="w-full max-w-md rounded-2xl border border-cream-300 bg-white p-5 shadow-2xl">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-base font-semibold text-bamboo-950">清空当前对话</h2>
            <p class="mt-2 text-sm leading-6 text-warm-600">确定清空当前 AI 店长界面的对话内容吗？历史记录不会被删除。</p>
          </div>
          <button class="icon-button" @click="clearDialogOpen = false"><X class="h-4 w-4" /></button>
        </div>
        <div class="mt-5 flex justify-end gap-2">
          <button class="secondary-button" @click="clearDialogOpen = false">取消</button>
          <button class="danger-button" @click="confirmClear"><Trash2 class="h-4 w-4" />确认清空</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.brain-shell { grid-template-columns: 320px minmax(0, 1fr); }
.brain-sidebar,.brain-chat { min-width: 0; }
:deep(.brain-markdown) { color: #173826; font-size: 0.95rem; line-height: 1.8; }
:deep(.brain-markdown > * + *) { margin-top: 1rem; }
:deep(.brain-markdown h2) { color: #0f3522; font-size: 1.18rem; font-weight: 800; line-height: 1.45; }
:deep(.brain-markdown h3) { color: #234d32; font-size: 1.02rem; font-weight: 800; line-height: 1.45; }
:deep(.brain-markdown h4) { color: #5f7d46; font-size: 0.94rem; font-weight: 800; line-height: 1.45; }
:deep(.brain-markdown p) { margin: 0; color: #2f4938; }
:deep(.brain-markdown strong) { color: #0f3522; font-weight: 800; }
:deep(.brain-markdown ul),:deep(.brain-markdown ol) { margin: 0; padding-left: 1.55rem; color: #2f4938; }
:deep(.brain-markdown ul) { list-style: disc; }
:deep(.brain-markdown ol) { list-style: decimal; }
:deep(.brain-markdown li) { margin: 0.45rem 0; padding-left: 0.28rem; line-height: 1.75; }
:deep(.brain-markdown li::marker) { color: #315b37; font-weight: 900; }
:deep(.brain-markdown code) { border-radius: 0.35rem; background: #f4efe5; padding: 0.08rem 0.3rem; color: #5f5143; font-size: 0.88em; }
:deep(.brain-table-wrap) { overflow-x: auto; border: 1px solid #eadfcc; border-radius: 0.9rem; background: #fffaf2; }
:deep(.brain-markdown table) { width: 100%; min-width: 520px; border-collapse: collapse; font-size: 0.9rem; line-height: 1.65; }
:deep(.brain-markdown th),:deep(.brain-markdown td) { border-bottom: 1px solid #eadfcc; padding: 0.65rem 0.8rem; text-align: left; vertical-align: top; }
:deep(.brain-markdown th) { background: #f5ecdc; color: #27482f; font-weight: 800; }
:deep(.brain-markdown tr:last-child td) { border-bottom: 0; }
.brain-typing,
.brain-streaming-note {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  color: #315b37;
  font-size: 0.86rem;
  font-weight: 700;
}
.brain-typing i,
.brain-streaming-note i {
  height: 0.32rem;
  width: 0.32rem;
  border-radius: 999px;
  background: #8cac77;
  animation: brain-dot 1s infinite ease-in-out;
}
.brain-typing i:nth-of-type(2),
.brain-streaming-note i:nth-of-type(2) { animation-delay: 0.14s; }
.brain-typing i:nth-of-type(3),
.brain-streaming-note i:nth-of-type(3) { animation-delay: 0.28s; }
.brain-streaming-note {
  margin-top: 0.8rem;
  border-top: 1px solid #eadfcc;
  padding-top: 0.65rem;
  color: #8b7460;
  font-size: 0.76rem;
}
@keyframes brain-dot {
  0%, 80%, 100% { opacity: 0.35; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-0.18rem); }
}
.secondary-button,.danger-button,.icon-button { display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; border-radius: 0.7rem; font-size: 0.75rem; font-weight: 600; transition: 150ms ease; }
.secondary-button { border: 1px solid #e5d8c5; background: white; color: #5f5143; padding: 0.6rem 0.85rem; }
.secondary-button:hover,.icon-button:hover { border-color: #8cac77; background: #f7faf4; color: #234d32; }
.danger-button { background: #b91c1c; color: white; padding: 0.6rem 0.85rem; }
.danger-button:hover { background: #991b1b; }
.icon-button { height: 2.25rem; width: 2.25rem; border: 1px solid #e5d8c5; color: #776655; }
@media (max-width: 1023px) { .brain-shell { grid-template-columns: 1fr; } }
.toast-enter-active,.toast-leave-active { transition: opacity 160ms ease, transform 160ms ease; }
.toast-enter-from,.toast-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
