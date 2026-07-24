<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHotelStore } from '@/stores/hotel'
import { useAiJobsStore } from '@/stores/aiJobs'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { buildContentAiParams } from '@/utils/aiContextParams'
import {
  CalendarDays,
  ChevronDown,
  Coins,
  FileText,
  Plus,
  Sparkles,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const store = useHotelStore()
const aiJobs = useAiJobsStore()
const pageStateKey = 'pricing'
const initialRestoredState = loadAiPageState<any>(pageStateKey)
const redirectingToResult = ref(Boolean(
  route.query.new !== '1'
  && initialRestoredState?.generating
  && initialRestoredState.runId
))

const form = reactive({
  pricingPeriod: 'next7',
  customStartDate: '',
  customEndDate: '',
  pricingGoal: 'balance',
  demandSignal: 'normal',
  bookingWindow: '1-3',
  eventFactor: 'normal',
  competitorPriceRange: '',
  currentPriceNotes: '',
  priceFloor: '',
  maxDiscountPercent: 10,
  targetChannels: ['ota'] as string[],
  promotionAllowed: true,
  packagePreference: 'room-only',
  riskLevel: 'balanced',
  constraints: '',
})

const showAdvanced = ref(false)
const submitting = ref(false)

const periodOptions = [
  { value: 'today', label: '今天及当日尾房' },
  { value: 'next3', label: '未来 3 天' },
  { value: 'next7', label: '未来 7 天' },
  { value: 'weekend', label: '下一个周末' },
  { value: 'holiday', label: '指定节假日' },
  { value: 'custom', label: '自定义日期' },
]

const goalOptions = [
  { value: 'fill', label: '优先提升成交', note: '适合临近入住日、需要加快转化' },
  { value: 'balance', label: '平衡价格与成交', note: '兼顾竞争力和收益空间' },
  { value: 'revenue', label: '优先提升收益', note: '适合需求较强、房型有差异化' },
  { value: 'brand', label: '维护价格体系', note: '避免公开渠道频繁低价' },
]

const demandOptions = [
  { value: 'unknown', label: '暂不确定' },
  { value: 'weak', label: '偏弱：咨询与预订较少' },
  { value: 'normal', label: '正常：与平日接近' },
  { value: 'strong', label: '偏强：咨询或搜索明显增加' },
  { value: 'hot', label: '火热：节庆或本地事件带动' },
]

const bookingWindowOptions = [
  { value: 'same-day', label: '当天' },
  { value: '1-3', label: '提前 1-3 天' },
  { value: '4-7', label: '提前 4-7 天' },
  { value: '8-14', label: '提前 8-14 天' },
  { value: '15+', label: '提前 15 天以上' },
]

const eventOptions = [
  { value: 'normal', label: '普通工作日' },
  { value: 'weekend', label: '普通周末' },
  { value: 'holiday', label: '法定节假日' },
  { value: 'local-event', label: '演出、展会或本地活动' },
  { value: 'weather-risk', label: '天气可能影响出行' },
]

const channelOptions = [
  { value: 'ota', label: 'OTA 平台' },
  { value: 'direct', label: '电话/前台直订' },
  { value: 'wechat', label: '微信私域' },
  { value: 'member', label: '会员/老客' },
]

const packageOptions = [
  { value: 'room-only', label: '仅调整房价' },
  { value: 'value-add', label: '优先加权益，不直接降价' },
  { value: 'bundle', label: '设计住宿套餐' },
  { value: 'member-only', label: '仅做私域/会员优惠' },
]

const riskOptions = [
  { value: 'conservative', label: '稳健' },
  { value: 'balanced', label: '均衡' },
  { value: 'aggressive', label: '积极' },
]

const dateRange = computed(() => {
  if (form.pricingPeriod === 'custom') {
    return [form.customStartDate, form.customEndDate].filter(Boolean).join(' 至 ')
  }
  return labelOf(periodOptions, form.pricingPeriod)
})

const canGenerate = computed(() => {
  if (!form.pricingGoal || !form.demandSignal || !form.bookingWindow || !form.eventFactor) return false
  if (form.pricingPeriod === 'custom') return Boolean(form.customStartDate && form.customEndDate)
  return Boolean(form.pricingPeriod)
})

const roomSnapshot = computed(() => (store.roomTypes || []).map(room => ({
  roomId: room.id,
  roomName: displayRoomName(room.name),
  basePrice: room.basePrice,
  roomCount: room.count,
  occupancyRate: roomOccupancyRate(room.name),
})))
const occupancyImport = computed(() => store.occupancyImport)
const occupancyText = computed(() => store.occupancySummaryText || '尚未上传历史出租率数据')

const cityName = computed(() => (
  store.config.poiCity
  || String(store.config.city || '').split('/').filter(Boolean).slice(-2, -1)[0]
  || String(store.config.city || '').split('/').filter(Boolean).pop()
  || '本地'
))
const hotelName = computed(() => store.config.poiName || store.config.name || '本店')
const recommendation = computed(() => store.pendingRecommendation || null)
const weatherText = computed(() => {
  const weather = store.weather
  if (!weather?.weather && !weather?.temperature) return ''
  return `${weather.weather || ''}${weather.temperature ? ` ${weather.temperature}℃` : ''}`.trim()
})
const nearbyPlaceText = computed(() => {
  const names = (recommendation.value?.nearbyHotPlaces || [])
    .map((item: any) => item?.name || item?.title)
    .filter(Boolean)
    .slice(0, 3)
  return names.length ? names.join('、') : store.config.nearby || `${cityName.value}核心商圈`
})
const priceEvidenceText = computed(() => {
  const prices = (recommendation.value?.nearbyHotelPrices || [])
    .map((item: any) => {
      const name = item?.hotelName || item?.name || '周边酒店'
      const price = item?.priceRange || item?.price || item?.lowestPrice
      return price ? `${name} ${price}` : ''
    })
    .filter(Boolean)
    .slice(0, 3)
  if (prices.length) return prices.join('；')
  return `${cityName.value}同商圈同档酒店当天入住价格，注明 OTA 平台、查询时间和待核实状态。`
})
const dynamicPlaceholders = computed(() => ({
  competitorPriceRange: priceEvidenceText.value,
  currentPriceNotes: `${hotelName.value}现有房型 ${roomSnapshot.value.length || 0} 个；补充今天咨询/预订变化、热门房型和滞销房型${weatherText.value ? `；今日天气 ${weatherText.value}` : ''}。`,
  priceFloor: (() => {
    const prices = roomSnapshot.value.map(room => Number(room.basePrice || 0)).filter(price => price > 0)
    return prices.length ? `${Math.max(0, Math.min(...prices) - 50)}` : '元'
  })(),
  constraints: `例如：${hotelName.value}公开渠道不得低于底价；未核实竞品价格不直接跟价；靠近${nearbyPlaceText.value}的卖点优先用权益表达。`,
}))

const summaryItems = computed(() => [
  { label: '定价周期', value: dateRange.value || '-' },
  { label: '定价目标', value: labelOf(goalOptions, form.pricingGoal) },
  { label: '需求信号', value: labelOf(demandOptions, form.demandSignal) },
  { label: '预订窗口', value: labelOf(bookingWindowOptions, form.bookingWindow) },
  { label: '日期影响', value: labelOf(eventOptions, form.eventFactor) },
  { label: '房型数量', value: `${roomSnapshot.value.length} 个` },
])

function roomOccupancyRate(roomName: string) {
  const summary = occupancyImport.value?.roomTypeSummaries.find(room => room.roomTypeName.trim() === roomName.trim())
  return summary ? summary.averageOccupancyRate : null
}

function displayRoomName(value: string) {
  return String(value || '')
    .replace(/[（(][^()（）]*[)）]\s*$/g, '')
    .trim()
}

function toggleChannel(channel: string) {
  const index = form.targetChannels.indexOf(channel)
  if (index >= 0) form.targetChannels.splice(index, 1)
  else form.targetChannels.push(channel)
}

function labelOf(options: { value: string; label: string }[], value: string) {
  return options.find(item => item.value === value)?.label || value
}

function persistState(extra: Record<string, any> = {}) {
  saveAiPageState(pageStateKey, {
    form: JSON.parse(JSON.stringify(form)),
    showAdvanced: showAdvanced.value,
    ...extra,
  })
}

function restoreState(restored = loadAiPageState<any>(pageStateKey)) {
  if (!restored) return
  if (restored.form && typeof restored.form === 'object') Object.assign(form, restored.form)
  showAdvanced.value = Boolean(restored.showAdvanced)
}

function redirectToRunningResult(restored = loadAiPageState<any>(pageStateKey)) {
  if (route.query.new === '1') return false
  if (restored?.generating && restored.runId) {
    router.replace({ path: '/pricing/result', query: { run: restored.runId } })
    return true
  }
  return false
}

function resetPricing() {
  Object.assign(form, {
    pricingPeriod: 'next7',
    customStartDate: '',
    customEndDate: '',
    pricingGoal: 'balance',
    demandSignal: 'normal',
    bookingWindow: '1-3',
    eventFactor: 'normal',
    competitorPriceRange: '',
    currentPriceNotes: '',
    priceFloor: '',
    maxDiscountPercent: 10,
    targetChannels: ['ota'],
    promotionAllowed: true,
    packagePreference: 'room-only',
    riskLevel: 'balanced',
    constraints: '',
  })
  showAdvanced.value = false
  persistState({ runId: '', generating: false, aiText: '', errorMessage: '', statusText: '' })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function buildPricingJobParams() {
  const channelLabels = form.targetChannels.map(channel => labelOf(channelOptions, channel))
  return buildContentAiParams(store, 'pricing', {
    theme: '酒店房型定价建议',
    dateRange: dateRange.value,
    pricingPeriod: form.pricingPeriod,
    pricingGoal: form.pricingGoal,
    pricingGoalLabel: labelOf(goalOptions, form.pricingGoal),
    demandSignal: form.demandSignal,
    demandSignalLabel: labelOf(demandOptions, form.demandSignal),
    bookingWindow: form.bookingWindow,
    bookingWindowLabel: labelOf(bookingWindowOptions, form.bookingWindow),
    eventFactor: form.eventFactor,
    eventFactorLabel: labelOf(eventOptions, form.eventFactor),
    competitorPriceRange: form.competitorPriceRange,
    currentPriceNotes: form.currentPriceNotes,
    priceFloor: form.priceFloor,
    maxDiscountPercent: form.maxDiscountPercent,
    targetChannels: form.targetChannels,
    channelLabels,
    promotionAllowed: form.promotionAllowed,
    packagePreference: form.packagePreference,
    packagePreferenceLabel: labelOf(packageOptions, form.packagePreference),
    riskLevel: form.riskLevel,
    riskLevelLabel: labelOf(riskOptions, form.riskLevel),
    constraints: form.constraints,
    roomSnapshot: roomSnapshot.value,
    evidenceRequirement: true,
    outputFormat: 'markdown',
    message: `请为${dateRange.value}生成房型定价执行方案。目标：${labelOf(goalOptions, form.pricingGoal)}；渠道：${channelLabels.join('、') || '待设定'}。输出要通俗、重点明确、步骤清楚，表格必须标准，不输出思考过程。`,
  })
}

async function startGeneration() {
  if (!canGenerate.value) return
  submitting.value = true
  try {
    persistState({ runId: '', generating: false, completed: false, aiText: '', errorMessage: '', statusText: '' })
    await aiJobs.submit('pricing', buildPricingJobParams(), `智能定价 ${dateRange.value}`)
    aiJobs.showPanel = true
  } finally {
    submitting.value = false
  }
}

function startGenerationLegacy() {
  if (!canGenerate.value) return
  const runId = `${Date.now()}`
  persistState({
    runId,
    generating: true,
    completed: false,
    aiText: '',
    errorMessage: '',
    statusText: '正在准备定价建议',
  })
  router.push({ path: '/pricing/result', query: { run: runId } })
}

onMounted(async () => {
  const restored = initialRestoredState || loadAiPageState<any>(pageStateKey)
  if (route.query.new === '1') resetPricing()
  else if (redirectToRunningResult(restored)) return
  else restoreState(restored)
  if (!store.config.name) await store.loadFromApi().catch(() => {})
  store.fetchWeather().catch(() => {})
  if ((store.config.poiId || store.config.poiName) && !store.pendingRecommendation) {
    store.fetchSurroundingRecommendation().catch(() => {})
  }
})
</script>

<template>
  <div v-if="!redirectingToResult" class="mx-auto max-w-6xl space-y-5 p-6">
    <header class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <div class="flex items-center gap-2 text-bamboo-900">
          <Coins class="h-5 w-5" />
          <h1 class="text-xl font-semibold">智能定价</h1>
        </div>
        <p class="mt-1 text-sm text-warm-600">填写真实经营条件，生成独立的房型价格执行方案。</p>
      </div>
      <div class="flex gap-2">
        <button class="secondary-button" @click="router.push('/history/pricing')"><FileText class="h-4 w-4" />生成记录</button>
        <button class="secondary-button" @click="resetPricing"><Plus class="h-4 w-4" />新建定价</button>
      </div>
    </header>

    <section class="overflow-hidden rounded-2xl border border-cream-300 bg-white shadow-sm">
      <div class="border-b border-cream-200 px-6 py-5">
        <h2 class="text-base font-semibold text-bamboo-900">定价条件</h2>
        <p class="mt-1 text-xs text-warm-500">只填写你能确认的信息，未知项可以留空，系统会在方案中标注待核实。</p>
      </div>

      <div class="grid gap-5 px-6 py-4 lg:grid-cols-[minmax(0,1fr)_240px] lg:items-start">
        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <label class="field">
              <span>定价周期</span>
              <select v-model="form.pricingPeriod" class="studio-input">
                <option v-for="item in periodOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label class="field">
              <span>日期影响因素</span>
              <select v-model="form.eventFactor" class="studio-input">
                <option v-for="item in eventOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
          </div>

          <div v-if="form.pricingPeriod === 'custom'" class="grid grid-cols-2 gap-3">
            <label class="field"><span>开始日期</span><input v-model="form.customStartDate" class="studio-input" type="date" /></label>
            <label class="field"><span>结束日期</span><input v-model="form.customEndDate" class="studio-input" type="date" /></label>
          </div>

          <div class="field">
            <span>本次定价目标</span>
            <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <button
                v-for="item in goalOptions"
                :key="item.value"
                class="option-card"
                :class="form.pricingGoal === item.value ? 'option-card-active' : ''"
                @click="form.pricingGoal = item.value"
              >
                <strong>{{ item.label }}</strong>
                <small>{{ item.note }}</small>
              </button>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <label class="field">
              <span>市场需求信号</span>
              <select v-model="form.demandSignal" class="studio-input">
                <option v-for="item in demandOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label class="field">
              <span>主要预订窗口</span>
              <select v-model="form.bookingWindow" class="studio-input">
                <option v-for="item in bookingWindowOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
          </div>

          <div class="field">
            <span>重点销售渠道</span>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="item in channelOptions"
                :key="item.value"
                class="choice-chip"
                :class="form.targetChannels.includes(item.value) ? 'choice-chip-active' : ''"
                @click="toggleChannel(item.value)"
              >{{ item.label }}</button>
            </div>
          </div>

          <button class="flex w-full items-center justify-between border-t border-cream-200 pt-3 text-sm font-medium text-bamboo-800" @click="showAdvanced = !showAdvanced">
            价格边界与补充信息
            <ChevronDown class="h-4 w-4 transition" :class="showAdvanced ? 'rotate-180' : ''" />
          </button>

          <div v-if="showAdvanced" class="space-y-4">
            <label class="field">
              <span>OTA 竞品价格观察 <small>可选</small></span>
              <input v-model="form.competitorPriceRange" class="studio-input" :placeholder="dynamicPlaceholders.competitorPriceRange" />
            </label>
            <label class="field">
              <span>当前价格或销售现象 <small>可选</small></span>
              <textarea v-model="form.currentPriceNotes" class="studio-input min-h-20 resize-none" :placeholder="dynamicPlaceholders.currentPriceNotes" />
            </label>
            <div class="grid grid-cols-2 gap-3">
              <label class="field"><span>最低可接受价</span><input v-model="form.priceFloor" class="studio-input" min="0" :placeholder="dynamicPlaceholders.priceFloor" type="number" /></label>
              <label class="field"><span>最大折扣 %</span><input v-model.number="form.maxDiscountPercent" class="studio-input" min="0" max="50" type="number" /></label>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <label class="field">
                <span>价格策略</span>
                <select v-model="form.packagePreference" class="studio-input">
                  <option v-for="item in packageOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>
              <label class="field">
                <span>风险偏好</span>
                <select v-model="form.riskLevel" class="studio-input">
                  <option v-for="item in riskOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>
            </div>
            <label class="flex items-center justify-between rounded-xl border border-cream-300 bg-cream-50 px-3 py-2.5 text-sm text-warm-700">
              允许使用限时促销
              <input v-model="form.promotionAllowed" class="h-4 w-4 accent-bamboo-800" type="checkbox" />
            </label>
            <label class="field">
              <span>不可违反的限制 <small>可选</small></span>
              <textarea v-model="form.constraints" class="studio-input min-h-20 resize-none" :placeholder="dynamicPlaceholders.constraints" />
            </label>
          </div>
          <div v-else class="grid gap-3 rounded-2xl border border-cream-200 bg-cream-50/70 p-4 sm:grid-cols-3">
            <div>
              <div class="text-[11px] font-semibold text-warm-400">AI 会自动参考</div>
              <p class="mt-1 line-clamp-3 text-xs leading-5 text-warm-600">{{ dynamicPlaceholders.competitorPriceRange }}</p>
            </div>
            <div>
              <div class="text-[11px] font-semibold text-warm-400">价格底线建议</div>
              <p class="mt-1 text-xs leading-5 text-warm-600">最低可接受价可留空，AI 会按本店房型挂牌价标注待核实。</p>
            </div>
            <div>
              <div class="text-[11px] font-semibold text-warm-400">执行边界</div>
              <p class="mt-1 line-clamp-3 text-xs leading-5 text-warm-600">{{ dynamicPlaceholders.constraints }}</p>
            </div>
          </div>

          <section v-if="roomSnapshot.length" class="rounded-2xl border border-cream-200 bg-white p-4">
            <div class="flex items-center justify-between gap-3">
              <div class="text-sm font-semibold text-bamboo-900">本店房型价格</div>
              <span class="text-[11px] text-warm-400">{{ roomSnapshot.length }} 个房型参与定价</span>
            </div>
            <div class="mt-3 grid gap-2 sm:grid-cols-3">
              <div v-for="room in roomSnapshot" :key="room.roomId || room.roomName" class="room-price-pill">
                <span>{{ room.roomName }}</span>
                <strong>¥{{ room.basePrice || 0 }}</strong>
              </div>
            </div>
          </section>
        </div>

        <aside class="lg:border-l lg:border-cream-200 lg:pl-5">
          <section class="mb-4 rounded-2xl border border-bamboo-100 bg-bamboo-50/70 p-3.5">
            <div class="flex items-center justify-between gap-2">
              <div class="text-sm font-semibold text-bamboo-900">实时参考</div>
              <span class="rounded-full bg-white px-2 py-1 text-[11px] text-bamboo-700">{{ recommendation?.fallback ? '基础推荐' : '周边智能体' }}</span>
            </div>
            <p class="mt-2 text-xs leading-5 text-warm-600">{{ hotelName }} · {{ cityName }}{{ weatherText ? ` · ${weatherText}` : '' }}</p>
            <p class="mt-1 text-xs leading-5 text-warm-600">价格参考：{{ priceEvidenceText }}</p>
            <p class="mt-1 text-xs leading-5 text-warm-600">历史出租率：{{ occupancyText }}</p>
          </section>

          <section class="rounded-2xl border border-cream-300 bg-cream-50 p-3.5">
            <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-900"><FileText class="h-4 w-4" />本次条件</div>
            <dl class="mt-3 space-y-2">
              <div v-for="item in summaryItems" :key="item.label">
                <dt class="text-[11px] text-warm-400">{{ item.label }}</dt>
                <dd class="mt-0.5 text-sm leading-5 text-warm-700">{{ item.value }}</dd>
              </div>
            </dl>
            <div class="mt-3 border-t border-cream-200 pt-3">
              <div class="text-[11px] text-warm-400">销售渠道</div>
              <div class="mt-2 flex flex-wrap gap-1.5">
                <span v-for="channel in form.targetChannels" :key="channel" class="rounded-full bg-white px-2 py-1 text-[11px] text-bamboo-700">{{ labelOf(channelOptions, channel) }}</span>
              </div>
            </div>
          </section>

        </aside>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-cream-200 bg-cream-50/60 px-6 py-3">
        <p class="flex items-center gap-2 text-xs text-warm-500"><CalendarDays class="h-4 w-4" />生成后将在独立页面展示完整价格方案，执行前请人工核对实时入住率、竞品价格和渠道政策。</p>
        <button class="generate-button" :disabled="!canGenerate || submitting" @click="startGeneration"><Sparkles class="h-4 w-4" />{{ submitting ? '已加入工作列表' : '生成定价方案' }}</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.field > span { display: block; margin-bottom: 0.45rem; color: #6f5f4e; font-size: 0.8rem; font-weight: 600; }
.field small { color: #a6927b; font-size: 0.7rem; font-weight: 400; }
.option-card { min-height: 76px; border: 1px solid #eadfce; border-radius: 0.75rem; padding: 0.65rem; text-align: left; transition: 150ms ease; }
.option-card strong { display: block; color: #4f4338; font-size: 0.76rem; }
.option-card small { display: block; margin-top: 0.25rem; color: #9b8976; font-size: 0.62rem; line-height: 1.35; }
.option-card:hover { border-color: #8cac77; background: #f7faf4; }
.option-card-active { border-color: #4f7a42; background: #eef5e9; box-shadow: 0 0 0 1px #4f7a42; }
.choice-chip { border: 1px solid #eadfce; border-radius: 999px; padding: 0.45rem 0.75rem; color: #776655; font-size: 0.75rem; transition: 150ms ease; }
.choice-chip:hover { border-color: #8cac77; }
.choice-chip-active { border-color: #315b37; background: #315b37; color: white; }
.room-price-pill {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  border-radius: 0.8rem;
  background: #faf7f1;
  padding: 0.65rem 0.8rem;
}
.room-price-pill span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #6f5f4e;
  font-size: 0.76rem;
}
.room-price-pill strong {
  flex-shrink: 0;
  color: #234d32;
  font-size: 0.82rem;
}
.primary-button,.secondary-button,.generate-button { display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; border-radius: 0.75rem; padding: 0.65rem 0.9rem; font-size: 0.78rem; font-weight: 600; transition: 150ms ease; }
.primary-button,.generate-button { background: #234d32; color: white; }
.primary-button:hover,.generate-button:hover { background: #183b26; }
.secondary-button { border: 1px solid #e5d8c5; background: white; color: #5f5143; }
.secondary-button:hover { border-color: #8cac77; background: #f7faf4; }
.generate-button:disabled { cursor: not-allowed; opacity: 0.5; }
</style>
