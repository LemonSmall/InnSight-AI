<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePlanStore, type MarketingPlan } from '@/stores/plan'
import { getGenerationHistory } from '@/api/history'
import {
  formatHistoryTime,
  historyTitle,
  promptText,
  resultText,
  reuseGeneration,
  type HistoryItem,
} from '@/utils/generationHistory'
import {
  ArrowLeft,
  Calendar,
  CheckCircle2,
  Clock,
  Copy,
  FileText,
  Loader2,
  Plus,
  Search,
  Trash2,
  TrendingUp,
  X,
} from 'lucide-vue-next'

const store = usePlanStore()
const router = useRouter()
const searchQuery = ref('')
const pageError = ref('')
const deleteTarget = ref<MarketingPlan | null>(null)
const deleting = ref(false)
const generatedRecords = ref<HistoryItem[]>([])
const loadingGenerated = ref(false)

const filteredPlans = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  if (!keyword) return store.plans
  return store.plans.filter(plan =>
    [plan.name, plan.festival, plan.hotelName, plan.target]
      .some(value => String(value || '').toLowerCase().includes(keyword))
  )
})

onMounted(reload)

async function reload() {
  pageError.value = ''
  loadingGenerated.value = true
  try {
    const [, historyResponse] = await Promise.all([
      store.loadFromApi(),
      getGenerationHistory('strategy', 80),
    ])
    generatedRecords.value = historyResponse.data?.data || []
  } catch {
    pageError.value = store.error || '营销方案加载失败，请稍后重试'
  } finally {
    loadingGenerated.value = false
  }
}

function createStrategy() {
  router.push({ path: '/strategy', query: { new: '1' } })
}

function openPlan(plan: MarketingPlan) {
  router.push({ path: '/plan', query: { id: plan.id } })
}

function openGenerated(item: HistoryItem) {
  router.push({ path: `/history/strategy/${item.id}`, query: { from: '/plans' } })
}

function reuseGenerated(item: HistoryItem) {
  reuseGeneration(item, router)
}

async function duplicatePlan(plan: MarketingPlan) {
  pageError.value = ''
  try {
    const clone = await store.duplicate(plan.id)
    if (clone) openPlan(clone)
  } catch {
    pageError.value = store.error || '方案复制失败'
  }
}

async function confirmDelete() {
  if (!deleteTarget.value || deleting.value) return
  deleting.value = true
  pageError.value = ''
  try {
    await store.remove(deleteTarget.value.id)
    deleteTarget.value = null
  } catch {
    pageError.value = store.error || '方案删除失败'
  } finally {
    deleting.value = false
  }
}

function statusText(status: MarketingPlan['status']) {
  if (status === 'active') return '进行中'
  if (status === 'completed') return '已完成'
  return '草稿'
}

function statusClass(status: MarketingPlan['status']) {
  if (status === 'active') return 'bg-emerald-50 text-emerald-700 border-emerald-200'
  if (status === 'completed') return 'bg-blue-50 text-blue-700 border-blue-200'
  return 'bg-cream-100 text-warm-600 border-cream-300'
}
</script>

