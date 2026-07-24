<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHotelStore } from '@/stores/hotel'
import { useAiJobsStore } from '@/stores/aiJobs'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { getGenerationHistory } from '@/api/history'
import { buildContentAiParams } from '@/utils/aiContextParams'
import {
  formatHistoryTime,
  historyTitle,
  promptText,
  type HistoryItem,
} from '@/utils/generationHistory'
import {
  budgetOptions,
  capacityOptions,
  channelOptions,
  createDefaultStrategyForm,
  depthOptions,
  objectiveOptions,
  occasionOptions,
  buildStrategyParams,
  optionLabel,
  periodOptions,
  type StrategyForm,
} from '@/utils/strategyConfig'
import { ArrowUpRight, Calendar, FileText, Lightbulb, Loader2, Plus, Sparkles } from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const hotelStore = useHotelStore()
const aiJobs = useAiJobsStore()
const pageStateKey = 'strategy'
const initialRestoredState = loadAiPageState<any>(pageStateKey)
const redirectingToResult = ref(Boolean(
  route.query.new !== '1'
  && initialRestoredState?.generating
  && initialRestoredState.runId
))
const form = reactive<StrategyForm>(createDefaultStrategyForm())
const pageError = ref('')
const recentStrategies = ref<HistoryItem[]>([])
const loadingRecent = ref(false)
const submitting = ref(false)

const cityName = computed(() => (
  hotelStore.config.poiCity
  || String(hotelStore.config.city || '').split('/').filter(Boolean).slice(-2, -1)[0]
  || String(hotelStore.config.city || '').split('/').filter(Boolean).pop()
  || '本地'
))
const hotelName = computed(() => hotelStore.config.poiName || hotelStore.config.name || '本店')
const recommendation = computed(() => hotelStore.pendingRecommendation || null)
const profileSuggestion = computed(() => recommendation.value?.hotelProfileSuggestion || {})
const occupancyCard = computed(() => {
  const data = hotelStore.occupancyImport
  if (!data) {
    return {
      rate: '待上传',
      range: '上传历史房态表后，这里会显示各房型出租率和占用房晚',
      summary: '策略页会优先参考已上传的历史经营数据。',
    }
  }
  return {
    rate: `${Math.round(data.averageOccupancyRate * 100)}%`,
    range: data.dateRange || '已上传周期',
    summary: hotelStore.occupancySummaryText,
  }
})
const weatherText = computed(() => {
  const weather = hotelStore.weather
  if (!weather?.weather && !weather?.temperature) return ''
  return `${weather.weather || ''}${weather.temperature ? ` ${weather.temperature}℃` : ''}`.trim()
})
const nearbyNames = computed(() => {
  const fromRecommendation = (recommendation.value?.nearbyHotPlaces || [])
    .map((item: any) => item?.name || item?.title)
    .filter(Boolean)
    .slice(0, 3)
  if (fromRecommendation.length) return fromRecommendation.join('、')
  return profileSuggestion.value.nearby || hotelStore.config.nearby || `${cityName.value}核心商圈`
})
const competitorPriceHint = computed(() => {
  const prices = (recommendation.value?.nearbyHotelPrices || [])
    .map((item: any) => item?.price || item?.priceRange || item?.lowestPrice)
    .filter(Boolean)
    .slice(0, 3)
  if (prices.length) return `${cityName.value}周边可见酒店价格：${prices.join('、')}，请保留平台/时间来源。`
  return `${cityName.value}同商圈 3-5 家酒店/民宿当天可售价格，写明平台、查询时间和待核实状态。`
})
const placeholders = computed(() => ({
  targetAudience: profileSuggestion.value.targetAudience
    || hotelStore.config.targetAudience
    || `${cityName.value}周边亲子家庭、情侣周末游、短途自驾客`,
  marketSignals: [
    `${hotelName.value}所在区域：${nearbyNames.value}`,
    weatherText.value ? `今日天气：${weatherText.value}` : '',
    '补充今天咨询量、搜索热词、活动或节假日变化，并注明来源。'
  ].filter(Boolean).join('；'),
  competitorObservations: competitorPriceHint.value,
  availableOffers: `结合${hotelName.value}现有资源填写，例如早餐、停车、延迟退房、周边合作、房型升级或私域会员权益。`,
  constraints: `例如：${hotelName.value}不做虚假低价；公开渠道不低于协议价；每天最多发布 1-2 条；未核实热点不直接宣传。`,
}))

