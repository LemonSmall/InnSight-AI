<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ExternalLink, FileText, Loader2, RotateCcw, Trash2, X } from 'lucide-vue-next'
import { deleteGenerationHistory, getGenerationHistory } from '@/api/history'
import {
  formatHistoryTime,
  generationModules,
  historyTitle,
  imageUrl,
  moduleDetailRoute,
  moduleHistoryRoute,
  moduleLabel,
  moduleRoute,
  promptText,
  resultText,
  reuseGeneration,
  type HistoryItem,
} from '@/utils/generationHistory'
import { cleanMarkdown, columnIndex, firstTable, parsePlan, sectionByTitle } from '@/utils/planMarkdown'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const deleting = ref(false)
const histories = ref<HistoryItem[]>([])
const selectedIds = ref<number[]>([])
const deleteDialogOpen = ref(false)
const deleteIds = ref<number[]>([])
const toast = ref('')

const moduleKey = computed(() => String(route.params.moduleKey || ''))
const moduleName = computed(() => moduleLabel(moduleKey.value))
const moduleMeta = computed(() => generationModules.find(item => item.key === moduleKey.value))

onMounted(load)
watch(moduleKey, () => load())

async function load() {
  loading.value = true
  try {
    const { data } = await getGenerationHistory(moduleKey.value || undefined, 100)
    histories.value = data.data || []
    selectedIds.value = selectedIds.value.filter(id => histories.value.some(item => item.id === id))
  } finally {
    loading.value = false
  }
}

function openDetail(item: HistoryItem) {
  router.push({ path: moduleDetailRoute(item), query: { from: moduleHistoryRoute(moduleKey.value) } })
}

function reuse(item: HistoryItem) {
  reuseGeneration(item, router)
  flash('已复用当时配置')
}

function isSelected(id: number) {
  return selectedIds.value.includes(id)
}

function toggleSelected(id: number) {
  selectedIds.value = isSelected(id)
    ? selectedIds.value.filter(item => item !== id)
    : [...selectedIds.value, id]
}

function toggleAll() {
  selectedIds.value = selectedIds.value.length === histories.value.length ? [] : histories.value.map(item => item.id)
}

function requestDelete(ids: number[]) {
  const safeIds = ids.filter(Boolean)
  if (!safeIds.length || deleting.value) return
  deleteIds.value = safeIds
  deleteDialogOpen.value = true
}

function cancelDelete() {
  if (deleting.value) return
  deleteIds.value = []
  deleteDialogOpen.value = false
}

async function confirmDelete() {
  const safeIds = deleteIds.value.filter(Boolean)
  if (!safeIds.length || deleting.value) return
  deleting.value = true
  try {
    await deleteGenerationHistory(safeIds)
    selectedIds.value = selectedIds.value.filter(id => !safeIds.includes(id))
    await load()
    deleteIds.value = []
    deleteDialogOpen.value = false
    flash('已删除生成记录')
  } finally {
    deleting.value = false
  }
}

function flash(message: string) {
  toast.value = message
  window.setTimeout(() => { toast.value = '' }, 1600)
}

function pricingPreview(item: HistoryItem) {
  if (item.moduleKey !== 'pricing') return []
  const sections = parsePlan(resultText(item))
  const table = firstTable(sectionByTitle(sections, [/房型定价|定价执行|逐房型|建议价/]))
  if (!table?.rows?.length) return []
  const roomIndex = columnIndex(table, ['房型', 'roomName'], 0)
  const currentIndex = columnIndex(table, ['当前挂牌价', '挂牌价', '当前价'], 1)
  const rangeIndex = columnIndex(table, ['建议价区间', '建议价', '价格区间'], 2)
  return table.rows
    .map(row => ({
      room: cleanMarkdown(row[roomIndex] || ''),
      current: compactPriceText(cleanMarkdown(row[currentIndex] || '待核实')),
      target: compactPriceText(cleanMarkdown(row[rangeIndex] || '待核实')),
    }))
    .filter(row => row.room && !/^(房型|roomName)$/i.test(row.room))
    .slice(0, 3)
}