<template>
  <div class="mx-auto max-w-6xl space-y-5 p-6">
    <header class="flex flex-wrap items-start justify-between gap-4">
      <div class="flex items-start gap-3">
        <button class="icon-button mt-0.5" title="返回营销策略" @click="router.push('/strategy')"><ArrowLeft class="h-4 w-4" /></button>
        <div>
          <div class="flex items-center gap-2 text-bamboo-900">
            <FileText class="h-5 w-5" />
            <h1 class="text-xl font-semibold">营销方案记录</h1>
          </div>
          <p class="mt-1 text-sm text-warm-600">查看、复用和持续维护已经保存的执行方案。</p>
        </div>
      </div>
      <button class="primary-button" @click="createStrategy"><Plus class="h-4 w-4" />创建新策略</button>
    </header>

    <section class="grid gap-3 sm:grid-cols-3">
      <div class="metric-card"><TrendingUp class="h-4 w-4 text-emerald-600" /><div><strong>{{ store.activePlans.length }}</strong><span>进行中</span></div></div>
      <div class="metric-card"><CheckCircle2 class="h-4 w-4 text-blue-600" /><div><strong>{{ store.completedPlans.length }}</strong><span>已完成</span></div></div>
      <div class="metric-card"><Clock class="h-4 w-4 text-warm-500" /><div><strong>{{ store.draftPlans.length }}</strong><span>草稿</span></div></div>
    </section>

    <section class="overflow-hidden rounded-2xl border border-cream-300 bg-white shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-cream-200 p-4">
        <div class="relative min-w-[260px] flex-1 sm:max-w-md">
          <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-warm-400" />
          <input v-model="searchQuery" class="studio-input pl-9" placeholder="搜索方案名称、场景、酒店或目标" />
        </div>
        <span class="text-xs text-warm-500">共 {{ filteredPlans.length }} 个方案</span>
      </div>

      <p v-if="pageError" class="m-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{{ pageError }}</p>
      <div v-if="store.loading" class="flex items-center justify-center gap-2 py-20 text-sm text-warm-500"><Loader2 class="h-5 w-5 animate-spin" />正在加载方案</div>
      <div v-else-if="filteredPlans.length" class="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
        <article v-for="plan in filteredPlans" :key="plan.id" class="plan-card" @click="openPlan(plan)">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <h2 class="truncate text-sm font-semibold text-bamboo-900">{{ plan.name }}</h2>
              <p class="mt-1 truncate text-xs text-warm-500">{{ plan.festival || plan.target || '常规营销方案' }}</p>
            </div>
            <span :class="['shrink-0 rounded-full border px-2 py-0.5 text-[10px]', statusClass(plan.status)]">{{ statusText(plan.status) }}</span>
          </div>

          <div class="mt-5 flex items-center gap-1.5 text-xs text-warm-600">
            <Calendar class="h-3.5 w-3.5" />
            {{ plan.period || '未设置执行周期' }}
          </div>
          <div v-if="plan.tags.length" class="mt-3 flex flex-wrap gap-1.5">
            <span v-for="tag in plan.tags.slice(0, 3)" :key="tag" class="rounded-full bg-bamboo-50 px-2 py-1 text-[10px] text-bamboo-700">{{ tag }}</span>
          </div>

          <div class="mt-5 flex items-center justify-between border-t border-cream-200 pt-3">
            <span class="text-[11px] text-warm-400">更新于 {{ plan.updatedAt || '-' }}</span>
            <div class="flex gap-1" @click.stop>
              <button class="mini-button" title="复制方案" @click="duplicatePlan(plan)"><Copy class="h-3.5 w-3.5" /></button>
              <button class="mini-button text-red-500" title="删除方案" @click="deleteTarget = plan"><Trash2 class="h-3.5 w-3.5" /></button>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="flex flex-col items-center justify-center py-20 text-center">
        <FileText class="h-9 w-9 text-warm-300" />
        <h2 class="mt-3 text-sm font-semibold text-bamboo-900">{{ searchQuery ? '没有匹配的方案' : '还没有保存方案' }}</h2>
        <p class="mt-1 text-xs text-warm-500">前往营销策略工作台生成新的可执行方案。</p>
        <button v-if="!searchQuery" class="primary-button mt-4" @click="createStrategy"><Plus class="h-4 w-4" />创建新策略</button>
      </div>
    </section>

    <section class="overflow-hidden rounded-2xl border border-cream-300 bg-white shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-cream-200 p-4">
        <div>
          <h2 class="text-sm font-semibold text-bamboo-900">AI 生成记录</h2>
          <p class="mt-1 text-xs text-warm-500">营销策略生成后的原始方案也在这里查看和复用。</p>
        </div>
        <span class="text-xs text-warm-500">共 {{ generatedRecords.length }} 条</span>
      </div>

      <div v-if="loadingGenerated" class="flex items-center justify-center gap-2 py-14 text-sm text-warm-500">
        <Loader2 class="h-5 w-5 animate-spin" />正在加载生成记录
      </div>
      <div v-else-if="generatedRecords.length" class="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
        <article v-for="record in generatedRecords" :key="record.id" class="plan-card">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <h2 class="truncate text-sm font-semibold text-bamboo-900">{{ historyTitle(record) }}</h2>
              <p class="mt-1 truncate text-xs text-warm-500">{{ promptText(record) || resultText(record).slice(0, 40) || '营销策略生成' }}</p>
            </div>
            <span class="shrink-0 rounded-full border border-bamboo-200 bg-bamboo-50 px-2 py-0.5 text-[10px] text-bamboo-700">生成记录</span>
          </div>
          <p class="mt-4 line-clamp-3 text-xs leading-6 text-warm-600">{{ resultText(record) || '暂无生成内容' }}</p>
          <div class="mt-5 flex items-center justify-between border-t border-cream-200 pt-3">
            <span class="text-[11px] text-warm-400">{{ formatHistoryTime(record.createdAt) }}</span>
            <div class="flex gap-1">
              <button class="mini-button" title="查看详情" @click="openGenerated(record)"><FileText class="h-3.5 w-3.5" /></button>
              <button class="mini-button" title="复用配置" @click="reuseGenerated(record)"><Copy class="h-3.5 w-3.5" /></button>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="flex flex-col items-center justify-center py-16 text-center">
        <Clock class="h-8 w-8 text-warm-300" />
        <h2 class="mt-3 text-sm font-semibold text-bamboo-900">还没有营销策略生成记录</h2>
        <p class="mt-1 text-xs text-warm-500">从营销策略工作台生成后会自动出现在这里。</p>
      </div>
    </section>

    <div v-if="deleteTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-bamboo-950/40 p-4" @click.self="deleteTarget = null">
      <div class="w-full max-w-sm rounded-2xl border border-cream-300 bg-white shadow-xl">
        <div class="flex items-start justify-between gap-4 p-5">
          <div>
            <h2 class="text-base font-semibold text-bamboo-900">删除营销方案</h2>
            <p class="mt-2 text-sm leading-6 text-warm-600">确定删除“{{ deleteTarget.name }}”吗？该操作无法恢复。</p>
          </div>
          <button class="icon-button" @click="deleteTarget = null"><X class="h-4 w-4" /></button>
        </div>
        <div class="flex justify-end gap-2 border-t border-cream-200 px-5 py-4">
          <button class="secondary-button" @click="deleteTarget = null">取消</button>
          <button class="danger-button" :disabled="deleting" @click="confirmDelete"><Loader2 v-if="deleting" class="h-4 w-4 animate-spin" /><Trash2 v-else class="h-4 w-4" />确认删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.primary-button,
