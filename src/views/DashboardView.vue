<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  FileText, LayoutGrid, BarChart3, Calendar, Cloud, CloudRain, Sun, CloudSun, ArrowUp, Activity
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'

const store = useHotelStore()
const router = useRouter()

// 初次加载时从后端拉取数据
onMounted(() => {
  store.loadFromApi()
})

const revenueFormatted = computed(() => {
  return store.totalRevenue.toLocaleString()
})

const freeCount = computed(() => {
  let c = 0
  for (const rs of store.roomStatuses) {
    c += rs.rooms.filter(r => r.status === 'free').length
  }
  return c
})

const repairCount = computed(() => {
  let c = 0
  for (const rs of store.roomStatuses) {
    c += rs.rooms.filter(r => r.status === 'repair').length
  }
  return c
})

interface RoomTypeStat {
  id: string
  name: string
  basePrice: number
  total: number
  sold: number
  free: number
  dirty: number
  repair: number
}

const roomTypeStats = computed<RoomTypeStat[]>(() => {
  return store.roomTypes.map(rt => {
    const st = store.roomStatuses.find(rs => rs.roomTypeId === rt.id)
    const rooms = st ? st.rooms : []
    return {
      id: rt.id,
      name: rt.name,
      basePrice: rt.basePrice,
      total: rooms.length,
      sold: rooms.filter(r => r.status === 'sold').length,
      free: rooms.filter(r => r.status === 'free').length,
      dirty: rooms.filter(r => r.status === 'dirty').length,
      repair: rooms.filter(r => r.status === 'repair').length,
    }
  })
})

const roomTypeTotal = computed(() => {
  let s = 0, f = 0, d = 0, r = 0
  for (const t of roomTypeStats.value) {
    s += t.sold; f += t.free; d += t.dirty; r += t.repair
  }
  return { sold: s, free: f, dirty: d, repair: r, total: s + f + d + r }
})

const futureStatus = computed(() => store.futureStatus)

const futureMaxOccupied = computed(() => {
  if (!futureStatus.value.length) return 1
  return Math.max(...futureStatus.value.map(d => d.totalOccupied))
})

const futurePeakDay = computed(() => {
  if (!futureStatus.value.length) return null
  return futureStatus.value.reduce((max, d) => d.totalOccupied > max.totalOccupied ? d : max)
})
</script>

