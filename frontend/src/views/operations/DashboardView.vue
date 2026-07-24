<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useHotelStore } from '@/stores/hotel'
import { useCreditsStore } from '@/stores/credits'
import { getKnowledgeItems } from '@/api/knowledge'
import { getGenerationHistory } from '@/api/history'
import {
  ArrowRight,
  BarChart3,
  BookOpenCheck,
  Bot,
  Brain,
  CalendarDays,
  History,
  Lightbulb,
  Loader2,
  Percent,
  RefreshCw,
  Sparkles,
  Table2,
  TrendingUp,
  WandSparkles,
} from 'lucide-vue-next'
import type { OccupancyRecord, RoomOccupancySummary } from '@/utils/occupancyImport'

const router = useRouter()
const hotel = useHotelStore()
const credits = useCreditsStore()
const loading = ref(true)
const knowledgeItems = ref<any[]>([])
const historyItems = ref<any[]>([])
const dateFrom = ref('')
const dateTo = ref('')

const occupancyImport = computed(() => hotel.occupancyImport)
const allRecords = computed(() => occupancyImport.value?.records || [])
const dateOptions = computed(() => Array.from(new Set(allRecords.value.map(row => row.date))).sort())
const filteredRecords = computed(() => {
  return allRecords.value.filter(row => {
    if (dateFrom.value && row.date < dateFrom.value) return false
    if (dateTo.value && row.date > dateTo.value) return false
    return true
  })
})
const usingFilteredRange = computed(() => Boolean(dateFrom.value || dateTo.value))
const roomPerformance = computed(() => summarizeRooms(filteredRecords.value))
const dailyTrend = computed(() => summarizeDays(filteredRecords.value))
const hasOccupancyData = computed(() => roomPerformance.value.length > 0)
const totalRoomNights = computed(() => filteredRecords.value.reduce((sum, row) => sum + row.totalRooms, 0))
const occupiedRoomNights = computed(() => filteredRecords.value.reduce((sum, row) => sum + row.occupiedRooms, 0))
const averageRate = computed(() => safeRate(occupiedRoomNights.value, totalRoomNights.value))
const averageOccupancyRate = computed(() => hasOccupancyData.value ? `${Math.round(averageRate.value * 100)}%` : '-')
const bestRoom = computed(() => roomPerformance.value[0])
const slowRoom = computed(() => [...roomPerformance.value].sort((a, b) => a.averageOccupancyRate - b.averageOccupancyRate)[0])
const topRooms = computed(() => roomPerformance.value.slice(0, 3))
const weakRooms = computed(() => [...roomPerformance.value].sort((a, b) => a.averageOccupancyRate - b.averageOccupancyRate).slice(0, 3))
const totalRemainingRoomNights = computed(() => filteredRecords.value.reduce((sum, row) => sum + row.remainingRooms, 0))
const peakDay = computed(() => [...dailyTrend.value].sort((a, b) => b.rate - a.rate)[0])
const lowDay = computed(() => [...dailyTrend.value].sort((a, b) => a.rate - b.rate)[0])
const roomRateBuckets = computed(() => {
  return roomPerformance.value.reduce((buckets, room) => {
    if (room.averageOccupancyRate >= 0.5) buckets.high += 1
    else if (room.averageOccupancyRate >= 0.25) buckets.medium += 1
    else buckets.low += 1
    return buckets
  }, { high: 0, medium: 0, low: 0 })
})
const summaryTips = computed(() => {
  if (!hasOccupancyData.value) return ['上传历史房态表后，这里会自动生成经营提醒。']
  const tips = [
    `当前周期平均出租率 ${averageOccupancyRate.value}，剩余可售房晚 ${totalRemainingRoomNights.value}。`,
  ]
  if (bestRoom.value) tips.push(`${bestRoom.value.roomTypeName} 表现最好，可优先承接活动和高意向客群。`)
  if (slowRoom.value) tips.push(`${slowRoom.value.roomTypeName} 出租率偏低，建议检查价格、图片和渠道曝光。`)
  if (peakDay.value && lowDay.value) tips.push(`${peakDay.value.date} 为峰值日，${lowDay.value.date} 为低点日，营销节奏可围绕低点补量。`)
  return tips
})
const dateRangeText = computed(() => {
  if (!dateOptions.value.length) return '待上传'
  const start = dateFrom.value || dateOptions.value[0]
  const end = dateTo.value || dateOptions.value[dateOptions.value.length - 1]
  return `${start} 至 ${end}`
})
const latestHistory = computed(() => historyItems.value.slice(0, 4))
const latestKnowledge = computed(() => knowledgeItems.value.slice(0, 4))

