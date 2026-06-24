<script setup lang="ts">
import { computed, ref, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Chart, registerables } from 'chart.js'
import {
  BedDouble, BarChart3, ArrowLeft,
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'

Chart.register(...registerables)

const store = useHotelStore()
const router = useRouter()

// ====== 图表 refs ======
const chartCanvas = ref<HTMLCanvasElement | null>(null)
let chartInstance: Chart | null = null

// ====== 数据计算 ======
const futureStatus = computed(() => store.futureStatus)

const totalRooms = computed(() => {
  if (!futureStatus.value.length) return 0
  return futureStatus.value[0].totalOccupied + futureStatus.value[0].totalAvailable
})

const peakDay = computed(() => {
  if (!futureStatus.value.length) return null
  return futureStatus.value.reduce((max, d) => d.totalOccupied > max.totalOccupied ? d : max)
})

const lowestDay = computed(() => {
  if (!futureStatus.value.length) return null
  return futureStatus.value.reduce((min, d) => d.totalOccupied < min.totalOccupied ? d : min)
})

const avgOccRate = computed(() => {
  if (!futureStatus.value.length) return 0
  const sum = futureStatus.value.reduce((s, d) => s + d.totalOccupied, 0)
  return Math.round(sum / futureStatus.value.length / totalRooms.value * 1000) / 10
})

// 峰值日房型分布
const peakRoomTypes = computed(() => {
  if (!peakDay.value) return []
  return peakDay.value.rooms
})

// ====== Chart ======
function buildChart() {
  if (!chartCanvas.value || !futureStatus.value.length) return

  if (chartInstance) chartInstance.destroy()

  const data = futureStatus.value
  const labels = data.map(d => d.date + '\n' + d.dayOfWeek)
  const occupied = data.map(d => d.totalOccupied)
  const available = data.map(d => d.totalAvailable)
  const occRate = occupied.map(o => Math.round(o / totalRooms.value * 100))

  chartInstance = new Chart(chartCanvas.value, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          type: 'line' as const,
          label: '出租率',
          data: occRate,
          borderColor: '#639922',
          backgroundColor: '#EAF3DE',
          borderWidth: 2,
          pointBackgroundColor: occRate.map(v => v >= 30 ? '#27500A' : v >= 15 ? '#EF9F27' : '#E24B4A'),
          pointBorderColor: '#fff',
          pointBorderWidth: 1.5,
          pointRadius: 5,
          tension: 0.35,
          yAxisID: 'y1',
          fill: false,
          order: 1,
        },
        {
          type: 'bar' as const,
          label: '占用房',
          data: occupied,
          backgroundColor: '#C0DD97',
          borderColor: '#639922',
          borderWidth: 0.5,
          borderRadius: 4,
          yAxisID: 'y',
          order: 2,
        },
        {
          type: 'bar' as const,
          label: '可售房',
          data: available,
          backgroundColor: '#E6F1FB',
          borderColor: '#85B7EB',
          borderWidth: 0.5,
          borderRadius: 4,
          yAxisID: 'y',
          order: 3,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#173404',
          titleColor: '#C0DD97',
          bodyColor: '#C0DD97',
          padding: 10,
          cornerRadius: 8,
          callbacks: {
            title: (items) => '　' + items[0].label.replace('\n', ' '),
            label: (item) => {
              if (item.dataset.label === '出租率') return '　出租率：' + item.parsed.y + '%'
              if (item.dataset.label === '占用房') return '　占用房：' + item.parsed.y + '间'
              return '　可售房：' + item.parsed.y + '间'
            },
          },
        },
      },
      scales: {
        x: {
          grid: { color: 'rgba(0,0,0,0.06)', lineWidth: 0.5 },
          ticks: { color: '#888780', font: { size: 11 } },
        },
        y: {
          grid: { color: 'rgba(0,0,0,0.06)', lineWidth: 0.5 },
          ticks: { color: '#888780', font: { size: 11 }, stepSize: 10, callback: (v) => v + '间' },
          min: 0,
          max: Math.ceil((totalRooms.value + 10) / 10) * 10,
          position: 'left',
        },
        y1: {
          grid: { display: false },
          ticks: { color: '#639922', font: { size: 11 }, callback: (v) => v + '%' },
          min: 0,
          max: Math.ceil((Math.max(...occRate) + 10) / 10) * 10,
          position: 'right',
        },
      },
    },
  })
}

onMounted(async () => {
  await nextTick()
  buildChart()
})

watch(futureStatus, () => {
  nextTick(() => buildChart())
})

// ====== 颜色工具 ======
function barColor(occ: number, total: number): string {
  const rate = occ / total
  if (rate >= 0.75) return '#E24B4A'
  if (rate >= 0.33) return '#EF9F27'
  return '#639922'
}

function textColor(occ: number, total: number): string {
  const rate = occ / total
  if (rate >= 0.75) return '#A32D2D'
  if (rate >= 0.33) return '#633806'
  return '#27500A'
}
</script>

