<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CheckCircle2, Table2, Trash2 } from 'lucide-vue-next'
import { getGenerationHistory } from '@/api/history'
import AiGenerationPreview from '@/components/ai/AiGenerationPreview.vue'
import { useHotelStore } from '@/stores/hotel'
import { parseOccupancyResultPayload, type OccupancyImportData } from '@/utils/occupancyImport'
import { rawParams, resultText, type HistoryItem } from '@/utils/generationHistory'

type OccupancyHistoryRow = OccupancyImportData & {
  historyKey: string
  historyItem: HistoryItem
  source: 'local' | 'remote'
}

const router = useRouter()
const store = useHotelStore()
const selectedImportedAt = ref('')
const remoteHistories = ref<HistoryItem[]>([])

const history = computed<OccupancyHistoryRow[]>(() => {
  const localRows = store.occupancyHistory.map(item => ({
    ...item,
    historyKey: `local-${item.importedAt}`,
    historyItem: occupancyItemToHistory(item),
    source: 'local' as const,
  }))
  const localKeys = new Set(localRows.map(item => `${sourceFileLabel(item)}-${item.dateRange}`))
  const remoteRows = remoteHistories.value
    .map(item => remoteHistoryToOccupancy(item))
    .filter(Boolean) as OccupancyHistoryRow[]
  return [
    ...localRows,
    ...remoteRows.filter(item => !localKeys.has(`${sourceFileLabel(item)}-${item.dateRange}`)),
  ].sort((a, b) => String(b.importedAt || '').localeCompare(String(a.importedAt || '')))
})
const activeImportedAt = computed(() => store.occupancyImport?.importedAt || '')
const selectedItem = computed(() => {
  if (!history.value.length) return null
  return history.value.find(item => item.historyKey === selectedImportedAt.value) || history.value[0]
})
const selectedHistoryItem = computed(() => selectedItem.value?.historyItem || null)

onMounted(() => {
  loadRemoteHistory()
})

async function loadRemoteHistory() {
  try {
    const { data } = await getGenerationHistory('occupancy_image', 100)
    remoteHistories.value = data?.data || data || []
  } catch {
    remoteHistories.value = []
  }
}

function selectItem(item: OccupancyHistoryRow) {
  selectedImportedAt.value = item.historyKey
}

async function restoreCurrent(item: OccupancyHistoryRow) {
  if (item.source === 'remote') {
    await store.applyOccupancyImport(item, { persistRooms: true })
    return
  }
  store.restoreOccupancyImport(item)
}

function removeItem(item: OccupancyHistoryRow) {
  if (item.source !== 'local') return
  store.removeOccupancyHistory(item.importedAt)
  if (selectedImportedAt.value === item.historyKey) selectedImportedAt.value = ''
}

function clearAll() {
  store.clearOccupancyHistory()
  selectedImportedAt.value = ''
}

function remoteHistoryToOccupancy(item: HistoryItem): OccupancyHistoryRow | null {
  if (item.status !== 'success') return null
  const params = rawParams(item)
  const source = String(params.sourceFileName || item.title || 'AI 房态导入')
  const data = parseOccupancyResultPayload(resultText(item), source)
  if (!data) return null
  data.importedAt = item.completedAt || item.createdAt || data.importedAt
  return {
    ...data,
    historyKey: `remote-${item.id}`,
    historyItem: item,
    source: 'remote',
  }
}

function occupancyItemToHistory(item: OccupancyImportData): HistoryItem {
  const source = sourceFileLabel(item)
  return {
    id: Date.parse(item.importedAt || '') || 0,
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
    createdAt: item.importedAt,
    completedAt: item.importedAt,
  }
}

function sourceFileLabel(item: OccupancyImportData) {
  const names = item.sourceFileNames?.length ? item.sourceFileNames : [item.sourceFileName]
  return names.filter(Boolean).join('、') || '未命名表格'
}