function compactPriceText(value: string) {
  const numbers = String(value || '').match(/\d{2,5}/g) || []
  if (numbers.length >= 2) return `¥${numbers[0]}-${numbers[1]}`
  if (numbers.length === 1) return `¥${numbers[0]}`
  return cleanMarkdown(value || '待核实').slice(0, 18)
}

function strategyPreview(item: HistoryItem) {
  if (item.moduleKey !== 'strategy') return { goals: [] as string[], phases: [] as string[], channels: [] as string[] }
  const sections = parsePlan(resultText(item))
  const kpi = firstTable(sectionByTitle(sections, [/核心目标|KPI|指标|目标/]))
  const timeline = firstTable(sectionByTitle(sections, [/时间表|执行时间|阶段|节奏/]))
  const channel = firstTable(sectionByTitle(sections, [/渠道|内容计划|发布计划/]))
  const metricIndex = kpi ? columnIndex(kpi, ['指标', '目标', 'KPI'], 0) : 0
  const targetIndex = kpi ? columnIndex(kpi, ['目标值', '数值', '结果'], 1) : 1
  const phaseIndex = timeline ? columnIndex(timeline, ['阶段'], 0) : 0
  const focusIndex = timeline ? columnIndex(timeline, ['重点'], 2) : 2
  const channelIndex = channel ? columnIndex(channel, ['渠道'], 0) : 0
  return {
    goals: (kpi?.rows || [])
      .map(row => [cleanMarkdown(row[metricIndex] || ''), compactGoalText(cleanMarkdown(row[targetIndex] || ''))].filter(Boolean).join('：'))
      .filter(Boolean)
      .slice(0, 2),
    phases: (timeline?.rows || [])
      .map(row => [cleanMarkdown(row[phaseIndex] || ''), cleanMarkdown(row[focusIndex] || '')].filter(Boolean).join('：'))
      .filter(Boolean)
      .slice(0, 3),
    channels: (channel?.rows || [])
      .map(row => cleanMarkdown(row[channelIndex] || ''))
      .filter(Boolean)
      .slice(0, 4),
  }
}

function compactGoalText(value: string) {
  const text = cleanMarkdown(value || '')
  if (!text) return ''
  if (/待核实|无|暂无/.test(text)) return '待核实'
  return text.length > 12 ? text.replace(/[，。,；;].*$/, '').slice(0, 12) : text
}
</script>