const canGenerate = computed(() => Boolean(
  form.objective && form.period && form.occasion && form.channels.length
))

function toggleChannel(channel: string) {
  const index = form.channels.indexOf(channel)
  if (index >= 0) form.channels.splice(index, 1)
  else form.channels.push(channel)
}

function restoreForm(restored = loadAiPageState<any>(pageStateKey)) {
  if (restored?.form && typeof restored.form === 'object') Object.assign(form, restored.form)
}

function redirectToRunningResult(restored = loadAiPageState<any>(pageStateKey)) {
  if (route.query.new === '1') return false
  if (restored?.generating && restored.runId) {
    router.replace({ path: '/strategy/result', query: { run: restored.runId } })
    return true
  }
  return false
}

function resetStrategy() {
  Object.assign(form, createDefaultStrategyForm())
  saveAiPageState(pageStateKey, { form: { ...form }, runId: '', generating: false, aiText: '' })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function buildStrategyJobParams() {
  const params = buildStrategyParams(form)
  const channelLabels = form.channels.map(channel => optionLabel(channelOptions, channel))
  params.message = `请生成营销策略执行方案。周期：${optionLabel(periodOptions, form.period)}；目标：${optionLabel(objectiveOptions, form.objective)}；客群：${form.targetAudience || '由 AI 基于上下文设定'}；渠道：${channelLabels.join('、') || '待设定'}。输出要给老板/前台看，重点标记清楚，按步骤说明怎么做，不输出冗余分析。`
  return buildContentAiParams(hotelStore, 'strategy', params)
}

async function startGeneration() {
  if (!canGenerate.value) return
  submitting.value = true
  try {
    saveAiPageState(pageStateKey, {
      form: JSON.parse(JSON.stringify(form)),
      runId: '',
      generating: false,
      statusText: '',
      aiText: '',
      errorMessage: '',
    })
    await aiJobs.submit('strategy', buildStrategyJobParams(), `营销策略 ${optionLabel(periodOptions, form.period)}`)
    aiJobs.showPanel = true
  } finally {
    submitting.value = false
  }
}

function startGenerationLegacy() {
  if (!canGenerate.value) return
  const runId = `${Date.now()}`
  saveAiPageState(pageStateKey, {
    form: JSON.parse(JSON.stringify(form)),
    runId,
    generating: true,
    statusText: '正在准备营销策略',
    aiText: '',
    errorMessage: '',
  })
  router.push({ path: '/strategy/result', query: { run: runId } })
}

function openGenerated(item: HistoryItem) {
  router.push({ path: `/history/strategy/${item.id}`, query: { from: '/strategy' } })
}

onMounted(async () => {
  const restored = initialRestoredState || loadAiPageState<any>(pageStateKey)
  if (route.query.new === '1') resetStrategy()
  else if (redirectToRunningResult(restored)) return
  else restoreForm(restored)
  if (!hotelStore.config.name) {
    await hotelStore.loadFromApi().catch(() => {})
  }
  hotelStore.fetchWeather().catch(() => {})
  if ((hotelStore.config.poiId || hotelStore.config.poiName) && !hotelStore.pendingRecommendation) {
    hotelStore.fetchSurroundingRecommendation().catch(() => {})
  }
  loadingRecent.value = true
  try {
    const { data } = await getGenerationHistory('strategy', 3)
    recentStrategies.value = Array.isArray(data?.data) ? data.data.slice(0, 3) : []
  } catch {
    pageError.value = '最近策略加载失败'
  } finally {
    loadingRecent.value = false
  }
})
</script>

<template>
  <div v-if="!redirectingToResult" class="mx-auto max-w-6xl space-y-5 p-6">
    <header class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <div class="flex items-center gap-2 text-bamboo-900">
          <Lightbulb class="h-5 w-5" />
          <h1 class="text-xl font-semibold">营销策略</h1>
        </div>
        <p class="mt-1 text-sm text-warm-600">填写真实经营条件，生成完整的阶段执行方案。</p>
      </div>
      <div class="flex gap-2">
        <button class="secondary-button" @click="router.push('/history/strategy')"><FileText class="h-4 w-4" />生成记录</button>
        <button class="primary-button" @click="resetStrategy"><Plus class="h-4 w-4" />新建策略</button>
      </div>
    </header>

    <section class="overflow-hidden rounded-2xl border border-cream-300 bg-white shadow-sm">
      <div class="border-b border-cream-200 px-6 py-5">
        <h2 class="text-base font-semibold text-bamboo-900">策略条件</h2>
        <p class="mt-1 text-xs text-warm-500">不确定的信息可以留空，智能体会将其标记为待核实，不会自行编造。</p>
      </div>

      <div class="grid gap-8 p-6 lg:grid-cols-2">
        <div class="space-y-6">
          <div class="field">
            <span>核心目标</span>
            <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
              <button
                v-for="item in objectiveOptions"
                :key="item.value"
                class="objective-card"
                :class="form.objective === item.value ? 'objective-card-active' : ''"
                @click="form.objective = item.value"
              >
                <strong>{{ item.label }}</strong>
                <small>{{ item.note }}</small>
              </button>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <label class="field"><span>执行周期</span><select v-model="form.period" class="studio-input"><option v-for="item in periodOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            <label class="field"><span>经营场景</span><select v-model="form.occasion" class="studio-input"><option v-for="item in occasionOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
          </div>

          <label class="field"><span>本次目标客群 <small>可选，AI 可智能设定</small></span><input v-model="form.targetAudience" class="studio-input" :placeholder="placeholders.targetAudience" /></label>

          <div class="field">
            <span>计划使用的渠道</span>
            <div class="flex flex-wrap gap-2">
              <button v-for="item in channelOptions" :key="item.value" class="choice-chip" :class="form.channels.includes(item.value) ? 'choice-chip-active' : ''" @click="toggleChannel(item.value)">{{ item.label }}</button>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <label class="field"><span>预算条件</span><select v-model="form.budgetLevel" class="studio-input"><option v-for="item in budgetOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            <label class="field"><span>执行能力</span><select v-model="form.executionCapacity" class="studio-input"><option v-for="item in capacityOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
          </div>
        </div>

        <div class="space-y-5 border-cream-200 lg:border-l lg:pl-8">
          <section class="rounded-2xl border border-bamboo-100 bg-bamboo-50/60 p-4">
            <div class="flex items-center justify-between gap-3">
              <h3 class="text-sm font-semibold text-bamboo-900">实时参考</h3>
              <span class="rounded-full bg-white px-2 py-1 text-[11px] text-bamboo-700">{{ recommendation?.fallback ? '基础推荐' : '周边智能体' }}</span>
            </div>
            <p class="mt-2 text-xs leading-5 text-warm-600">{{ hotelName }} · {{ cityName }}{{ weatherText ? ` · ${weatherText}` : '' }}</p>
            <p class="mt-1 text-xs leading-5 text-warm-600">周边：{{ nearbyNames }}</p>
            <div class="mt-3 rounded-2xl border border-white bg-white p-3">
              <div class="flex items-center justify-between gap-3">
                <div class="text-xs font-semibold text-bamboo-900">历史出租率</div>
                <span class="rounded-full bg-bamboo-50 px-2 py-0.5 text-[11px] font-semibold text-bamboo-800">{{ occupancyCard.rate }}</span>
              </div>
              <div class="mt-1 text-[11px] text-warm-500">{{ occupancyCard.range }}</div>
              <p class="mt-1 text-xs leading-5 text-warm-600">{{ occupancyCard.summary }}</p>
            </div>
          </section>

          <label class="field"><span>已确认的市场信号 <small>可选</small></span><textarea v-model="form.marketSignals" class="studio-input min-h-24 resize-none" :placeholder="placeholders.marketSignals" /></label>
          <label class="field"><span>竞品观察 <small>可选</small></span><textarea v-model="form.competitorObservations" class="studio-input min-h-24 resize-none" :placeholder="placeholders.competitorObservations" /></label>
          <label class="field"><span>可使用的权益或资源 <small>可选</small></span><textarea v-model="form.availableOffers" class="studio-input min-h-20 resize-none" :placeholder="placeholders.availableOffers" /></label>
          <label class="field"><span>执行限制 <small>可选</small></span><textarea v-model="form.constraints" class="studio-input min-h-20 resize-none" :placeholder="placeholders.constraints" /></label>

          <div class="grid grid-cols-2 gap-3">
            <label class="field"><span>方案深度</span><select v-model="form.outputDepth" class="studio-input"><option v-for="item in depthOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            <label class="flex items-center justify-between self-end rounded-xl border border-cream-300 bg-cream-50 px-3 py-2.5 text-sm text-warm-700">标注待核实信息<input v-model="form.evidenceRequirement" class="h-4 w-4 accent-bamboo-800" type="checkbox" /></label>
          </div>
        </div>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-cream-200 bg-cream-50/60 px-6 py-4">
        <p class="text-xs text-warm-500">生成后将在独立页面展示完整策略，可随时返回修改条件。</p>
        <button class="generate-button" :disabled="!canGenerate || submitting" @click="startGeneration"><Sparkles class="h-4 w-4" />{{ submitting ? '已加入工作列表' : '生成完整营销策略' }}</button>
      </div>
    </section>

    <section class="rounded-2xl border border-cream-300 bg-white px-5 py-3 shadow-sm">
      <div class="flex min-w-0 items-center gap-4">
        <h2 class="shrink-0 text-sm font-semibold text-bamboo-900">已保存方案</h2>
        <p v-if="pageError" class="min-w-0 flex-1 truncate text-sm text-red-600">{{ pageError }}</p>
        <div v-else-if="loadingRecent" class="flex h-9 min-w-0 flex-1 items-center"><Loader2 class="h-5 w-5 animate-spin text-warm-400" /></div>
        <div v-else-if="recentStrategies.length" class="flex min-w-0 flex-1 gap-2 overflow-hidden">
          <button v-for="item in recentStrategies" :key="item.id" class="saved-plan" @click="openGenerated(item)">
            <span class="min-w-0 truncate font-medium text-bamboo-900">{{ historyTitle(item) }}</span>
            <span class="flex min-w-0 items-center gap-1 truncate text-xs text-warm-500"><Calendar class="h-3.5 w-3.5 shrink-0" />{{ formatHistoryTime(item.createdAt) }}</span>
            <span class="min-w-0 truncate text-xs text-warm-500">{{ promptText(item) || '营销策略生成' }}</span>
          </button>
        </div>
        <div v-else class="min-w-0 flex-1 truncate text-sm text-warm-500">暂无最近策略</div>
        <button class="secondary-button shrink-0" @click="router.push('/history/strategy')">查看全部 <ArrowUpRight class="h-4 w-4" /></button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.field > span { display: block; margin-bottom: 0.45rem; color: #6f5f4e; font-size: 0.8rem; font-weight: 600; }
.field small { color: #a6927b; font-size: 0.7rem; font-weight: 400; }
.objective-card { min-height: 72px; border: 1px solid #eadfce; border-radius: 0.75rem; padding: 0.65rem; text-align: left; transition: 150ms ease; }
.objective-card strong { display: block; color: #4f4338; font-size: 0.76rem; }
.objective-card small { display: block; margin-top: 0.25rem; color: #9b8976; font-size: 0.62rem; line-height: 1.35; }
.objective-card:hover { border-color: #8cac77; background: #f7faf4; }
.objective-card-active { border-color: #4f7a42; background: #eef5e9; box-shadow: 0 0 0 1px #4f7a42; }
.choice-chip { border: 1px solid #eadfce; border-radius: 999px; padding: 0.45rem 0.75rem; color: #776655; font-size: 0.75rem; transition: 150ms ease; }
.choice-chip:hover { border-color: #8cac77; }
.choice-chip-active { border-color: #315b37; background: #315b37; color: white; }
.primary-button,.secondary-button,.generate-button { display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; border-radius: 0.75rem; padding: 0.65rem 0.9rem; font-size: 0.78rem; font-weight: 600; transition: 150ms ease; }
.primary-button,.generate-button { background: #234d32; color: white; }
.primary-button:hover,.generate-button:hover { background: #183b26; }
.secondary-button { border: 1px solid #e5d8c5; background: white; color: #5f5143; }
.secondary-button:hover { border-color: #8cac77; background: #f7faf4; }
.generate-button:disabled { cursor: not-allowed; opacity: 0.5; }
.saved-plan { display: flex; flex: 1 1 0; min-width: 0; height: 36px; align-items: center; gap: 0.5rem; border: 1px solid #eadfce; border-radius: 0.75rem; padding: 0 0.75rem; text-align: left; transition: 150ms ease; }
.saved-plan:hover { border-color: #8cac77; background: #f7faf4; }
</style>