const quickActions = [
  { title: '更新酒店资料', desc: '补充设施、政策、活动和临时经营信息', route: '/knowledge', icon: BookOpenCheck },
  { title: '问 AI 店长', desc: '基于本店资料获取经营建议', route: '/brain', icon: Brain },
  { title: '开始内容创作', desc: '生成小红书、朋友圈、推文或海报', route: '/create', icon: WandSparkles },
  { title: '制定营销策略', desc: '按目标、周期和渠道生成执行方案', route: '/strategy', icon: Lightbulb },
]

async function load() {
  loading.value = true
  await Promise.allSettled([
    hotel.loadFromApi(),
    credits.loadFromApi(),
    loadKnowledge(),
    loadHistory(),
  ])
  loading.value = false
}

async function loadKnowledge() {
  const { data: itemRes } = await getKnowledgeItems(undefined, 20)
  knowledgeItems.value = itemRes.data || itemRes || []
}

async function loadHistory() {
  const { data: res } = await getGenerationHistory(undefined, 20)
  historyItems.value = res.data || res || []
}

function resetDateRange() {
  dateFrom.value = ''
  dateTo.value = ''
}

function summarizeRooms(records: OccupancyRecord[]): RoomOccupancySummary[] {
  const groups = new Map<string, OccupancyRecord[]>()
  records.forEach(row => groups.set(row.roomTypeName, [...(groups.get(row.roomTypeName) || []), row]))
  return Array.from(groups.entries()).map(([roomTypeName, rows]) => {
    const sortedRows = [...rows].sort((a, b) => a.date.localeCompare(b.date))
    const latest = sortedRows[sortedRows.length - 1]
    const roomNights = rows.reduce((sum, row) => sum + row.totalRooms, 0)
    const occupied = rows.reduce((sum, row) => sum + row.occupiedRooms, 0)
    const remaining = rows.reduce((sum, row) => sum + row.remainingRooms, 0)
    return {
      roomTypeName,
      totalRooms: rows[0]?.totalRooms || 0,
      days: rows.length,
      occupiedRoomNights: occupied,
      remainingRoomNights: remaining,
      averageOccupancyRate: safeRate(occupied, roomNights),
      latestOccupiedRooms: latest?.occupiedRooms || 0,
      latestRemainingRooms: latest?.remainingRooms || 0,
    }
  }).sort((a, b) => b.averageOccupancyRate - a.averageOccupancyRate)
}

function summarizeDays(records: OccupancyRecord[]) {
  const groups = new Map<string, OccupancyRecord[]>()
  records.forEach(row => groups.set(row.date, [...(groups.get(row.date) || []), row]))
  return Array.from(groups.entries()).map(([date, rows]) => {
    const total = rows.reduce((sum, row) => sum + row.totalRooms, 0)
    const occupied = rows.reduce((sum, row) => sum + row.occupiedRooms, 0)
    return { date, rate: safeRate(occupied, total), occupied, total }
  }).sort((a, b) => a.date.localeCompare(b.date))
}

function safeRate(occupied: number, total: number) {
  return total ? occupied / total : 0
}

function knowledgeTitle(item: any) {
  return item.title || item.category || '酒店资料'
}

function historyTitle(item: any) {
  return item.title || item.moduleName || item.moduleKey || 'AI 生成内容'
}

