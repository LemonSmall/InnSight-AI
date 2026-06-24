<script setup lang="ts">
import { ref } from 'vue'
import { X, Camera, Loader2, CheckCircle2, ArrowRight } from 'lucide-vue-next'
import { useHotelStore, type FutureDailyStatus } from '@/stores/hotel'

const emit = defineEmits<{ close: [] }>()
const store = useHotelStore()

const scanning = ref(false)
const done = ref(false)
const progress = ref(0)

// 模拟解析出的7天数据
const parsedData = ref<FutureDailyStatus[]>([])

// 根据截图模拟的解析结果
const mockParsed: FutureDailyStatus[] = [
  { date: '06-03', dayOfWeek: '周三', totalOccupied: 25, totalAvailable: 54, rooms: [
    { name: '双床房', occupied: 2, available: 3, overbooked: 0 },
    { name: '大床房', occupied: 3, available: 3, overbooked: 0 },
    { name: '亲子房', occupied: 0, available: 12, overbooked: 0 },
    { name: '套房', occupied: 0, available: 2, overbooked: 0 },
    { name: '高级双床房', occupied: 6, available: 14, overbooked: 0 },
    { name: '高级大床房', occupied: 12, available: 17, overbooked: 0 },
    { name: '家庭房', occupied: 0, available: 3, overbooked: 0 },
    { name: '三人房', occupied: 2, available: 0, overbooked: 0 },
  ]},
  { date: '06-04', dayOfWeek: '周四', totalOccupied: 9, totalAvailable: 70, rooms: [
    { name: '双床房', occupied: 3, available: 2, overbooked: 0 },
    { name: '大床房', occupied: 0, available: 6, overbooked: 0 },
    { name: '亲子房', occupied: 0, available: 12, overbooked: 0 },
    { name: '套房', occupied: 0, available: 2, overbooked: 0 },
    { name: '高级双床房', occupied: 2, available: 18, overbooked: 0 },
    { name: '高级大床房', occupied: 2, available: 27, overbooked: 0 },
    { name: '家庭房', occupied: 0, available: 3, overbooked: 0 },
    { name: '三人房', occupied: 0, available: 0, overbooked: 0 },
  ]},
  { date: '06-05', dayOfWeek: '周五', totalOccupied: 16, totalAvailable: 63, rooms: [
    { name: '双床房', occupied: 3, available: 2, overbooked: 0 },
    { name: '大床房', occupied: 3, available: 3, overbooked: 0 },
    { name: '亲子房', occupied: 0, available: 12, overbooked: 0 },
    { name: '套房', occupied: 0, available: 2, overbooked: 0 },
    { name: '高级双床房', occupied: 5, available: 15, overbooked: 0 },
    { name: '高级大床房', occupied: 4, available: 25, overbooked: 0 },
    { name: '家庭房', occupied: 0, available: 3, overbooked: 0 },
    { name: '三人房', occupied: 1, available: 1, overbooked: 0 },
  ]},
  { date: '06-06', dayOfWeek: '周六', totalOccupied: 31, totalAvailable: 48, rooms: [
    { name: '双床房', occupied: 3, available: 2, overbooked: 0 },
    { name: '大床房', occupied: 3, available: 3, overbooked: 0 },
    { name: '亲子房', occupied: 4, available: 8, overbooked: 0 },
    { name: '套房', occupied: 2, available: 0, overbooked: 0 },
    { name: '高级双床房', occupied: 15, available: 5, overbooked: 0 },
    { name: '高级大床房', occupied: 3, available: 26, overbooked: 0 },
    { name: '家庭房', occupied: 0, available: 3, overbooked: 0 },
    { name: '三人房', occupied: 1, available: 1, overbooked: 0 },
  ]},
  { date: '06-07', dayOfWeek: '周日', totalOccupied: 21, totalAvailable: 58, rooms: [
    { name: '双床房', occupied: 2, available: 3, overbooked: 0 },
    { name: '大床房', occupied: 0, available: 6, overbooked: 0 },
    { name: '亲子房', occupied: 4, available: 8, overbooked: 0 },
    { name: '套房', occupied: 2, available: 0, overbooked: 0 },
    { name: '高级双床房', occupied: 12, available: 8, overbooked: 0 },
    { name: '高级大床房', occupied: 0, available: 29, overbooked: 0 },
    { name: '家庭房', occupied: 0, available: 3, overbooked: 0 },
    { name: '三人房', occupied: 1, available: 1, overbooked: 0 },
  ]},
  { date: '06-08', dayOfWeek: '周一', totalOccupied: 3, totalAvailable: 76, rooms: [
    { name: '双床房', occupied: 0, available: 5, overbooked: 0 },
    { name: '大床房', occupied: 0, available: 6, overbooked: 0 },
    { name: '亲子房', occupied: 0, available: 12, overbooked: 0 },
    { name: '套房', occupied: 2, available: 0, overbooked: 0 },
    { name: '高级双床房', occupied: 1, available: 19, overbooked: 0 },
    { name: '高级大床房', occupied: 0, available: 29, overbooked: 0 },
    { name: '家庭房', occupied: 0, available: 3, overbooked: 0 },
    { name: '三人房', occupied: 0, available: 2, overbooked: 0 },
  ]},
  { date: '06-09', dayOfWeek: '周二', totalOccupied: 3, totalAvailable: 76, rooms: [
    { name: '双床房', occupied: 0, available: 5, overbooked: 0 },
    { name: '大床房', occupied: 0, available: 6, overbooked: 0 },
    { name: '亲子房', occupied: 0, available: 12, overbooked: 0 },
    { name: '套房', occupied: 2, available: 0, overbooked: 0 },
    { name: '高级双床房', occupied: 1, available: 19, overbooked: 0 },
    { name: '高级大床房', occupied: 0, available: 29, overbooked: 0 },
    { name: '家庭房', occupied: 0, available: 3, overbooked: 0 },
    { name: '三人房', occupied: 0, available: 2, overbooked: 0 },
  ]},
]