.secondary-button,
.danger-button { display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; border-radius: 0.75rem; padding: 0.65rem 0.9rem; font-size: 0.78rem; font-weight: 600; transition: 150ms ease; }
.primary-button { background: #234d32; color: white; }
.primary-button:hover { background: #183b26; }
.secondary-button { border: 1px solid #e5d8c5; background: white; color: #5f5143; }
.secondary-button:hover { border-color: #8cac77; background: #f7faf4; }
.danger-button { background: #dc2626; color: white; }
.danger-button:disabled { opacity: 0.55; }
.icon-button { display: inline-flex; height: 2.25rem; width: 2.25rem; align-items: center; justify-content: center; border: 1px solid #e5d8c5; border-radius: 0.65rem; color: #776655; transition: 150ms ease; }
.icon-button:hover { border-color: #8cac77; background: #f7faf4; color: #234d32; }
.metric-card { display: flex; align-items: center; gap: 0.8rem; border: 1px solid #eadfce; border-radius: 0.9rem; background: white; padding: 0.9rem 1rem; }
.metric-card strong { display: block; color: #234d32; font-size: 1.1rem; line-height: 1; }
.metric-card span { display: block; margin-top: 0.2rem; color: #9b8976; font-size: 0.7rem; }
.plan-card { cursor: pointer; border: 1px solid #eadfce; border-radius: 0.85rem; padding: 1rem; transition: 150ms ease; }
.plan-card:hover { transform: translateY(-1px); border-color: #8cac77; background: #fbfdf9; box-shadow: 0 5px 18px rgb(54 79 50 / 0.08); }
.mini-button { display: inline-flex; height: 1.9rem; width: 1.9rem; align-items: center; justify-content: center; border-radius: 0.5rem; color: #8a7968; }
.mini-button:hover { background: #f3eadf; }
</style>