<template>
  <div class="p-6 space-y-6">
    <!-- Page title + actions -->
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-bamboo-900">数字营销大盘</h1>
      <button @click="router.push('/room-status')" class="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-bamboo-800 text-bamboo-100 rounded-lg hover:bg-bamboo-900 transition-colors">
        <Activity class="w-3.5 h-3.5" />
        房态分析
      </button>
    </div>

    <!-- 2-column grid -->
    <div class="grid gap-6" style="grid-template-columns: 3fr 2fr;">
      <!-- ========== LEFT COLUMN ========== -->
      <div class="space-y-6">

        <!-- KPI Row -->
        <div class="grid grid-cols-4 gap-3">
          <!-- Card 1: Occupancy -->
          <div class="card !p-4">
            <div class="text-xs text-warm-600 mb-1">今日出租率</div>
            <div class="text-3xl font-bold text-bamboo-800">{{ store.occupancyRate }}%</div>
            <div class="flex items-center gap-1 mt-2 text-xs text-bamboo-600">
              <ArrowUp class="w-3 h-3" />
              <span>12%</span>
              <span class="text-warm-600 ml-1">vs 昨日</span>
            </div>
          </div>

          <!-- Card 2: Sold / Total -->
          <div class="card !p-4">
            <div class="text-xs text-warm-600 mb-1">已售 / 总房量</div>
            <div class="text-3xl font-bold text-bamboo-800">
              {{ store.totalSold }}<span class="text-lg text-warm-600 font-normal">/{{ store.totalRooms }}</span>
            </div>
            <div class="text-xs text-warm-600 mt-2">
              {{ freeCount }}间可售 · {{ repairCount }}间维修
            </div>
          </div>

          <!-- Card 3: Revenue -->
          <div class="card !p-4">
            <div class="text-xs text-warm-600 mb-1">今日预计营收</div>
            <div class="text-3xl font-bold text-bamboo-800">¥{{ revenueFormatted }}</div>
            <div class="flex items-center gap-1 mt-2 text-xs text-bamboo-600">
              <ArrowUp class="w-3 h-3" />
              <span>8%</span>
              <span class="text-warm-600 ml-1">同比</span>
            </div>
          </div>

          <!-- Card 4: RevPAR -->
          <div class="card !p-4">
            <div class="text-xs text-warm-600 mb-1">RevPAR</div>
            <div class="text-3xl font-bold text-bamboo-800">¥{{ store.revpar }}</div>
            <div class="text-xs text-warm-600 mt-2">单房收益</div>
          </div>
        </div>

        <!-- AI Daily Brief -->
        <div class="card">
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-2">
              <FileText class="w-4 h-4 text-bamboo-700" />
              <h2 class="text-sm font-semibold text-bamboo-900">今日早报 · AI诊断</h2>
            </div>
            <span class="badge badge-green">06:32生成</span>
          </div>

          <div class="space-y-3">
            <!-- Amber alert -->
            <div class="border-l-4 border-amber-400 bg-amber-50 rounded-r-lg p-3">
              <div class="text-sm text-warm-700 leading-relaxed">
                <span class="font-semibold">风险预警：</span>今日周四+小雨，散客冲动预订下降约20%，建议立即启动私域老客复购，推送"雨天温泉套餐"至微信群。
              </div>
            </div>

            <!-- Green alert -->
            <div class="border-l-4 border-bamboo-600 bg-bamboo-50 rounded-r-lg p-3">
              <div class="text-sm text-bamboo-800 leading-relaxed">
                <span class="font-semibold">今日核心动作：</span>进入端午蓄水期，上午10点前完成小红书+朋友圈端午海报推送，下午检查OTA端午房态同步。
              </div>
            </div>

            <!-- Purple alert -->
            <div class="border-l-4 border-purple-500 bg-purple-50 rounded-r-lg p-3">
              <div class="text-sm text-purple-800 leading-relaxed">
                <span class="font-semibold">定价指令：</span>端午3天房价上调10%挂牌，结合"赠粽礼+汉服体验"打包价，提升客单价至¥1,580-¥2,080区间。
              </div>
            </div>
          </div>
        </div>

        <!-- Real-time Room Status -->
        <div class="card">
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-2">
              <LayoutGrid class="w-4 h-4 text-bamboo-700" />
              <h2 class="text-sm font-semibold text-bamboo-900">实时房态</h2>
            </div>
            <span class="text-[11px] text-warm-500">共 {{ roomTypeTotal.total }} 间</span>
          </div>

          <!-- Room type stats bars (max-height scrollable for 10+ types) -->
          <div class="space-y-2.5 max-h-[360px] overflow-y-auto pr-1 -mr-1">
            <div v-for="stat in roomTypeStats" :key="stat.id">
              <div class="flex items-center justify-between mb-0.5">
                <div class="flex items-center gap-1.5 min-w-0">
                  <span class="text-[11px] font-medium text-bamboo-800 truncate">{{ stat.name }}</span>
                  <span class="text-[10px] text-warm-500 flex-shrink-0">¥{{ stat.basePrice }}</span>
                </div>
                <span class="text-[10px] text-warm-500 flex-shrink-0 ml-2">{{ stat.total }}间</span>
              </div>
              <!-- stacked bar -->
              <div class="w-full h-5 rounded overflow-hidden flex">
                <div v-if="stat.sold > 0" class="bg-bamboo-700 h-full flex items-center justify-center text-[9px] text-white font-medium"
                  :style="{ width: (stat.sold / stat.total * 100) + '%' }">
                  {{ stat.sold }}
                </div>
                <div v-if="stat.free > 0" class="bg-blue-400 h-full flex items-center justify-center text-[9px] text-white font-medium"
                  :style="{ width: (stat.free / stat.total * 100) + '%' }">
                  {{ stat.free }}
                </div>
                <div v-if="stat.dirty > 0" class="bg-rose-300 h-full flex items-center justify-center text-[9px] text-white font-medium"
                  :style="{ width: (stat.dirty / stat.total * 100) + '%' }">
                  {{ stat.dirty }}
                </div>
                <div v-if="stat.repair > 0" class="bg-warm-400 h-full flex items-center justify-center text-[9px] text-white font-medium"
                  :style="{ width: (stat.repair / stat.total * 100) + '%' }">
                  {{ stat.repair }}
                </div>
              </div>
            </div>
          </div>

          <!-- Total bar -->
          <div class="mt-4 pt-4 border-t border-cream-200/60">
            <div class="flex items-center justify-between mb-1">
              <span class="text-xs font-medium text-bamboo-900">总览</span>
              <span class="text-[11px] text-warm-500">{{ roomTypeTotal.total }}间</span>
            </div>
            <div class="w-full h-7 rounded-md overflow-hidden flex">
              <div v-if="roomTypeTotal.sold > 0" class="bg-bamboo-700 h-full flex items-center justify-center text-[10px] text-white font-medium"
                :style="{ width: (roomTypeTotal.sold / roomTypeTotal.total * 100) + '%' }">
                {{ roomTypeTotal.sold }}
              </div>
              <div v-if="roomTypeTotal.free > 0" class="bg-blue-400 h-full flex items-center justify-center text-[10px] text-white font-medium"
                :style="{ width: (roomTypeTotal.free / roomTypeTotal.total * 100) + '%' }">
                {{ roomTypeTotal.free }}
              </div>
              <div v-if="roomTypeTotal.dirty > 0" class="bg-rose-300 h-full flex items-center justify-center text-[10px] text-white font-medium"
                :style="{ width: (roomTypeTotal.dirty / roomTypeTotal.total * 100) + '%' }">
                {{ roomTypeTotal.dirty }}
              </div>
              <div v-if="roomTypeTotal.repair > 0" class="bg-warm-400 h-full flex items-center justify-center text-[10px] text-white font-medium"
                :style="{ width: (roomTypeTotal.repair / roomTypeTotal.total * 100) + '%' }">
                {{ roomTypeTotal.repair }}
              </div>
            </div>
          </div>
        </div>

      </div>

      <!-- ========== RIGHT COLUMN ========== -->
      <div class="space-y-6">

        <!-- 7-Day Occupancy Trend (from uploaded data) -->
        <div class="card">
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-2">
              <BarChart3 class="w-4 h-4 text-bamboo-700" />
              <h2 class="text-sm font-semibold text-bamboo-900">未来7天占用趋势</h2>
            </div>
            <span class="text-[10px] text-warm-500">来自房态图</span>
          </div>

          <div class="flex items-end justify-between gap-1 h-44">
            <div
              v-for="(d, i) in futureStatus"
              :key="d.date"
              class="flex flex-col items-center gap-1 flex-1"
            >
              <span class="text-xs font-medium"
                :class="d.totalOccupied > 25 ? 'text-rose-600' : d.totalOccupied > 15 ? 'text-amber-600' : 'text-bamboo-800'"
              >
                {{ d.totalOccupied }}
              </span>
              <div class="w-full rounded-t-md transition-all relative"
                :class="d.totalOccupied > 25 ? 'bg-rose-400' : d.totalOccupied > 15 ? 'bg-amber-400' : 'bg-bamboo-200'"
                :style="{ height: (d.totalOccupied / futureMaxOccupied * 120) + 'px' }"
              >
                <div v-if="d.totalOccupied === futurePeakDay?.totalOccupied" class="absolute -top-5 left-1/2 -translate-x-1/2 text-[9px] text-rose-500 whitespace-nowrap">
                  峰值
                </div>
              </div>
              <span class="text-[10px] text-warm-600">{{ d.date }}</span>
              <span class="text-[9px] text-warm-400">{{ d.dayOfWeek }}</span>
            </div>
          </div>

          <div class="mt-3 flex items-center justify-center gap-4 text-[10px] text-warm-600">
            <div class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-rose-400" />紧张（>25间）</div>
            <div class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-amber-400" />中等（16-25间）</div>
            <div class="flex items-center gap-1"><span class="w-2 h-2 rounded bg-bamboo-200" />宽松（<16间）</div>
          </div>
        </div>

        <!-- Dragon Boat Festival Progress -->
        <div class="card">
          <div class="flex items-center gap-2 mb-4">
            <Calendar class="w-4 h-4 text-bamboo-700" />
            <h2 class="text-sm font-semibold text-bamboo-900">端午备战进度</h2>
          </div>

          <div class="space-y-4">
            <!-- Progress 1 -->
            <div>
              <div class="flex justify-between text-xs mb-1.5">
                <span class="text-warm-600">端午预订率</span>
                <span class="text-bamboo-800 font-medium">22%<span class="text-warm-600 font-normal">/60%目标</span></span>
              </div>
              <div class="w-full bg-cream-200 rounded-full h-2">
                <div class="bg-rose-400 h-2 rounded-full" style="width: 22%" />
              </div>
            </div>

            <!-- Progress 2 -->
            <div>
              <div class="flex justify-between text-xs mb-1.5">
                <span class="text-warm-600">内容发布</span>
                <span class="text-bamboo-800 font-medium">2<span class="text-warm-600 font-normal">/5项</span></span>
              </div>
              <div class="w-full bg-cream-200 rounded-full h-2">
                <div class="bg-bamboo-600 h-2 rounded-full" style="width: 40%" />
              </div>
            </div>

            <!-- Progress 3 -->
            <div>
              <div class="flex justify-between text-xs mb-1.5">
                <span class="text-warm-600">本周好评任务</span>
                <span class="text-bamboo-800 font-medium">3<span class="text-warm-600 font-normal">/5条</span></span>
              </div>
              <div class="w-full bg-cream-200 rounded-full h-2">
                <div class="bg-blue-500 h-2 rounded-full" style="width: 60%" />
              </div>
            </div>
          </div>
        </div>

        <!-- Weekly Weather -->
        <div class="card">
          <div class="flex items-center gap-2 mb-4">
            <Cloud class="w-4 h-4 text-bamboo-700" />
            <h2 class="text-sm font-semibold text-bamboo-900">本周天气</h2>
          </div>

          <div class="flex justify-between">
            <div class="flex flex-col items-center gap-1">
              <CloudRain class="w-6 h-6 text-blue-400" />
              <span class="text-xs text-warm-600">今日</span>
              <span class="text-sm font-medium text-bamboo-800">19°</span>
              <span class="text-[10px] text-warm-400">雨</span>
            </div>
            <div class="flex flex-col items-center gap-1">
              <CloudRain class="w-6 h-6 text-blue-400" />
              <span class="text-xs text-warm-600">周五</span>
              <span class="text-sm font-medium text-bamboo-800">21°</span>
              <span class="text-[10px] text-warm-400">雨</span>
            </div>
            <div class="flex flex-col items-center gap-1 bg-bamboo-50 rounded-xl px-3 py-2 -mx-1">
              <Sun class="w-6 h-6 text-warm-400" />
              <span class="text-xs text-warm-600">周六</span>
              <span class="text-sm font-medium text-bamboo-800">28°</span>
              <span class="text-[10px] text-bamboo-600">晴</span>
            </div>
            <div class="flex flex-col items-center gap-1 bg-bamboo-50 rounded-xl px-3 py-2 -mx-1">
              <Sun class="w-6 h-6 text-warm-400" />
              <span class="text-xs text-warm-600">周日</span>
              <span class="text-sm font-medium text-bamboo-800">29°</span>
              <span class="text-[10px] text-bamboo-600">晴</span>
            </div>
            <div class="flex flex-col items-center gap-1">
              <CloudSun class="w-6 h-6 text-warm-400" />
              <span class="text-xs text-warm-600">周一</span>
              <span class="text-sm font-medium text-bamboo-800">24°</span>
              <span class="text-[10px] text-warm-400">多云</span>
            </div>
          </div>

          <div class="mt-4 bg-bamboo-50 border border-bamboo-200/60 rounded-lg p-3">
            <div class="text-xs text-bamboo-700 leading-relaxed">
              <span class="font-semibold">📈 天气利好：</span>周六日晴好，适合拉升房价10-15%，预计日增量价齐升，建议提前锁房控单。
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>