function formatImportTime(value: string) {
  if (!value) return '未知时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <div class="space-y-5 pb-8">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <button class="mb-3 inline-flex items-center gap-1.5 text-xs font-semibold text-warm-500 hover:text-bamboo-800" @click="router.push('/setup')">
          <ArrowLeft class="h-3.5 w-3.5" />
          返回基础信息
        </button>
        <div class="flex items-center gap-2 text-bamboo-900">
          <Table2 class="h-5 w-5" />
          <h1 class="text-xl font-semibold">房态导入</h1>
        </div>
        <p class="mt-1 text-sm text-warm-600">查看每次上传的房态表快照，必要时可以设为当前经营数据。</p>
      </div>
      <button v-if="history.length" class="btn-secondary" @click="clearAll">
        <Trash2 class="h-4 w-4" />
        清空历史
      </button>
    </header>

    <section v-if="history.length" class="grid gap-5 xl:grid-cols-[360px_minmax(0,1fr)]">
      <aside class="rounded-3xl border border-cream-300 bg-white p-4 shadow-sm">
        <h2 class="px-1 text-sm font-semibold text-bamboo-950">上传记录</h2>
        <div class="mt-3 space-y-2">
          <article
            v-for="item in history"
            :key="item.historyKey"
            class="rounded-2xl border p-3 transition"
            :class="selectedItem?.historyKey === item.historyKey ? 'border-bamboo-300 bg-bamboo-50' : 'border-cream-200 bg-cream-50 hover:border-bamboo-200 hover:bg-white'"
          >
            <button class="w-full text-left" @click="selectItem(item)">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <span class="mb-1 inline-flex rounded-full bg-white px-2 py-0.5 text-[10px] font-semibold text-bamboo-700">房态导入</span>
                  <div class="truncate text-sm font-semibold text-bamboo-950">{{ sourceFileLabel(item) }}</div>
                  <div class="mt-0.5 text-[11px] text-warm-500">{{ item.dateRange || '未识别周期' }}</div>
                </div>
                <span v-if="activeImportedAt === item.importedAt" class="inline-flex shrink-0 items-center gap-1 rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-bamboo-700">
                  <CheckCircle2 class="h-3 w-3" />
                  当前
                </span>
              </div>
              <div class="mt-2 text-[11px] text-warm-500">{{ formatImportTime(item.importedAt) }}</div>
            </button>
            <div class="mt-3 flex items-center gap-2">
              <button class="rounded-lg border border-cream-300 bg-white px-2.5 py-1.5 text-[11px] font-semibold text-bamboo-800 hover:border-bamboo-300" @click="restoreCurrent(item)">
                设为当前
              </button>
              <button v-if="item.source === 'local'" class="rounded-lg border border-rose-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold text-rose-500 hover:bg-rose-50" @click="removeItem(item)">
                删除
              </button>
            </div>
          </article>
        </div>
      </aside>

      <main v-if="selectedItem && selectedHistoryItem" class="space-y-5">
        <section class="rounded-3xl border border-cream-300 bg-white p-5 shadow-sm">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="mb-2 inline-flex rounded-full bg-bamboo-50 px-2.5 py-1 text-xs font-semibold text-bamboo-800">房态导入</div>
              <h2 class="text-base font-semibold text-bamboo-950">{{ sourceFileLabel(selectedItem) }}</h2>
              <p class="mt-1 text-xs text-warm-500">{{ selectedItem.dateRange || '未识别周期' }} / {{ formatImportTime(selectedItem.importedAt) }}</p>
            </div>
            <button class="btn-primary" @click="restoreCurrent(selectedItem)">设为当前汇总</button>
          </div>
        </section>

        <AiGenerationPreview :item="selectedHistoryItem" />
      </main>
    </section>

    <section v-else class="rounded-3xl border border-dashed border-cream-300 bg-white px-6 py-16 text-center shadow-sm">
      <Table2 class="mx-auto h-10 w-10 text-warm-300" />
      <h2 class="mt-4 text-base font-semibold text-bamboo-950">暂无房态导入记录</h2>
      <p class="mt-2 text-sm text-warm-500">回到基础信息页上传房态表后，历史记录会自动出现在这里。</p>
      <button class="btn-primary mt-5" @click="router.push('/setup')">去上传表格</button>
    </section>
  </div>
</template>

<style scoped>
.metric-box {
  border-radius: 1rem;
  background: #faf7f1;
  padding: 0.9rem;
}

.metric-box span {
  display: block;
  color: #9b8976;
  font-size: 0.72rem;
}

.metric-box strong {
  display: block;
  margin-top: 0.35rem;
  color: #234d32;
  font-size: 1.25rem;
  line-height: 1.2;
}
</style>