<template>
  <div class="mx-auto max-w-7xl space-y-5 p-6">
    <transition name="toast">
      <div v-if="toast" class="fixed right-6 top-6 z-50 rounded-lg bg-bamboo-900 px-4 py-2 text-sm text-bamboo-50 shadow-xl">{{ toast }}</div>
    </transition>

    <header class="flex flex-wrap items-start justify-between gap-4">
      <div class="flex min-w-0 items-start gap-3">
        <button class="icon-button mt-0.5" title="返回生成页" @click="router.push(moduleMeta?.route || '/history')">
          <ArrowLeft class="h-4 w-4" />
        </button>
        <div>
          <p class="text-xs font-semibold text-bamboo-700">生成记录</p>
          <h1 class="mt-1 text-2xl font-semibold text-bamboo-950">{{ moduleName }}历史生成</h1>
          <p class="mt-2 text-sm text-warm-600">查看历史结果，点击记录进入详情页，也可以直接复用当时配置。</p>
        </div>
      </div>
      <div class="flex gap-2">
        <button class="secondary-button" @click="load">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
          <RotateCcw v-else class="h-4 w-4" />
          刷新
        </button>
        <button class="primary-button" @click="router.push(moduleRoute(moduleKey))">
          <ExternalLink class="h-4 w-4" />
          去生成
        </button>
      </div>
    </header>

    <section v-if="histories.length" class="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-cream-300 bg-white px-4 py-3 shadow-sm">
      <button class="secondary-button" @click="toggleAll">{{ selectedIds.length === histories.length ? '取消全选' : '全选' }}</button>
      <button class="danger-button" :disabled="!selectedIds.length || deleting" @click="requestDelete(selectedIds)">
        <Loader2 v-if="deleting" class="h-4 w-4 animate-spin" />
        <Trash2 v-else class="h-4 w-4" />
        删除选中 {{ selectedIds.length || '' }}
      </button>
    </section>

    <section v-if="loading" class="flex min-h-[420px] items-center justify-center rounded-2xl border border-cream-300 bg-white">
      <Loader2 class="h-6 w-6 animate-spin text-bamboo-700" />
    </section>

    <section v-else-if="histories.length" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <article v-for="item in histories" :key="item.id" class="history-card">
        <div class="mb-3 flex items-center justify-between gap-2">
          <label class="inline-flex items-center gap-2 text-xs text-warm-500" @click.stop>
            <input type="checkbox" class="h-4 w-4 accent-bamboo-800" :checked="isSelected(item.id)" @change="toggleSelected(item.id)" />
            选择
          </label>
          <button class="danger-mini" title="删除记录" @click.stop="requestDelete([item.id])"><Trash2 class="h-3.5 w-3.5" /></button>
        </div>
        <button class="block w-full text-left" @click="openDetail(item)">
          <div v-if="imageUrl(item)" class="mb-3 aspect-[4/3] overflow-hidden rounded-xl bg-cream-100">
            <img :src="imageUrl(item)" class="h-full w-full object-cover" />
          </div>
          <div class="flex items-center justify-between gap-2">
            <span class="rounded-full bg-bamboo-50 px-2 py-0.5 text-[10px] font-semibold text-bamboo-700">{{ moduleLabel(item.moduleKey) }}</span>
            <span class="text-[10px] text-warm-400">{{ formatHistoryTime(item.createdAt) }}</span>
          </div>
          <h2 class="mt-3 line-clamp-1 text-base font-semibold text-bamboo-950">{{ historyTitle(item) }}</h2>
          <p class="mt-2 line-clamp-3 text-sm leading-6 text-warm-600">{{ promptText(item) || resultText(item) }}</p>

          <div v-if="pricingPreview(item).length" class="mt-3 rounded-xl border border-cream-200 bg-cream-50 p-3">
            <div class="mb-2 text-[11px] font-bold text-bamboo-800">价格重点</div>
            <div class="space-y-2">
              <div v-for="row in pricingPreview(item)" :key="row.room" class="price-mini-row">
                <span>{{ row.room }}</span>
                <strong>{{ row.current }} → {{ row.target }}</strong>
              </div>
            </div>
          </div>

          <div v-else-if="strategyPreview(item).goals.length || strategyPreview(item).phases.length || strategyPreview(item).channels.length" class="mt-3 rounded-xl border border-cream-200 bg-cream-50 p-3">
            <div class="mb-2 text-[11px] font-bold text-bamboo-800">目标与执行重点</div>
            <p v-for="goal in strategyPreview(item).goals" :key="goal" class="strategy-goal-line">{{ goal }}</p>
            <div v-if="strategyPreview(item).channels.length" class="mb-2 flex flex-wrap gap-1.5">
              <span v-for="channel in strategyPreview(item).channels" :key="channel" class="strategy-chip">{{ channel }}</span>
            </div>
            <p v-for="phase in strategyPreview(item).phases" :key="phase" class="strategy-mini-line">{{ phase }}</p>
          </div>
        </button>
        <div class="mt-4 flex items-center justify-between border-t border-cream-100 pt-3">
          <span class="text-xs text-warm-400">{{ item.costCredits || 0 }} 算力</span>
          <button class="text-button" @click="reuse(item)">复用配置</button>
        </div>
      </article>
    </section>

    <section v-else class="flex min-h-[420px] flex-col items-center justify-center rounded-2xl border border-dashed border-cream-300 bg-white text-center">
      <FileText class="h-10 w-10 text-warm-300" />
      <h2 class="mt-4 text-base font-semibold text-bamboo-950">暂无{{ moduleName }}生成记录</h2>
      <button class="primary-button mt-5" @click="router.push(moduleRoute(moduleKey))">去生成</button>
    </section>

    <div v-if="deleteDialogOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-bamboo-950/45 p-4" @click.self="cancelDelete">
      <section class="w-full max-w-md rounded-2xl border border-cream-300 bg-white p-5 shadow-2xl">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-base font-semibold text-bamboo-950">删除生成记录</h2>
            <p class="mt-2 text-sm leading-6 text-warm-600">
              {{ deleteIds.length === 1 ? '确定删除这条生成记录吗？删除后无法恢复。' : `确定删除选中的 ${deleteIds.length} 条生成记录吗？删除后无法恢复。` }}
            </p>
          </div>
          <button class="icon-button" :disabled="deleting" @click="cancelDelete"><X class="h-4 w-4" /></button>
        </div>
        <div class="mt-5 flex justify-end gap-2">
          <button class="secondary-button" :disabled="deleting" @click="cancelDelete">取消</button>
          <button class="danger-button" :disabled="deleting" @click="confirmDelete">
            <Loader2 v-if="deleting" class="h-4 w-4 animate-spin" />
            <Trash2 v-else class="h-4 w-4" />
            确认删除
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.primary-button,.secondary-button,.icon-button,.text-button { display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; border-radius: 0.7rem; font-size: 0.75rem; font-weight: 600; transition: 150ms ease; }
.primary-button { background: #234d32; color: white; padding: 0.6rem 0.85rem; }
.primary-button:hover { background: #183b26; }
.secondary-button { border: 1px solid #e5d8c5; background: white; color: #5f5143; padding: 0.6rem 0.85rem; }
.secondary-button:hover,.icon-button:hover { border-color: #8cac77; background: #f7faf4; color: #234d32; }
.icon-button { height: 2.25rem; width: 2.25rem; border: 1px solid #e5d8c5; color: #776655; }
.icon-button:disabled { cursor: not-allowed; opacity: 0.5; }
.text-button { color: #315b37; }
.danger-button { display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; border-radius: 0.7rem; background: #b91c1c; color: white; padding: 0.6rem 0.85rem; font-size: 0.75rem; font-weight: 600; transition: 150ms ease; }
.danger-button:hover { background: #991b1b; }
.danger-button:disabled { cursor: not-allowed; opacity: 0.45; }
.danger-mini { display: inline-flex; height: 1.85rem; width: 1.85rem; align-items: center; justify-content: center; border-radius: 0.55rem; color: #b91c1c; transition: 150ms ease; }
.danger-mini:hover { background: #fee2e2; }
.history-card { border: 1px solid #eadfce; border-radius: 1rem; background: white; padding: 1rem; box-shadow: 0 8px 24px rgb(70 55 36 / 6%); transition: 160ms ease; }
.history-card:hover { border-color: #8cac77; transform: translateY(-2px); }
.price-mini-row {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.15fr);
  align-items: center;
  gap: 0.75rem;
  border-radius: 0.6rem;
  background: white;
  padding: 0.45rem 0.55rem;
}
.price-mini-row span {
  min-width: 0;
  overflow: hidden;
  color: #5f5143;
  font-size: 0.72rem;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.price-mini-row strong {
  min-width: 0;
  color: #0f5a2a;
  font-size: 0.78rem;
  font-weight: 900;
  line-height: 1.45;
  overflow-wrap: anywhere;
  text-align: right;
}
.strategy-chip {
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.2rem 0.5rem;
  color: #315b37;
  font-size: 0.68rem;
  font-weight: 800;
}
.strategy-goal-line {
  margin-bottom: 0.45rem;
  border-radius: 0.55rem;
  background: #f4f8f0;
  padding: 0.45rem 0.55rem;
  color: #0f5a2a;
  font-size: 0.74rem;
  font-weight: 900;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.strategy-mini-line {
  overflow: hidden;
  color: #66594b;
  font-size: 0.74rem;
  line-height: 1.65;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.toast-enter-active,.toast-leave-active { transition: opacity 160ms ease, transform 160ms ease; }
.toast-enter-from,.toast-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