function startScan() {
  scanning.value = true
  done.value = false
  progress.value = 0
  const iv = setInterval(() => {
    progress.value += Math.random() * 18 + 6
    if (progress.value > 100) progress.value = 100
    if (progress.value >= 100) {
      clearInterval(iv)
      done.value = true
      parsedData.value = JSON.parse(JSON.stringify(mockParsed))
    }
  }, 200)
}

function confirmScan() {
  store.saveFutureStatus(parsedData.value)
  const el = document.createElement('div')
  el.className = 'fixed top-4 right-4 bg-bamboo-100 text-bamboo-800 border border-bamboo-300 rounded-lg px-4 py-3 text-sm z-[999] font-medium shadow-lg'
  el.textContent = '✓ 未来7天房态已入库，大盘已更新'
  document.body.appendChild(el)
  setTimeout(() => el.remove(), 2500)
  emit('close')
}
</script>

<template>
  <div class="fixed inset-0 bg-bamboo-950/40 flex items-start justify-center z-50 p-4 overflow-auto" @click.self="$emit('close')">
    <div class="bg-white rounded-2xl p-6 w-full max-w-4xl shadow-xl relative mt-8 mb-8">
      <button @click="$emit('close')" class="absolute top-4 right-4 text-warm-600 hover:text-bamboo-800">
        <X class="w-5 h-5" />
      </button>

      <div class="text-base font-serif font-semibold text-bamboo-800 mb-5 flex items-center gap-2">
        <Camera class="w-5 h-5 text-bamboo-600" />
        上传未来7天房态图
      </div>

      <!-- Step 1: Upload -->
      <div v-if="!scanning" class="border-2 border-dashed border-cream-300 rounded-xl p-8 text-center cursor-pointer hover:border-bamboo-300 hover:bg-bamboo-50/30 transition-all" @click="startScan">
        <Camera class="w-8 h-8 text-warm-600/40 mx-auto mb-3" />
        <p class="text-sm text-warm-600">点击上传或拍照</p>
        <p class="text-xs text-warm-600/60 mt-1">支持 PMS截图 · 前台小票 · 手机拍照</p>
      </div>

      <!-- Step 2: Scanning -->
      <div v-else-if="!done" class="py-4">
        <div class="flex items-center gap-2 text-sm text-bamboo-700 mb-4">
          <Loader2 class="w-4 h-4 animate-spin" />
          AI识别中...
        </div>
        <div class="h-1.5 bg-cream-200 rounded-full overflow-hidden">
          <div class="h-full bg-bamboo-500 rounded-full transition-all duration-300" :style="{ width: progress + '%' }" />
        </div>
      </div>

      <!-- Step 3: Review & Confirm -->
      <div v-else>
        <div class="bg-bamboo-50 border-l-3 border-bamboo-500 rounded-r-lg p-4 mb-5 text-sm text-bamboo-800 leading-relaxed">
          <strong>识别完成！</strong> 已提取未来7天房态数据（06-03 至 06-09），请核对后确认入库。
        </div>

        <!-- 7-day summary cards -->
        <div class="grid grid-cols-7 gap-2 mb-5">
          <div v-for="d in parsedData" :key="d.date" class="text-center p-2 rounded-lg"
            :class="d.totalOccupied > 25 ? 'bg-rose-50 border border-rose-200' : d.totalOccupied > 15 ? 'bg-amber-50 border border-amber-200' : 'bg-bamboo-50 border border-bamboo-200'"
          >
            <div class="text-xs text-warm-600">{{ d.date }}</div>
            <div class="text-[10px] text-warm-500">{{ d.dayOfWeek }}</div>
            <div class="text-lg font-bold" :class="d.totalOccupied > 25 ? 'text-rose-600' : d.totalOccupied > 15 ? 'text-amber-600' : 'text-bamboo-700'">
              {{ d.totalOccupied }}
            </div>
            <div class="text-[10px] text-warm-500">占用</div>
          </div>
        </div>

        <!-- Detailed table -->
        <div class="overflow-x-auto mb-5">
          <table class="w-full text-xs border border-cream-300 rounded-lg overflow-hidden">
            <thead>
              <tr class="bg-cream-100">
                <th class="px-2 py-2 text-left text-warm-600 sticky left-0 bg-cream-100">房型</th>
                <th v-for="d in parsedData" :key="d.date" class="px-2 py-2 text-center text-warm-600 min-w-[80px]">
                  <div>{{ d.date }}</div>
                  <div class="text-[10px]">{{ d.dayOfWeek }}</div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="roomType in parsedData[0]?.rooms || []" :key="roomType.name" class="border-t border-cream-200/60">
                <td class="px-2 py-2 font-medium text-bamboo-800 sticky left-0 bg-white">{{ roomType.name }}</td>
                <td v-for="d in parsedData" :key="d.date + roomType.name" class="px-2 py-2 text-center">
                  <div class="flex flex-col items-center gap-0.5">
                    <span class="font-medium" :class="d.rooms.find(r => r.name === roomType.name)?.occupied ? 'text-rose-500' : 'text-warm-400'">
                      {{ d.rooms.find(r => r.name === roomType.name)?.occupied || 0 }}
                    </span>
                    <span class="text-[10px] text-warm-500">
                      {{ d.rooms.find(r => r.name === roomType.name)?.available || 0 }}
                    </span>
                  </div>
                </td>
              </tr>
              <tr class="border-t-2 border-cream-300 bg-cream-50 font-medium">
                <td class="px-2 py-2 text-bamboo-800 sticky left-0 bg-cream-50">总计</td>
                <td v-for="d in parsedData" :key="d.date" class="px-2 py-2 text-center">
                  <div class="flex flex-col items-center">
                    <span :class="d.totalOccupied > 25 ? 'text-rose-600' : 'text-bamboo-700'">{{ d.totalOccupied }}</span>
                    <span class="text-[10px] text-warm-500">{{ d.totalAvailable }}</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="flex gap-3">
          <button @click="confirmScan" class="btn-primary flex-1 justify-center">
            <CheckCircle2 class="w-4 h-4" />
            确认入库，更新大盘
          </button>
          <button @click="$emit('close')" class="btn-secondary">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>
