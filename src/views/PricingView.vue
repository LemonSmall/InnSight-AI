<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useHotelStore } from '@/stores/hotel'
import { getPricing } from '@/api/hotel'
import { Coins, TrendingUp, TrendingDown, Sparkles } from 'lucide-vue-next'

const store = useHotelStore()

const form = reactive({
  occupancy: '',
  holiday: '',
  weather: '',
  competition: '',
})

const showResult = ref(false)

const occupancyOptions = [
  { value: '90+', label: '90%以上' },
  { value: '70-90', label: '70%-90%' },
  { value: '50-70', label: '50%-70%' },
  { value: '30-50', label: '30%-50%' },
  { value: '30-', label: '30%以下' },
]

const holidayOptions = [
  { value: 'big', label: '大长假（春节/国庆）' },
  { value: 'small', label: '小长假（端午/清明/五一/中秋）' },
  { value: 'weekend', label: '周末' },
  { value: 'emotion', label: '情感节（情人节/七夕/圣诞）' },
  { value: 'normal', label: '非节假日' },
]

const weatherOptions = [
  { value: 'sunny', label: '晴天/多云' },
  { value: 'rain', label: '小雨/阵雨' },
  { value: 'heavy', label: '大雨/暴雨' },
  { value: 'extreme', label: '台风/极端天气' },
]

const competitionOptions = [
  { value: 'none', label: '无竞争压力（周边无同类民宿调价）' },
  { value: 'light', label: '轻微竞争（个别竞品小幅调价）' },
  { value: 'medium', label: '中度竞争（多家竞品有促销活动）' },
  { value: 'high', label: '高竞争压力（竞品大幅降价抢客）' },
]

interface PricingResult {
  roomId: string
  roomName: string
  basePrice: number
  recommendedPrice: number
  changePercent: number
}

interface ReasonItem {
  text: string
}

const pricingResults = ref<PricingResult[]>([])
const reasons = ref<ReasonItem[]>([])

function getHolidayMultiplier(): number {
  switch (form.holiday) {
    case 'big': return 1.28
    case 'small': return 1.15
    case 'weekend': return 1.1
    case 'emotion': return 1.2
    default: return 1.0
  }
}

function getOccupancyAdjust(): number {
  switch (form.occupancy) {
    case '90+': return 0.10
    case '70-90': return 0.05
    case '50-70': return 0
    case '30-50': return -0.08
    case '30-': return -0.18
    default: return 0
  }
}

function getWeatherAdjust(): number {
  switch (form.weather) {
    case 'sunny': return 0
    case 'rain': return -0.08
    case 'heavy': return -0.15
    case 'extreme': return -0.22
    default: return 0
  }
}

function getCompetitionAdjust(): number {
  switch (form.competition) {
    case 'none': return 0.05
    case 'light': return 0
    case 'medium': return -0.10
    case 'high': return -0.15
    default: return 0
  }
}

function getHolidayLabel(h: string): string {
  return holidayOptions.find(o => o.value === h)?.label || '非节假日'
}

function getOccupancyLabel(o: string): string {
  return occupancyOptions.find(op => op.value === o)?.label || '50%-70%'
}

function getWeatherLabel(w: string): string {
  return weatherOptions.find(op => op.value === w)?.label || '晴天/多云'
}

function getCompetitionLabel(c: string): string {
  return competitionOptions.find(op => op.value === c)?.label || '无竞争压力'
}

async function generatePricing() {
  showResult.value = false

  try {
    const { data: res } = await getPricing({
      holiday: form.holiday,
      occupancy: form.occupancy,
      weather: form.weather,
      competition: form.competition,
    })
    const d = res.data || res

    if (d.results) {
      pricingResults.value = d.results.map((r: any) => ({
        roomId: String(r.roomId),
        roomName: r.roomName,
        basePrice: r.basePrice,
        recommendedPrice: r.recommendedPrice,
        changePercent: r.changePercent,
      }))
    }
    if (d.reasons) {
      reasons.value = d.reasons.map((t: string) => ({ text: t }))
    }

    showResult.value = true
  } catch {
    // 回退到本地计算
    const holiday = getHolidayMultiplier()
    const occupancy = getOccupancyAdjust()
    const weather = getWeatherAdjust()
    const competition = getCompetitionAdjust()

    const results: PricingResult[] = store.roomTypes.map(rt => {
      const totalAdjust = 1 + occupancy + weather + competition
      const recommendedPrice = Math.round(rt.basePrice * holiday * totalAdjust)
      const changePercent = Math.round((recommendedPrice / rt.basePrice - 1) * 100)
      return { roomId: rt.id, roomName: rt.name, basePrice: rt.basePrice, recommendedPrice, changePercent }
    })
    pricingResults.value = results
    reasons.value = [
      { text: `节假日类型为「${getHolidayLabel(form.holiday)}」，基础倍率 ×${holiday}` },
      { text: `(后端未连接，使用本地计算)` },
    ]
    showResult.value = true
  }
}