<template>
  <div class="p-6 space-y-5">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <button @click="router.push('/dashboard')" class="w-7 h-7 flex items-center justify-center rounded-lg border border-cream-300 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors">
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-lg font-semibold text-bamboo-900">房态分析</h1>
          <p class="text-xs text-warm-500 mt-0.5">未来7天房态趋势与各房型占用分布</p>
        </div>
      </div>
    </div>

    <!-- KPI Row -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <div class="bg-cream-50 rounded-lg px-4 py-3">
        <div class="text-xl font-bold text-bamboo-800">
          {{ peakDay?.totalOccupied ?? 0 }}间
          <span class="text-xs font-normal text-warm-500 ml-1">/{{ totalRooms }}间</span>
        </div>
        <div class="text-[11px] text-warm-500 mt-1">
          {{ peakDay?.date }} {{ peakDay?.dayOfWeek }} 最高占用
        </div>
        <div class="text-[11px] text-amber-600 mt-0.5">
          出租率 {{ peakDay ? Math.round(peakDay.totalOccupied / totalRooms * 100) : 0 }}%
        </div>
      </div>

      <div class="bg-cream-50 rounded-lg px-4 py-3">
        <div class="text-xl font-bold text-rose-600">
          {{ lowestDay?.totalOccupied ?? 0 }}间
        </div>
        <div class="text-[11px] text-warm-500 mt-1">
          {{ lowestDay?.date }} {{ lowestDay?.dayOfWeek }} 最低占用
        </div>
        <div class="text-[11px] text-rose-500 mt-0.5">
          出租率仅 {{ lowestDay ? Math.round(lowestDay.totalOccupied / totalRooms * 100) : 0 }}%
        </div>
      </div>

      <div class="bg-cream-50 rounded-lg px-4 py-3">
        <div class="text-xl font-bold text-bamboo-800">{{ avgOccRate }}%</div>
        <div class="text-[11px] text-warm-500 mt-1">7天平均出租率</div>
        <div class="text-[11px] text-rose-500 mt-0.5" v-if="avgOccRate < 30">整体偏低需警惕</div>
        <div class="text-[11px] text-bamboo-600 mt-0.5" v-else>表现稳健</div>
      </div>

      <div class="bg-cream-50 rounded-lg px-4 py-3">
        <div class="text-xl font-bold text-amber-700">{{ totalRooms }}间</div>
        <div class="text-[11px] text-warm-500 mt-1">总可用房量</div>
        <div class="text-[11px] text-warm-500 mt-0.5">超额数均为 0</div>
      </div>
    </div>

    <!-- Chart -->
    <div class="card">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-2">
          <BarChart3 class="w-4 h-4 text-blue-600" />
          <h2 class="text-sm font-semibold text-bamboo-900">7天出租率趋势 · 06/03–06/09</h2>
        </div>
        <div class="flex gap-3 text-[10px] text-warm-500">
          <span class="flex items-center gap-1">
            <span class="w-4 h-0.5 rounded bg-bamboo-600" /> 出租率%
          </span>
          <span class="flex items-center gap-1">
            <span class="w-2 h-2 rounded-full bg-bamboo-800" /> 占用房
          </span>
          <span class="flex items-center gap-1">
            <span class="w-2 h-2 rounded-full bg-blue-100 border border-blue-400" /> 可售房
          </span>
        </div>
      </div>
      <div class="relative h-[220px]">
        <canvas ref="chartCanvas" />
      </div>
    </div>

    <!-- 峰值日房型分布 -->
    <div class="card">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-2">
          <BedDouble class="w-4 h-4 text-purple-600" />
          <h2 class="text-sm font-semibold text-bamboo-900">
            各房型占用分布 · 峰值日（{{ peakDay?.date }} {{ peakDay?.dayOfWeek }}）
          </h2>
        </div>
        <span class="text-[10px] font-medium bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full">
          {{ peakDay?.dayOfWeek }}出租率最高 {{ peakDay ? Math.round(peakDay.totalOccupied / totalRooms * 100) : 0 }}%
        </span>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <div
          v-for="rt in peakRoomTypes"
          :key="rt.name"
          class="bg-cream-50 rounded-lg px-3 py-2.5"
        >
          <div class="text-[11px] text-warm-600 mb-1.5 truncate">{{ rt.name }}</div>
          <div class="h-1.5 bg-cream-200 rounded-full mb-1.5 overflow-hidden">
            <div
              class="h-full rounded-full transition-all"
              :style="{
                width: (rt.occupied / (rt.occupied + rt.available) * 100) + '%',
                backgroundColor: barColor(rt.occupied, rt.occupied + rt.available),
              }"
            />
          </div>
          <div class="flex justify-between text-[10px]">
            <span class="font-medium" :style="{ color: textColor(rt.occupied, rt.occupied + rt.available) }">
              {{ rt.occupied }}间
            </span>
            <span class="text-warm-500">共{{ rt.occupied + rt.available }}间</span>
          </div>
        </div>
      </div>
    </div>

    <!-- AI 诊断 -->
    <div class="space-y-2">
      <div class="border-l-3 border-amber-400 bg-amber-50 rounded-r-lg p-4 text-xs text-warm-700 leading-relaxed">
        <strong class="text-amber-800">AI诊断：</strong>
        工作日（周一/二）出租率仅4%，严重空置。高级大床房共29间，周六占用仅3间（10%），是最大的浪费资产，建议优先针对该房型设计工作日特惠套餐。
      </div>
      <div class="border-l-3 border-bamboo-600 bg-bamboo-50 rounded-r-lg p-4 text-xs text-bamboo-800 leading-relaxed">
        <strong class="text-bamboo-900">建议行动：</strong>
        本周末（{{ peakDay?.date }} {{ peakDay?.dayOfWeek }}）出租率达到峰值{{ peakDay ? Math.round(peakDay.totalOccupied / totalRooms * 100) : 0 }}%，可适当拉升高级双床房和套房价格5-10%；同时提前为下周工作日备货「闪购特惠」，联动美团/携程限时活动激活空置资产。
      </div>
    </div>
  </div>
</template>