function historySummary(item: any) {
  return item.summary || item.prompt || item.outputContent || item.output_content || '已生成内容'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(5, 16)
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-[1500px] space-y-5 pb-8">
    <section class="overflow-hidden rounded-3xl border border-cream-300 bg-white shadow-sm">
      <div class="grid xl:grid-cols-[minmax(0,1fr)_520px]">
        <div class="bg-bamboo-950 p-6 text-bamboo-50 lg:p-8">
          <div class="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-bamboo-100">
            <Sparkles class="h-3.5 w-3.5" />
            房型经营数据驱动的 AI 运营工作台
          </div>
          <h1 class="mt-5 max-w-3xl text-3xl font-semibold leading-tight">看清哪些房型卖得动，再让 AI 帮你定价和做营销</h1>
          <p class="mt-3 max-w-3xl text-sm leading-7 text-bamboo-100/75">上传历史房态表后，工作台会按日期和房型汇总占用、可售和出租率，后续 AI 分析也会优先参考这些已确认数据。</p>

          <div class="mt-7 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div class="hero-metric"><span>平均出租率</span><strong>{{ averageOccupancyRate }}</strong></div>
            <div class="hero-metric"><span>占用房晚</span><strong>{{ occupiedRoomNights }}</strong></div>
            <div class="hero-metric"><span>房型数</span><strong>{{ roomPerformance.length || hotel.roomTypes.length }}</strong></div>
            <div class="hero-metric"><span>数据周期</span><strong class="metric-small">{{ dateRangeText }}</strong></div>
          </div>
        </div>

        <div class="p-6">
          <div class="flex items-center justify-between gap-3">
            <div>
              <h2 class="text-base font-semibold text-bamboo-950">推荐动作</h2>
              <p class="mt-1 text-xs text-warm-500">从真实资料开始完成业务闭环</p>
            </div>
            <button class="icon-button" title="刷新" @click="load"><RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" /></button>
          </div>
          <div class="mt-4 grid gap-3 sm:grid-cols-2">
            <button v-for="action in quickActions" :key="action.route" class="action-card" @click="router.push(action.route)">
              <span class="action-icon"><component :is="action.icon" class="h-5 w-5" /></span>
              <span class="min-w-0"><strong>{{ action.title }}</strong><small>{{ action.desc }}</small></span>
            </button>
          </div>
        </div>
      </div>
    </section>

    <div v-if="loading" class="flex justify-center py-20"><Loader2 class="h-7 w-7 animate-spin text-bamboo-700" /></div>

    <template v-else>
      <section class="rounded-3xl border border-cream-300 bg-white p-4 shadow-sm">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
            <CalendarDays class="h-4 w-4" />
            日期范围
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <select v-model="dateFrom" class="filter-select">
              <option value="">开始：全部</option>
              <option v-for="date in dateOptions" :key="`from-${date}`" :value="date">{{ date }}</option>
            </select>
            <select v-model="dateTo" class="filter-select">
              <option value="">结束：全部</option>
              <option v-for="date in dateOptions" :key="`to-${date}`" :value="date">{{ date }}</option>
            </select>
            <button class="filter-button" :disabled="!usingFilteredRange" @click="resetDateRange">默认全部</button>
          </div>
        </div>
      </section>

      <section class="dashboard-summary-row grid items-start gap-5 xl:grid-cols-[minmax(0,1.08fr)_minmax(420px,0.92fr)]">
        <div class="room-table-card rounded-3xl border border-cream-300 bg-white shadow-sm">
          <div class="section-head">
            <div>
              <h2>房型经营表现</h2>
              <p>按当前日期范围汇总占用、可售和出租率。</p>
            </div>
            <button class="text-xs font-semibold text-bamboo-700" @click="router.push('/setup')">上传表格</button>
          </div>
          <div v-if="hasOccupancyData" class="max-h-[430px] overflow-auto px-5 pb-5">
            <table class="w-full min-w-[720px] text-left text-sm">
              <thead class="text-xs text-warm-500">
                <tr class="border-b border-cream-200">
                  <th class="py-3 font-semibold">房型</th>
                  <th class="py-3 font-semibold">房量</th>
                  <th class="py-3 font-semibold">占用房晚</th>
                  <th class="py-3 font-semibold">剩余房晚</th>
                  <th class="py-3 font-semibold">平均出租率</th>
                  <th class="py-3 font-semibold">最近占用/可售</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-cream-100">
                <tr v-for="room in roomPerformance" :key="room.roomTypeName" class="text-warm-700">
                  <td class="py-3 font-semibold text-bamboo-950">{{ room.roomTypeName }}</td>
                  <td class="py-3">{{ room.totalRooms }}</td>
                  <td class="py-3">{{ room.occupiedRoomNights }}</td>
                  <td class="py-3">{{ room.remainingRoomNights }}</td>
                  <td class="py-3"><span class="rate-pill">{{ Math.round(room.averageOccupancyRate * 100) }}%</span></td>
                  <td class="py-3">{{ room.latestOccupiedRooms }} / {{ room.latestRemainingRooms }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state">
            <Table2 class="h-8 w-8" />
            <span>还没有历史房态数据</span>
            <button @click="router.push('/setup')">去上传表格</button>
          </div>
        </div>

        <div class="summary-card rounded-3xl border border-cream-300 bg-white shadow-sm">
          <div class="section-head">
            <div>
              <h2>经营判断摘要</h2>
              <p>用于快速决定定价和营销优先级。</p>
            </div>
            <Percent class="h-4 w-4 text-bamboo-700" />
          </div>
          <div class="dashboard-summary-detail space-y-4 p-5">
            <div class="grid grid-cols-2 gap-3">
              <div class="plain-metric compact"><span>表现最好房型</span><strong>{{ bestRoom?.roomTypeName || '-' }}</strong></div>
              <div class="plain-metric compact"><span>待提升房型</span><strong>{{ slowRoom?.roomTypeName || '-' }}</strong></div>
              <div class="plain-metric compact"><span>剩余可售房晚</span><strong>{{ totalRemainingRoomNights }}</strong></div>
              <div class="plain-metric compact"><span>平均出租率</span><strong>{{ averageOccupancyRate }}</strong></div>
            </div>
            <div class="insight-panel">
              <div class="insight-title">经营提醒</div>
              <ul class="space-y-2">
                <li v-for="tip in summaryTips" :key="tip">{{ tip }}</li>
              </ul>
            </div>
            <div v-if="topRooms.length" class="grid gap-3 sm:grid-cols-2">
              <div class="room-rank-panel">
                <div class="rank-title">高出租率房型</div>
                <div v-for="room in topRooms" :key="`top-${room.roomTypeName}`" class="rank-row">
                  <span>{{ room.roomTypeName }}</span>
                  <strong>{{ Math.round(room.averageOccupancyRate * 100) }}%</strong>
                </div>
              </div>
              <div class="room-rank-panel">
                <div class="rank-title">待补量房型</div>
                <div v-for="room in weakRooms" :key="`weak-${room.roomTypeName}`" class="rank-row">
                  <span>{{ room.roomTypeName }}</span>
                  <strong>{{ room.latestRemainingRooms }} 间</strong>
                </div>
              </div>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3 p-5">
            <div class="plain-metric"><span>表现最好房型</span><strong>{{ bestRoom?.roomTypeName || '-' }}</strong></div>
            <div class="plain-metric"><span>待提升房型</span><strong>{{ slowRoom?.roomTypeName || '-' }}</strong></div>
            <div class="plain-metric"><span>平均出租率</span><strong>{{ averageOccupancyRate }}</strong></div>
            <div class="plain-metric"><span>数据来源</span><strong>{{ occupancyImport?.sourceFileNames?.length || (occupancyImport ? 1 : 0) }}</strong></div>
          </div>
        </div>
      </section>

      <section class="grid items-stretch gap-5 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <div class="analysis-card room-rate-card rounded-3xl border border-cream-300 bg-white p-5 shadow-sm">
          <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
            <BarChart3 class="h-4 w-4" />
            房型出租率对比
          </div>
          <div v-if="roomPerformance.length" class="room-chart-content mt-4">
            <div class="grid gap-3 sm:grid-cols-3">
              <div class="trend-stat"><span>高表现</span><strong>{{ roomRateBuckets.high }}</strong><small>≥ 50%</small></div>
              <div class="trend-stat"><span>观察中</span><strong>{{ roomRateBuckets.medium }}</strong><small>25%-49%</small></div>
              <div class="trend-stat"><span>待补量</span><strong>{{ roomRateBuckets.low }}</strong><small>&lt; 25%</small></div>
            </div>
            <div class="room-rate-scroll mt-4 grid gap-3 pr-1 md:grid-cols-2">
              <div v-for="room in roomPerformance" :key="room.roomTypeName" class="room-bar-card">
                <div class="mb-2 flex items-center justify-between gap-3 text-xs">
                  <span class="truncate font-semibold text-bamboo-950">{{ room.roomTypeName }}</span>
                  <span class="font-semibold text-bamboo-800">{{ Math.round(room.averageOccupancyRate * 100) }}%</span>
                </div>
                <div class="h-2.5 overflow-hidden rounded-full bg-cream-100">
                  <div class="h-full rounded-full bg-bamboo-700" :style="{ width: `${Math.round(room.averageOccupancyRate * 100)}%` }" />
                </div>
                <div class="mt-2 flex items-center justify-between text-[11px] font-semibold text-warm-500">
                  <span>占用 {{ room.occupiedRoomNights }}</span>
                  <span>剩余 {{ room.remainingRoomNights }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state compact">暂无图表数据</div>
        </div>

        <div class="analysis-card rounded-3xl border border-cream-300 bg-white p-5 shadow-sm">
          <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
            <TrendingUp class="h-4 w-4" />
            日期出租率趋势
          </div>
          <div v-if="dailyTrend.length" class="dashboard-trend-detail mt-4 space-y-4">
            <div class="grid gap-3 sm:grid-cols-3">
              <div class="trend-stat"><span>峰值日期</span><strong>{{ peakDay?.date || '-' }}</strong><small>{{ peakDay ? `${Math.round(peakDay.rate * 100)}%` : '-' }}</small></div>
              <div class="trend-stat"><span>低点日期</span><strong>{{ lowDay?.date || '-' }}</strong><small>{{ lowDay ? `${Math.round(lowDay.rate * 100)}%` : '-' }}</small></div>
              <div class="trend-stat"><span>周期均值</span><strong>{{ averageOccupancyRate }}</strong><small>{{ occupiedRoomNights }} / {{ totalRoomNights }}</small></div>
            </div>
            <div class="flex h-36 items-end gap-2 overflow-x-auto rounded-2xl bg-cream-50 px-4 pb-7 pt-4">
              <div v-for="day in dailyTrend" :key="day.date" class="flex min-w-10 flex-1 flex-col items-center justify-end gap-2">
                <div class="w-full rounded-t-lg bg-bamboo-700" :style="{ height: `${Math.max(10, Math.round(day.rate * 130))}px` }" />
                <span class="text-[10px] text-warm-500">{{ day.date }}</span>
              </div>
            </div>
            <div class="daily-chip-grid grid gap-2 sm:grid-cols-3">
              <div v-for="day in dailyTrend" :key="`detail-${day.date}`" class="daily-chip">
                <span>{{ day.date }}</span>
                <strong>{{ Math.round(day.rate * 100) }}%</strong>
                <small>{{ day.occupied }} / {{ day.total }}</small>
              </div>
            </div>
          </div>
          <div v-if="dailyTrend.length" class="mt-5 flex h-52 items-end gap-2 overflow-x-auto rounded-2xl bg-cream-50 px-4 pb-8 pt-4">
            <div v-for="day in dailyTrend" :key="day.date" class="flex min-w-10 flex-1 flex-col items-center justify-end gap-2">
              <div class="w-full rounded-t-lg bg-bamboo-700" :style="{ height: `${Math.max(8, Math.round(day.rate * 150))}px` }" />
              <span class="text-[10px] text-warm-500">{{ day.date }}</span>
            </div>
          </div>
          <div v-else class="empty-state compact">暂无趋势数据</div>
        </div>
      </section>

      <section class="grid gap-5 xl:grid-cols-2">
        <div class="rounded-3xl border border-cream-300 bg-white shadow-sm">
          <div class="section-head">
            <div><h2>最近确认的酒店资料</h2><p>这些内容会进入 AI 上下文。</p></div>
            <button class="text-xs font-semibold text-bamboo-700" @click="router.push('/knowledge')">资料中心</button>
          </div>
          <div v-if="latestKnowledge.length" class="divide-y divide-cream-200">
            <button v-for="item in latestKnowledge" :key="item.id" class="list-row" @click="router.push('/knowledge')">
              <span class="list-icon"><BookOpenCheck class="h-4 w-4" /></span>
              <span class="min-w-0 flex-1"><strong>{{ knowledgeTitle(item) }}</strong><small>{{ item.content || item.summary || item.value || '已确认资料' }}</small></span>
              <ArrowRight class="h-4 w-4 text-warm-300" />
            </button>
          </div>
          <div v-else class="empty-state compact"><BookOpenCheck class="h-8 w-8" /><span>还没有已确认资料</span></div>
        </div>

        <div class="rounded-3xl border border-cream-300 bg-white shadow-sm">
          <div class="section-head">
            <div><h2>最近 AI 生成</h2><p>生成结果可在历史中继续复用。</p></div>
            <button class="text-xs font-semibold text-bamboo-700" @click="router.push('/history')">全部历史</button>
          </div>
          <div v-if="latestHistory.length" class="divide-y divide-cream-200">
            <button v-for="item in latestHistory" :key="item.id" class="list-row" @click="router.push('/history')">
              <span class="list-icon"><Bot class="h-4 w-4" /></span>
              <span class="min-w-0 flex-1"><strong>{{ historyTitle(item) }}</strong><small>{{ historySummary(item) }}</small></span>
              <span class="shrink-0 text-[11px] text-warm-400">{{ formatTime(item.createdAt || item.created_at) }}</span>
            </button>
          </div>
          <div v-else class="empty-state compact"><History class="h-8 w-8" /><span>还没有生成记录</span></div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.hero-metric { border: 1px solid rgb(255 255 255 / 0.1); border-radius: 0.85rem; background: rgb(255 255 255 / 0.08); padding: 0.9rem; }
.hero-metric span { display: block; color: rgb(220 237 215 / 0.65); font-size: 0.72rem; }
.hero-metric strong { display: block; margin-top: 0.45rem; color: white; font-size: 1.45rem; line-height: 1.2; }
.hero-metric .metric-small { font-size: 1.05rem; }
.icon-button { display: inline-flex; height: 2.1rem; width: 2.1rem; align-items: center; justify-content: center; border: 1px solid #eadfce; border-radius: 0.7rem; color: #776655; }
.icon-button:hover { border-color: #8cac77; background: #f7faf4; }
.action-card { display: flex; align-items: center; gap: 0.75rem; min-height: 86px; border: 1px solid #eadfce; border-radius: 0.85rem; background: #fbf8f3; padding: 0.9rem; text-align: left; transition: 150ms ease; }
.action-card:hover { transform: translateY(-1px); border-color: #8cac77; background: white; box-shadow: 0 5px 16px rgb(54 79 50 / 0.08); }
.action-icon,.list-icon { display: flex; height: 2.25rem; width: 2.25rem; flex-shrink: 0; align-items: center; justify-content: center; border-radius: 0.65rem; background: #edf5e9; color: #315b37; }
.action-card strong,.list-row strong { display: block; color: #203f2b; font-size: 0.86rem; }
.action-card small,.list-row small { display: block; margin-top: 0.25rem; overflow: hidden; color: #9b8976; font-size: 0.72rem; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem; border-bottom: 1px solid #f0e7dc; padding: 1rem 1.25rem; }
.section-head h2 { color: #203f2b; font-size: 0.95rem; font-weight: 700; }
.section-head p { margin-top: 0.2rem; color: #9b8976; font-size: 0.72rem; }
.dashboard-summary-row { display: block; }
.room-table-card { display: none; }
.summary-card { width: 100%; }
.summary-card .dashboard-summary-detail { display: grid; grid-template-columns: minmax(260px,0.9fr) minmax(360px,1.25fr) minmax(320px,1fr); gap: 1rem; padding: 1rem 1.25rem 1.25rem; }
.summary-card .dashboard-summary-detail > * { margin-top: 0 !important; }
.summary-card .dashboard-summary-detail > .grid:first-child { grid-template-columns: repeat(2,minmax(0,1fr)); align-content: start; }
.summary-card .insight-panel { min-height: 100%; }
.summary-card .dashboard-summary-detail > .grid:last-child { align-content: start; }
.dashboard-summary-detail + .grid { display: none; }
.dashboard-trend-detail + .mt-5 { display: none; }
.plain-metric { border: 1px solid #f0e7dc; border-radius: 0.85rem; background: #fbf8f3; padding: 1rem; }
.plain-metric.compact { min-height: 84px; padding: 0.85rem; }
.plain-metric span { display: block; color: #9b8976; font-size: 0.72rem; }
.plain-metric strong { display: block; margin-top: 0.35rem; color: #234d32; font-size: 1.25rem; line-height: 1.25; }
.insight-panel { border: 1px solid #dbe9d2; border-radius: 0.85rem; background: #f7fbf4; padding: 0.9rem; }
.insight-title,.rank-title { color: #203f2b; font-size: 0.78rem; font-weight: 800; }
.insight-panel ul { margin-top: 0.65rem; color: #6f5e4e; font-size: 0.74rem; line-height: 1.55; }
.insight-panel li { position: relative; padding-left: 0.8rem; }
.insight-panel li::before { position: absolute; left: 0; top: 0.58rem; height: 0.28rem; width: 0.28rem; border-radius: 999px; background: #315b37; content: ''; }
.room-rank-panel { border: 1px solid #f0e7dc; border-radius: 0.85rem; background: #fffdf9; padding: 0.85rem; }
.rank-row { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; border-top: 1px solid #f4ede3; padding-top: 0.55rem; margin-top: 0.55rem; color: #6f5e4e; font-size: 0.74rem; }
.rank-row span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-row strong { flex-shrink: 0; color: #234d32; font-weight: 800; }
.analysis-card { overflow: hidden; }
.room-rate-card { display: flex; height: 100%; flex-direction: column; }
.room-chart-content { display: grid; gap: 1rem; }
.room-rate-card .room-chart-content { min-height: 0; flex: 1; grid-template-rows: auto minmax(0, 1fr); }
.room-rate-scroll { min-height: 0; overflow-y: auto; overscroll-behavior: contain; }
.room-rate-scroll::-webkit-scrollbar { width: 6px; }
.room-rate-scroll::-webkit-scrollbar-thumb { border-radius: 999px; background: #d8cdbc; }
.room-bar-card { border: 1px solid #f0e7dc; border-radius: 0.8rem; background: #fffdf9; padding: 0.85rem; }
.room-bar-card:hover { border-color: #dbe9d2; background: #f7fbf4; }
.dashboard-trend-detail { display: grid; gap: 1rem; }
.trend-stat,.daily-chip { border: 1px solid #f0e7dc; border-radius: 0.8rem; background: #fbf8f3; padding: 0.75rem; }
.trend-stat span,.daily-chip span { display: block; color: #9b8976; font-size: 0.68rem; font-weight: 700; }
.trend-stat strong,.daily-chip strong { display: block; margin-top: 0.25rem; color: #234d32; font-size: 1rem; line-height: 1.2; }
.trend-stat small,.daily-chip small { display: block; margin-top: 0.15rem; color: #b88324; font-size: 0.68rem; font-weight: 700; }
.daily-chip-grid { max-height: 190px; overflow-y: auto; padding-right: 0.25rem; }
.daily-chip-grid::-webkit-scrollbar { width: 6px; }
.daily-chip-grid::-webkit-scrollbar-thumb { border-radius: 999px; background: #d8cdbc; }
.filter-select,.filter-button { height: 2.35rem; border: 1px solid #eadfce; border-radius: 0.75rem; background: white; padding: 0 0.8rem; color: #315b37; font-size: 0.78rem; font-weight: 600; }
.filter-button:disabled { opacity: 0.45; }
.rate-pill { border-radius: 999px; background: #edf5e9; padding: 0.25rem 0.65rem; font-weight: 700; color: #234d32; }
.list-row { display: flex; width: 100%; align-items: center; gap: 0.75rem; padding: 0.85rem 1.25rem; text-align: left; transition: 150ms ease; }
.list-row:hover { background: #fbf8f3; }
.empty-state { display: flex; min-height: 190px; flex-direction: column; align-items: center; justify-content: center; gap: 0.6rem; color: #b4a38f; font-size: 0.78rem; }
.empty-state.compact { min-height: 160px; }
.empty-state button { color: #315b37; font-weight: 700; }
@media (max-width: 1280px) {
  .summary-card .dashboard-summary-detail { grid-template-columns: 1fr; }
}
</style>