const allFilled = computed(() => {
  return form.occupancy && form.holiday && form.weather && form.competition
})
</script>

<template>
  <div class="max-w-4xl mx-auto p-6 space-y-6">
    <!-- Title -->
    <div class="flex items-center gap-3">
      <Coins class="w-5 h-5 text-bamboo-700" />
      <h1 class="text-lg font-semibold text-bamboo-900">智能定价</h1>
    </div>

    <!-- Input Card -->
    <div class="card space-y-5">
      <h2 class="text-base font-semibold text-bamboo-800">定价参数设置</h2>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="label">当前入住率</label>
          <select v-model="form.occupancy" class="input-field">
            <option value="" disabled>请选择入住率区间</option>
            <option v-for="o in occupancyOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </div>

        <div>
          <label class="label">节假日类型</label>
          <select v-model="form.holiday" class="input-field">
            <option value="" disabled>请选择节假日类型</option>
            <option v-for="h in holidayOptions" :key="h.value" :value="h.value">{{ h.label }}</option>
          </select>
        </div>

        <div>
          <label class="label">天气情况</label>
          <select v-model="form.weather" class="input-field">
            <option value="" disabled>请选择天气情况</option>
            <option v-for="w in weatherOptions" :key="w.value" :value="w.value">{{ w.label }}</option>
          </select>
        </div>

        <div>
          <label class="label">周边竞争</label>
          <select v-model="form.competition" class="input-field">
            <option value="" disabled>请选择竞争程度</option>
            <option v-for="c in competitionOptions" :key="c.value" :value="c.value">{{ c.label }}</option>
          </select>
        </div>
      </div>

      <button
        class="btn-primary"
        :disabled="!allFilled"
        :class="!allFilled ? 'opacity-50 cursor-not-allowed' : ''"
        @click="generatePricing"
      >
        <Sparkles class="w-4 h-4" />
        生成智能定价建议
      </button>
    </div>

    <!-- Result Card -->
    <div v-if="showResult" class="card space-y-6">
      <h2 class="text-base font-semibold text-bamboo-800 flex items-center gap-2">
        <Sparkles class="w-4 h-4 text-warm-400" />
        AI定价建议
      </h2>

      <!-- Price Cards Row -->
      <div class="grid grid-cols-3 gap-4">
        <div
          v-for="r in pricingResults"
          :key="r.roomId"
          class="bg-cream-50 border border-cream-200 rounded-lg p-4 text-center space-y-2"
        >
          <div class="text-sm font-medium text-warm-600">{{ r.roomName }}</div>
          <div class="text-3xl font-bold text-bamboo-800">¥{{ r.recommendedPrice }}</div>
          <div class="text-xs text-warm-600 line-through">原价 ¥{{ r.basePrice }}</div>
          <div
            :class="[
              'inline-flex items-center gap-1 text-sm font-semibold rounded-full px-2.5 py-0.5',
              r.changePercent >= 0
                ? 'text-rose-500 bg-rose-50'
                : 'text-bamboo-700 bg-bamboo-100'
            ]"
          >
            <TrendingUp v-if="r.changePercent >= 0" class="w-3.5 h-3.5" />
            <TrendingDown v-else class="w-3.5 h-3.5" />
            {{ r.changePercent >= 0 ? '+' : '' }}{{ r.changePercent }}%
          </div>
        </div>
      </div>

      <!-- Reasons -->
      <div class="border-t border-cream-200 pt-5 space-y-3">
        <h3 class="text-sm font-semibold text-bamboo-800">调价理由</h3>
        <ol class="space-y-2 list-decimal list-inside">
          <li
            v-for="(r, i) in reasons"
            :key="i"
            class="text-sm text-warm-600 leading-relaxed"
          >
            {{ r.text }}
          </li>
        </ol>
      </div>
    </div>
  </div>
</template>
