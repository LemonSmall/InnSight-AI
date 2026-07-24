<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, BadgeCheck, Building, CheckCircle2, Loader2, MapPin, Search, Sparkles, Table2, Tags, Upload, X } from 'lucide-vue-next'
import { getRegionChildren } from '@/api/hotel'
import { getGenerationHistory } from '@/api/history'
import { useHotelStore, type HotelPoiCandidate } from '@/stores/hotel'
import { parseOccupancyFile, parseOccupancyImage, parseOccupancyResultPayload, type OccupancyImportData } from '@/utils/occupancyImport'
import { resultText, rawParams, type HistoryItem } from '@/utils/generationHistory'
import { safeUiText } from '@/utils/uiText'

interface RegionOption {
  name: string
  adcode: string
  citycode?: string
  level: string
  center?: string
}

const router = useRouter()
const store = useHotelStore()

const toast = ref('')
const saving = ref(false)
const poiSearching = ref(false)
const poiBinding = ref('')
const poiResults = ref<HotelPoiCandidate[]>([])
const poiError = ref('')
const suggestionOpen = ref(false)
const pendingBindPoi = ref<HotelPoiCandidate | null>(null)
const searchTimer = ref<number | null>(null)
const importingOccupancy = ref(false)
const occupancyDragActive = ref(false)
const pageDragActive = ref(false)
const occupancyFileInput = ref<HTMLInputElement | null>(null)
const pendingOccupancyImport = ref<OccupancyImportData | null>(null)
const pendingOccupancyFileName = ref('')
const occupancyProcessingKey = 'sushijia:occupancy-import-processing'
const occupancyCompletedKey = 'sushijia:occupancy-import-completed'
let occupancyRecoverTimer: number | null = null

const provinces = ref<RegionOption[]>([])
const cities = ref<RegionOption[]>([])
const districts = ref<RegionOption[]>([])
const regionLoading = ref(false)

const region = reactive({
  province: store.config.poiProvince || '',
  city: store.config.poiCity || store.config.city || '',
  district: store.config.poiDistrict || '',
})

const form = reactive({
  name: store.config.name,
  type: store.config.type || '精品民宿',
  city: store.config.city,
  totalRooms: store.config.totalRooms,
  tags: store.config.tags,
  targetAudience: store.config.targetAudience,
  nearby: store.config.nearby,
})

function syncFormFromStore() {
  Object.assign(form, {
    name: store.config.name,
    type: store.config.type || '精品民宿',
    city: store.config.city,
    totalRooms: store.totalRooms,
    tags: store.config.tags,
    targetAudience: store.config.targetAudience,
    nearby: store.config.nearby,
  })
  Object.assign(region, {
    province: store.config.poiProvince || '',
    city: store.config.poiCity || store.config.city || '',
    district: store.config.poiDistrict || '',
  })
}

const boundPoi = computed(() => ({
  verified: Boolean(store.config.poiVerified && store.config.poiId),
  name: store.config.poiName || store.config.name,
  address: store.config.poiAddress || '',
  city: [store.config.poiProvince, store.config.poiCity, store.config.poiDistrict].filter(Boolean).join(' / '),
  type: store.config.poiTypeName || '',
}))

const tags = computed(() => String(form.tags || '').split(/[,，、]/).map(item => item.trim()).filter(Boolean))
const selectedCity = computed(() => region.city || form.city)
const recommendation = computed<any>(() => null)
const profileSuggestion = computed(() => recommendation.value?.hotelProfileSuggestion || {})
const recommendationSummary = computed(() => {
  const rec = recommendation.value
  if (!rec) return []
  return [
    { label: '当前酒店价格', value: `${rec.currentHotelPrices?.length || 0} 条` },
    { label: '周边酒店价格', value: `${rec.nearbyHotelPrices?.length || 0} 条` },
    { label: '热门地点', value: `${rec.nearbyHotPlaces?.length || 0} 个` },
    { label: '本地事件', value: `${rec.localEvents?.length || 0} 条` },
  ]
})
const occupancyImport = computed(() => store.occupancyImport)
const occupancyHistory = computed(() => store.occupancyHistory)
const activeOccupancyImportedAt = computed(() => occupancyImport.value?.importedAt || '')
const occupancyRateText = computed(() => occupancyImport.value ? `${Math.round(occupancyImport.value.averageOccupancyRate * 100)}%` : '-')
const importedRoomSummary = computed(() => (occupancyImport.value?.roomTypeSummaries || []).slice(0, 6))

function poiMeta(item: HotelPoiCandidate) {
  return [
    [item.province, item.city, item.district].filter(Boolean).join(' / '),
    item.businessArea ? `${item.businessArea}商圈` : '',
    item.keytag,
    item.rating ? `评分 ${item.rating}` : '',
    item.lowestPrice ? `参考起价 ¥${item.lowestPrice}` : '',
    item.typeName,
  ].filter(Boolean).join(' 路 ')
}

onMounted(async () => {
  await store.loadFromApi().then(syncFormFromStore).catch(() => {})
  recoverOccupancyProcessing()
  await loadProvinces()
  if (region.province) await loadCities(region.province)
  if (region.city) await loadDistricts(region.city)
})

onUnmounted(() => {
  if (occupancyRecoverTimer) window.clearTimeout(occupancyRecoverTimer)
})

watch(() => region.province, async (value) => {
  region.city = ''
  region.district = ''
  cities.value = []
  districts.value = []
  if (value) await loadCities(value)
  syncCity()
})

watch(() => region.city, async (value) => {
  region.district = ''
  districts.value = []
  if (value) await loadDistricts(value)
  syncCity()
  triggerSuggest()
})

watch(() => region.district, syncCity)

function syncCity() {
  form.city = [region.province, region.city, region.district].filter(Boolean).join(' / ')
}

async function loadProvinces() {
  regionLoading.value = true
  try {
    const { data: response } = await getRegionChildren({ keyword: '中国', subdistrict: 1 })
    provinces.value = response.data || response || []
  } finally {
    regionLoading.value = false
  }
}

async function loadCities(province: string) {
  const { data: response } = await getRegionChildren({ keyword: province, subdistrict: 1 })
  cities.value = response.data || response || []
}

async function loadDistricts(city: string) {
  const { data: response } = await getRegionChildren({ keyword: city, subdistrict: 1 })
  districts.value = response.data || response || []
}

async function handleSave() {
  saving.value = true
  try {
    await store.saveConfig({ ...store.config, ...form, totalRooms: store.totalRooms, city: form.city || region.city })
    form.totalRooms = store.totalRooms
    flash('已保存')
  } catch {
    flash(store.error || '酒店资料保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function handleOccupancyUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  await uploadOccupancyFile(file)
  input.value = ''
}

async function handleOccupancyDrop(event: DragEvent) {
  occupancyDragActive.value = false
  pageDragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  await uploadOccupancyFile(file)
}

async function handlePageDrop(event: DragEvent) {
  occupancyDragActive.value = false
  pageDragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  await uploadOccupancyFile(file)
}

function handlePageDragLeave(event: DragEvent) {
  const current = event.currentTarget as HTMLElement | null
  const related = event.relatedTarget as Node | null
  if (!current || !related || !current.contains(related)) {
    pageDragActive.value = false
  }
}

function handleOccupancyDragLeave(event: DragEvent) {
  const current = event.currentTarget as HTMLElement | null
  const related = event.relatedTarget as Node | null
  if (!current || !related || !current.contains(related)) {
    occupancyDragActive.value = false
  }
}

async function uploadOccupancyFile(file: File) {
  importingOccupancy.value = true
  const isImageUpload = file.type.startsWith('image/')
  if (isImageUpload) {
    saveOccupancyProcessing(file)
    scheduleOccupancyRecovery()
  }
  try {
    const data = isImageUpload
      ? await parseOccupancyImage(file)
      : await parseOccupancyFile(file)
    if (isImageUpload) saveCompletedOccupancyImport(data, file.name)
    pendingOccupancyImport.value = data
    pendingOccupancyFileName.value = file.name
    clearOccupancyProcessing()
  } catch (error: any) {
    const message = String(error?.message || '')
    if (isImageUpload && /aborted|abort|network|fetch|连接|中断|timeout/i.test(message)) {
      flash('房态图片仍在后台识别，稍后会自动恢复结果')
      scheduleOccupancyRecovery()
    } else {
      flash(message || '解析失败，请检查是否包含房型、占用房、剩余可售或出租率')
      clearOccupancyProcessing()
    }
  } finally {
    importingOccupancy.value = Boolean(readOccupancyProcessing())
  }
}

function saveOccupancyProcessing(file: File) {
  try {
    window.localStorage?.setItem(occupancyProcessingKey, JSON.stringify({
      fileName: file.name,
      startedAt: new Date().toISOString(),
    }))
  } catch {
    // best effort only
  }
}

function readOccupancyProcessing(): { fileName: string; startedAt: string } | null {
  try {
    const raw = window.localStorage?.getItem(occupancyProcessingKey)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function clearOccupancyProcessing() {
  try {
    window.localStorage?.removeItem(occupancyProcessingKey)
  } catch {
    // best effort only
  }
  if (occupancyRecoverTimer) {
    window.clearTimeout(occupancyRecoverTimer)
    occupancyRecoverTimer = null
  }
}

function saveCompletedOccupancyImport(data: OccupancyImportData, fileName: string) {
  try {
    window.localStorage?.setItem(occupancyCompletedKey, JSON.stringify({ fileName, data }))
  } catch {
    // best effort only
  }
}

function readCompletedOccupancyImport(): { fileName: string; data: OccupancyImportData } | null {
  try {
    const raw = window.localStorage?.getItem(occupancyCompletedKey)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function clearCompletedOccupancyImport() {
  try {
    window.localStorage?.removeItem(occupancyCompletedKey)
  } catch {
    // best effort only
  }
}

function recoverOccupancyProcessing() {
  const completed = readCompletedOccupancyImport()
  if (completed?.data?.records?.length) {
    pendingOccupancyImport.value = completed.data
    pendingOccupancyFileName.value = completed.fileName || completed.data.sourceFileName || '房态图片'
    importingOccupancy.value = false
    clearOccupancyProcessing()
    return
  }
  const pending = readOccupancyProcessing()
  if (!pending) return
  importingOccupancy.value = true
  pendingOccupancyFileName.value = pending.fileName || '房态图片'
  scheduleOccupancyRecovery(200)
}

function scheduleOccupancyRecovery(delay = 2500) {
  if (occupancyRecoverTimer) window.clearTimeout(occupancyRecoverTimer)
  occupancyRecoverTimer = window.setTimeout(() => {
    pollOccupancyGeneration().catch(() => scheduleOccupancyRecovery())
  }, delay)
}

async function pollOccupancyGeneration() {
  const pending = readOccupancyProcessing()
  if (!pending) {
    importingOccupancy.value = false
    return
  }
  const started = Date.parse(pending.startedAt || '')
  if (Number.isFinite(started) && Date.now() - started > 8 * 60 * 1000) {
    clearOccupancyProcessing()
    importingOccupancy.value = false
    flash('房态图片识别超时，请重新上传')
    return
  }

  const { data } = await getGenerationHistory('occupancy_image', 10)
  const rows = ((data?.data || data || []) as HistoryItem[])
  const matched = rows.find(item => isMatchingOccupancyHistory(item, pending))
  if (!matched || matched.status === 'processing') {
    importingOccupancy.value = true
    scheduleOccupancyRecovery()
    return
  }
  if (matched.status === 'failed') {
    clearOccupancyProcessing()
    importingOccupancy.value = false
    flash(matched.errorMsg || '房态图片识别失败，请重新上传')
    return
  }

  const parsed = parseOccupancyResultPayload(resultText(matched), pending.fileName || '房态图片')
  if (!parsed) {
    scheduleOccupancyRecovery()
    return
  }
  pendingOccupancyImport.value = parsed
  pendingOccupancyFileName.value = pending.fileName || '房态图片'
  saveCompletedOccupancyImport(parsed, pending.fileName || '房态图片')
  importingOccupancy.value = false
  clearOccupancyProcessing()
}

function isMatchingOccupancyHistory(item: HistoryItem, pending: { fileName: string; startedAt: string }) {
  const itemTime = Date.parse(item.createdAt || item.completedAt || '')
  const startTime = Date.parse(pending.startedAt || '')
  if (Number.isFinite(itemTime) && Number.isFinite(startTime) && itemTime + 5000 < startTime) return false
  const params = rawParams(item)
  const sourceFileName = String(params.sourceFileName || params.fileName || '')
  return !pending.fileName || !sourceFileName || sourceFileName === pending.fileName
}

async function confirmOccupancyImport() {
  const data = pendingOccupancyImport.value
  if (!data) return
  importingOccupancy.value = true
  try {
    await store.applyOccupancyImport(data, { persistRooms: true })
    form.totalRooms = store.totalRooms
    const report = store.lastOccupancyImportReport
    if (report?.conflicts) {
      flash(`新增 ${report.added} 条，重复跳过 ${report.duplicates} 条，冲突 ${report.conflicts} 条已保留旧数据`)
    } else if (report?.duplicates) {
      flash(`新增 ${report.added} 条，重复跳过 ${report.duplicates} 条`)
    } else {
      flash(`已导入 ${report?.added || data.records.length} 条房态数据`)
    }
    clearCompletedOccupancyImport()
    closeOccupancyPreview()
  } catch (error: any) {
    flash(error?.message || store.error || '导入失败，请稍后重试')
  } finally {
    importingOccupancy.value = false
  }
}

function closeOccupancyPreview() {
  pendingOccupancyImport.value = null
  pendingOccupancyFileName.value = ''
  clearCompletedOccupancyImport()
}

function clearOccupancyData() {
  store.clearOccupancyImport()
  flash('已清空当前汇总数据')
}

function restoreOccupancyHistory(item: OccupancyImportData) {
  store.restoreOccupancyImport(item)
  form.totalRooms = store.totalRooms
  flash('已切换到历史上传图表')
}

function removeOccupancyHistory(item: OccupancyImportData) {
  store.removeOccupancyHistory(item.importedAt)
  flash('已删除这条历史上传记录')
}

function clearOccupancyUploadHistory() {
  store.clearOccupancyHistory()
  flash('已清空历史上传图表')
}

function formatImportTime(value: string) {
  if (!value) return '未知时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function sourceFileLabel(item: OccupancyImportData) {
  const names = item.sourceFileNames?.length ? item.sourceFileNames : [item.sourceFileName]
  return names.filter(Boolean).join('、') || '未命名表格'
}

function triggerSuggest() {
  if (searchTimer.value) window.clearTimeout(searchTimer.value)
  const keyword = form.name.trim()
  if (keyword.length < 2) {
    poiResults.value = []
    poiError.value = ''
    suggestionOpen.value = false
    return
  }
  searchTimer.value = window.setTimeout(() => searchPoi(true), 360)
}

async function searchPoi(silent = false) {
  poiError.value = ''
  const keyword = form.name.trim()
  if (!keyword) {
    poiError.value = '请先输入酒店名称'
    return
  }
  if (!selectedCity.value) {
    poiError.value = '请先选择省市，再搜索酒店'
    return
  }
  poiSearching.value = true
  try {
    poiResults.value = await store.searchPoi(keyword, region.city || selectedCity.value)
    suggestionOpen.value = poiResults.value.length > 0
    if (!poiResults.value.length && !silent) poiError.value = '没有找到酒店类结果，请换一个名称或城市再试'
  } catch (error: any) {
    poiError.value = error?.response?.data?.message || error?.message || '酒店搜索失败，请稍后重试'
  } finally {
    poiSearching.value = false
  }
}

function requestBindPoi(item: HotelPoiCandidate) {
  if (!item.poiId) return
  pendingBindPoi.value = item
  suggestionOpen.value = false
}

function closeBindConfirm() {
  if (poiBinding.value) return
  pendingBindPoi.value = null
}

async function confirmBindPoi() {
  const item = pendingBindPoi.value
  if (!item?.poiId) return
  poiBinding.value = item.poiId
  saving.value = true
  try {
    await store.bindPoiCandidate(item)
    syncFormFromStore()
    pendingBindPoi.value = null
    suggestionOpen.value = false
    poiResults.value = []
    flash('已绑定酒店并保存基础信息')
  } catch (error: any) {
    flash(error?.response?.data?.message || store.error || '真实酒店绑定失败')
  } finally {
    poiBinding.value = ''
    saving.value = false
  }
}

function flash(message: string) {
  toast.value = safeUiText(message, '操作失败，请稍后重试')
  window.setTimeout(() => { toast.value = '' }, 2000)
}

function goRooms() {
  router.push('/rooms')
}

function applyRecommendation(mode: 'all' | 'profile' | 'nearby') {
  const profile = profileSuggestion.value
  if (mode === 'all' || mode === 'profile') {
    if (profile.name) form.name = profile.name
    if (profile.type) form.type = profile.type
    if (profile.city) form.city = profile.city
    if (profile.tags) form.tags = profile.tags
    if (profile.targetAudience) form.targetAudience = profile.targetAudience
  }
  if ((mode === 'all' || mode === 'nearby') && profile.nearby) {
    form.nearby = profile.nearby
  }
  flash('已填充推荐配置，请确认后保存')
  store.clearRecommendation()
}

function ignoreRecommendation() {
  store.clearRecommendation()
}
</script>

<template>
  <div
    class="setup-page relative mx-auto max-w-[1500px] space-y-4 pb-4"
    @dragenter.prevent="pageDragActive = true"
    @dragover.prevent="pageDragActive = true"
    @dragleave.prevent="handlePageDragLeave"
    @drop.prevent="handlePageDrop"
  >
    <transition name="toast">
      <div v-if="toast" class="fixed right-6 top-6 z-50 rounded-lg bg-bamboo-800 px-5 py-3 text-sm text-cream-100 shadow-lg">
        {{ toast }}
      </div>
    </transition>

    <transition name="toast">
      <div
        v-if="pendingBindPoi"
        class="fixed inset-0 z-[70] flex items-center justify-center bg-bamboo-950/45 p-4 backdrop-blur-sm"
        @click.self="closeBindConfirm"
      >
        <div class="w-full max-w-lg overflow-hidden rounded-3xl border border-cream-200 bg-white shadow-2xl">
          <div class="flex items-start justify-between gap-4 border-b border-cream-200 bg-cream-50 px-5 py-4">
            <div>
              <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
                <MapPin class="h-4 w-4 text-bamboo-700" />
                确认绑定酒店
              </div>
              <p class="mt-1 text-xs leading-5 text-warm-500">绑定后会同步酒店信息，并自动保存基础信息。</p>
            </div>
            <button
              type="button"
              class="rounded-full p-1.5 text-warm-500 hover:bg-white hover:text-bamboo-900"
              :disabled="Boolean(poiBinding)"
              @click="closeBindConfirm"
            >
              <X class="h-4 w-4" />
            </button>
          </div>

          <div class="p-5">
            <div class="rounded-2xl border border-bamboo-100 bg-bamboo-50/70 p-4">
              <div class="text-base font-semibold text-bamboo-950">{{ pendingBindPoi.name }}</div>
              <div class="mt-2 text-sm leading-6 text-warm-700">{{ pendingBindPoi.address || '暂无详细地址' }}</div>
              <div class="mt-3 flex flex-wrap gap-2 text-[11px] text-warm-600">
                <span class="rounded-full bg-white px-2.5 py-1">{{ [pendingBindPoi.province, pendingBindPoi.city, pendingBindPoi.district].filter(Boolean).join(' / ') || '城市未知' }}</span>
                <span v-if="pendingBindPoi.typeName" class="rounded-full bg-white px-2.5 py-1">{{ pendingBindPoi.typeName }}</span>
                <span v-if="pendingBindPoi.businessArea" class="rounded-full bg-white px-2.5 py-1">{{ pendingBindPoi.businessArea }}商圈</span>
              </div>
            </div>

            <div class="mt-5 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                type="button"
                class="btn-secondary justify-center"
                :disabled="Boolean(poiBinding)"
                @click="closeBindConfirm"
              >
                取消
              </button>
              <button
                type="button"
                class="btn-primary justify-center"
                :disabled="Boolean(poiBinding)"
                @click="confirmBindPoi"
              >
                <Loader2 v-if="poiBinding" class="h-4 w-4 animate-spin" />
                {{ poiBinding ? '绑定并保存中...' : '确认绑定' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </transition>

    <div
      v-if="pageDragActive"
      class="fixed inset-0 z-40 flex items-center justify-center bg-bamboo-950/35 p-8 backdrop-blur-sm"
    >
      <div class="rounded-3xl border border-bamboo-200 bg-white px-10 py-8 text-center shadow-2xl">
        <Upload class="mx-auto h-8 w-8 text-bamboo-800" />
        <div class="mt-3 text-lg font-semibold text-bamboo-950">松开鼠标上传房态表</div>
        <div class="mt-1 text-sm text-warm-500">支持 .xlsx、.xls、.csv、.tsv</div>
      </div>
    </div>

    <section class="overflow-hidden rounded-3xl border border-cream-300 bg-white shadow-sm">
      <div class="grid lg:grid-cols-[minmax(0,1fr)_360px]">
        <div class="p-4 lg:p-5">
          <div class="flex items-center gap-2 text-xs font-semibold text-bamboo-700">
            <Building class="h-4 w-4" />
            绑定酒店
          </div>
          <h1 class="mt-1 text-lg font-semibold text-bamboo-950 lg:text-xl">绑定酒店，再补充经营资料</h1>
          <p class="mt-1.5 max-w-3xl text-xs leading-5 text-warm-600">
            先选省市区，再输入酒店名称。选择真实酒店后会自动写入名称、地址、商圈和联系方式，并同步到后续 AI 能力。
          </p>
        </div>
        <div class="border-t border-cream-200 bg-bamboo-950 p-4 text-bamboo-50 lg:border-l lg:border-t-0 lg:p-5">
          <div class="flex items-center gap-2 text-sm font-semibold">
            <Sparkles class="h-4 w-4" />
            AI 会优先引用
          </div>
          <div class="mt-3 space-y-2 text-xs leading-5 text-bamboo-100/80">
            <div class="flex gap-2"><BadgeCheck class="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />地图确认的酒店名称、地址、商圈和电话</div>
            <div class="flex gap-2"><BadgeCheck class="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />酒店、民宿、客栈等住宿类 POI</div>
            <div class="flex gap-2"><BadgeCheck class="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />房型、标签、客群和知识库</div>
          </div>
        </div>
      </div>
    </section>

    <section class="grid items-stretch gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
      <div class="flex h-full flex-col gap-4">
        <section v-if="recommendation" class="rounded-3xl border border-bamboo-200 bg-bamboo-50 p-4 shadow-sm lg:p-5">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div>
              <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
                <Sparkles class="h-4 w-4" />
                为您自动推荐配置
              </div>
              <p class="mt-1 text-xs leading-5 text-warm-600">
                {{ recommendation.fallback ? '当前使用地图绑定信息生成基础推荐，周边实时搜索暂未返回可用结果。' : '已根据周边信息整理配置建议。' }}
              </p>
            </div>
            <span class="rounded-full bg-white px-3 py-1 text-[11px] font-semibold text-bamboo-800">{{ recommendation.provider || 'surrounding' }}</span>
          </div>
          <div class="mt-4 grid gap-3 md:grid-cols-4">
            <div v-for="item in recommendationSummary" :key="item.label" class="rounded-2xl bg-white p-3">
              <div class="text-[11px] text-warm-500">{{ item.label }}</div>
              <div class="mt-1 text-lg font-semibold text-bamboo-950">{{ item.value }}</div>
            </div>
          </div>
          <div class="mt-4 rounded-2xl bg-white p-4 text-xs leading-6 text-warm-700">
            <p v-if="profileSuggestion.tags"><strong class="text-bamboo-900">推荐标签：</strong>{{ profileSuggestion.tags }}</p>
            <p v-if="profileSuggestion.targetAudience"><strong class="text-bamboo-900">推荐客群：</strong>{{ profileSuggestion.targetAudience }}</p>
            <p v-if="profileSuggestion.nearby"><strong class="text-bamboo-900">推荐周边：</strong>{{ profileSuggestion.nearby }}</p>
            <p v-if="recommendation.unavailableFields?.length" class="mt-2 text-warm-500">未获取：{{ recommendation.unavailableFields.join('、') }}</p>
          </div>
          <div class="mt-4 flex flex-wrap gap-2">
            <button class="btn-primary" @click="applyRecommendation('all')">全部采纳</button>
            <button class="btn-secondary" @click="applyRecommendation('profile')">只填基础配置</button>
            <button class="btn-secondary" @click="applyRecommendation('nearby')">只填周边信息</button>
            <button class="btn-secondary" @click="ignoreRecommendation">暂不使用</button>
          </div>
        </section>

        <section class="rounded-3xl border border-cream-300 bg-white p-4 shadow-sm">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
                <MapPin class="h-4 w-4" />
                绑定酒店
              </div>
              <p class="mt-1 text-xs leading-5 text-warm-500">省市区固定选择，酒店名称输入时会自动出现关联酒店候选。</p>
            </div>
            <span v-if="boundPoi.verified" class="inline-flex items-center gap-1.5 rounded-full bg-bamboo-50 px-3 py-1 text-xs font-semibold text-bamboo-800">
              <CheckCircle2 class="h-3.5 w-3.5" />
              已绑定
            </span>
          </div>

          <div v-if="boundPoi.verified" class="mt-3 rounded-2xl border border-bamboo-100 bg-bamboo-50/70 p-3">
            <div class="text-sm font-semibold text-bamboo-950">{{ boundPoi.name }}</div>
            <div class="mt-1 text-xs leading-5 text-warm-600">{{ boundPoi.address || '暂无地址' }}</div>
            <div class="mt-2 flex flex-wrap gap-2 text-[11px] text-warm-500">
              <span>{{ boundPoi.city || '城市未知' }}</span>
              <span v-if="boundPoi.type">{{ boundPoi.type }}</span>
            </div>
          </div>

          <div class="mt-3 grid gap-2 md:grid-cols-3">
            <select v-model="region.province" class="input-field" :disabled="regionLoading">
              <option value="">选择省份</option>
              <option v-for="item in provinces" :key="item.adcode" :value="item.name">{{ item.name }}</option>
            </select>
            <select v-model="region.city" class="input-field" :disabled="!region.province">
              <option value="">选择城市</option>
              <option v-for="item in cities" :key="item.adcode" :value="item.name">{{ item.name }}</option>
            </select>
            <select v-model="region.district" class="input-field" :disabled="!region.city || !districts.length">
              <option value="">选择区县</option>
              <option v-for="item in districts" :key="item.adcode" :value="item.name">{{ item.name }}</option>
            </select>
          </div>

          <div class="relative mt-3">
            <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-warm-400" />
            <input
              v-model="form.name"
              class="input-field pl-9 pr-12"
              placeholder="输入酒店名称，例如：开元名都大酒店"
              @input="triggerSuggest"
              @focus="suggestionOpen = poiResults.length > 0"
              @keyup.enter="searchPoi(false)"
            />
            <Loader2 v-if="poiSearching" class="absolute right-4 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-bamboo-700" />

            <div v-if="suggestionOpen && poiResults.length" class="absolute left-0 right-0 top-[calc(100%+8px)] z-30 overflow-hidden rounded-2xl border border-cream-300 bg-white shadow-2xl">
              <button
                v-for="item in poiResults"
                :key="item.poiId"
                class="flex w-full items-start justify-between gap-4 border-b border-cream-100 px-4 py-3 text-left last:border-b-0 hover:bg-cream-50"
                :disabled="Boolean(poiBinding)"
                @mousedown.prevent="requestBindPoi(item)"
              >
                <span class="min-w-0">
                  <span class="block font-semibold text-bamboo-950">{{ item.name }}</span>
                  <span class="mt-1 block text-xs leading-5 text-warm-600">{{ item.address || '暂无详细地址' }}</span>
                  <span class="mt-1 block text-[11px] text-warm-500">{{ poiMeta(item) }}</span>
                </span>
                <Loader2 v-if="poiBinding === item.poiId" class="mt-1 h-4 w-4 shrink-0 animate-spin text-bamboo-700" />
                <span v-else class="mt-0.5 shrink-0 rounded-full bg-bamboo-50 px-2.5 py-1 text-[11px] font-semibold text-bamboo-800">选择</span>
              </button>
            </div>
          </div>

          <div class="mt-2 flex flex-wrap items-center gap-3">
            <button class="btn-secondary justify-center" :disabled="poiSearching" @click="searchPoi(false)">
              <Loader2 v-if="poiSearching" class="h-4 w-4 animate-spin" />
              <Search v-else class="h-4 w-4" />
              搜索酒店
            </button>
            <p v-if="poiError" class="text-xs text-red-500">{{ poiError }}</p>
          </div>
        </section>

        <section class="flex flex-1 flex-col rounded-3xl border border-cream-300 bg-white p-4 shadow-sm">
          <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 class="text-sm font-semibold text-bamboo-950">基础资料</h2>
              <p class="mt-1 text-xs text-warm-500">保存后会同步到创作中心、AI 店长和智能定价。</p>
            </div>
            <div class="flex flex-wrap gap-2">
              <button class="btn-primary" :disabled="saving" @click="handleSave">
                <Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
                {{ saving ? '保存中...' : '保存' }}
              </button>
              <button class="btn-secondary" @click="goRooms">
                配置房型
                <ArrowRight class="h-4 w-4" />
              </button>
            </div>
          </div>

          <div class="grid gap-3 md:grid-cols-2">
            <div>
              <label class="label">酒店名称</label>
              <input v-model="form.name" type="text" class="input-field" placeholder="请输入酒店名称" @input="triggerSuggest" />
            </div>

            <div>
              <label class="label">酒店类型</label>
              <select v-model="form.type" class="input-field">
                <option value="精品民宿">精品民宿</option>
                <option value="度假酒店">度假酒店</option>
                <option value="商务酒店">商务酒店</option>
                <option value="亲子民宿">亲子民宿</option>
                <option value="酒店式公寓">酒店式公寓</option>
                <option value="酒店">酒店</option>
              </select>
            </div>

            <div>
              <label class="label">所在地区</label>
              <input :value="form.city" type="text" class="input-field bg-cream-50 text-warm-600" readonly placeholder="请在上方选择省市区" />
            </div>

            <div>
              <label class="label">客房总数</label>
              <input :value="store.totalRooms" type="number" class="input-field bg-cream-50 text-warm-600" readonly placeholder="请先配置房型" />
            </div>
          </div>

          <div class="mt-3 grid items-start gap-3 md:grid-cols-2">
            <div class="space-y-3">
              <div>
                <label class="label">特色标签</label>
                <input v-model="form.tags" type="text" class="input-field" placeholder="用逗号分隔，如：竹林景观、私汤温泉、亲子友好" />
              </div>

              <div>
                <label class="label">周边信息</label>
                <textarea v-model="form.nearby" class="input-field setup-nearby-field" rows="3" placeholder="如：详细地址、周边景区、步行街、亲子乐园、交通站点、联系电话" />
              </div>
            </div>

            <div>
              <label class="label">目标客群</label>
              <textarea v-model="form.targetAudience" class="input-field setup-audience-field" rows="5" placeholder="比如亲子家庭、情侣周末游、商务差旅客" />
            </div>
          </div>
        </section>
      </div>

      <aside class="flex h-full flex-col gap-3">
        <div class="rounded-3xl border border-cream-300 bg-white p-4 shadow-sm">
          <h2 class="text-sm font-semibold text-bamboo-950">资料预览</h2>
          <div class="mt-3 space-y-2 text-sm">
            <div class="rounded-2xl bg-cream-50 p-3">
              <div class="flex items-center gap-2 text-xs text-warm-500"><Building class="h-3.5 w-3.5" />门店</div>
              <div class="mt-1 font-semibold text-bamboo-950">{{ form.name || '未填写酒店名称' }}</div>
              <div class="mt-0.5 text-xs text-warm-500">{{ form.type || '未选择类型' }} / {{ form.totalRooms || 0 }} 间房</div>
            </div>
            <div class="grid grid-cols-2 gap-2">
              <div class="rounded-2xl bg-cream-50 p-3">
                <div class="text-[11px] text-warm-500">地区</div>
                <div class="mt-1 text-sm font-semibold text-bamboo-950">{{ form.city || '未选择地区' }}</div>
              </div>
              <div class="rounded-2xl bg-cream-50 p-3">
                <div class="text-[11px] text-warm-500">标签数</div>
                <div class="mt-1 text-sm font-semibold text-bamboo-950">{{ tags.length || 0 }}</div>
              </div>
            </div>
            <div class="rounded-2xl bg-cream-50 p-3">
              <div class="flex items-center gap-2 text-xs text-warm-500"><Tags class="h-3.5 w-3.5" />特色标签</div>
              <div class="mt-2 flex flex-wrap gap-1.5">
                <span v-for="tag in tags" :key="tag" class="rounded-full bg-bamboo-100 px-2 py-0.5 text-[11px] text-bamboo-800">{{ tag }}</span>
                <span v-if="!tags.length" class="text-xs text-warm-500">暂无标签</span>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-3xl border border-bamboo-200 bg-bamboo-50 p-3.5">
          <h2 class="text-sm font-semibold text-bamboo-950">建议填写方式</h2>
          <p class="mt-1.5 text-xs leading-5 text-warm-600">先选省市，再从联想酒店中选择真实门店。上传历史房态表后，出租率和房型表现会纳入分析。</p>
        </div>

        <div
          class="flex flex-1 flex-col rounded-3xl border bg-white p-4 shadow-sm transition"
          :class="occupancyDragActive ? 'border-bamboo-500 bg-bamboo-50 shadow-md' : 'border-cream-300'"
          @dragenter.prevent="occupancyDragActive = true"
          @dragover.prevent="occupancyDragActive = true"
          @dragleave.prevent="handleOccupancyDragLeave"
          @drop.prevent="handleOccupancyDrop"
        >
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
                <Table2 class="h-4 w-4" />
                历史房态表
              </div>
              <p class="mt-1 text-xs leading-5 text-warm-500">不定期上传也可以，系统会按日期和房型合并，重复数据自动跳过；不一致数据会保留旧值，避免误覆盖。</p>
            </div>
          </div>

          <input ref="occupancyFileInput" class="hidden" type="file" accept=".xlsx,.xls,.csv,.tsv,.png,.jpg,.jpeg,.webp" @change="handleOccupancyUpload" />
          <div
            class="mt-3 flex min-h-[66px] flex-col items-center justify-center rounded-2xl border border-dashed px-3 py-3 text-center transition"
            :class="occupancyDragActive ? 'border-bamboo-500 bg-white text-bamboo-900' : 'border-cream-300 bg-cream-50 text-warm-500'"
          >
            <Loader2 v-if="importingOccupancy" class="h-5 w-5 animate-spin text-bamboo-700" />
            <Upload v-else class="h-5 w-5 text-bamboo-700" />
            <div class="mt-1 text-sm font-semibold text-bamboo-950">{{ importingOccupancy ? '解析中...' : '拖拽表格或图片上传' }}</div>
            <div class="mt-0.5 text-xs leading-5">支持表格、图片</div>
          </div>

          <div class="mt-3 grid gap-2">
            <button class="btn-secondary justify-center" :disabled="importingOccupancy" @click="occupancyFileInput?.click()">
              <Loader2 v-if="importingOccupancy" class="h-4 w-4 animate-spin" />
              <Upload v-else class="h-4 w-4" />
              {{ importingOccupancy ? '解析中...' : '上传表格/图片' }}
            </button>
            <button class="btn-secondary justify-center" @click="router.push('/setup/occupancy-history')">
              <Table2 class="h-4 w-4" />
              查看房态导入
            </button>
          </div>

          <div v-if="occupancyImport" class="mt-3 rounded-2xl border border-bamboo-100 bg-bamboo-50 p-3">
            <div class="flex items-center justify-between gap-3">
              <span class="text-xs font-semibold text-bamboo-900">当前已导入 {{ occupancyImport.roomTypeSummaries.length }} 个房型</span>
              <span class="rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-bamboo-800">{{ occupancyRateText }}</span>
            </div>
            <div class="mt-1 truncate text-[11px] text-warm-500">{{ occupancyImport.dateRange || '已导入周期' }}</div>
          </div>

          <div v-if="!occupancyImport" class="mt-3 rounded-2xl border border-dashed border-cream-300 bg-cream-50 p-3 text-center text-xs leading-5 text-warm-500">
            支持日期横向展开表：每个日期下包含占用房、剩余可售，也可包含出租率。
          </div>

          <div v-if="false" class="mt-5 border-t border-cream-200 pt-4">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h3 class="text-sm font-semibold text-bamboo-950">房态导入</h3>
                <p class="mt-0.5 text-xs leading-5 text-warm-500">每次上传都会保留一份快照，点击记录可切回查看。</p>
              </div>
              <button
                v-if="occupancyHistory.length"
                class="text-[11px] font-semibold text-warm-500 hover:text-red-500"
                @click="clearOccupancyUploadHistory"
              >
                清空历史
              </button>
            </div>

            <div v-if="occupancyHistory.length" class="mt-3 max-h-72 space-y-2 overflow-y-auto pr-1">
              <article
                v-for="item in occupancyHistory"
                :key="item.importedAt"
                class="rounded-2xl border p-3 transition"
                :class="activeOccupancyImportedAt === item.importedAt ? 'border-bamboo-300 bg-bamboo-50' : 'border-cream-200 bg-cream-50 hover:border-bamboo-200 hover:bg-white'"
              >
                <button class="w-full text-left" @click="restoreOccupancyHistory(item)">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                      <span class="mb-1 inline-flex rounded-full bg-white px-2 py-0.5 text-[10px] font-semibold text-bamboo-700">房态导入</span>
                      <div class="truncate text-sm font-semibold text-bamboo-950">{{ sourceFileLabel(item) }}</div>
                      <div class="mt-0.5 text-[11px] text-warm-500">{{ item.dateRange || '未识别周期' }} / {{ formatImportTime(item.importedAt) }}</div>
                    </div>
                    <span class="shrink-0 rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-bamboo-700">
                      {{ activeOccupancyImportedAt === item.importedAt ? '当前' : '查看' }}
                    </span>
                  </div>
                  <div class="mt-3 grid grid-cols-3 gap-2 text-center">
                    <div class="rounded-xl bg-white px-2 py-2">
                      <div class="text-[10px] text-warm-400">出租率</div>
                      <div class="mt-0.5 text-xs font-semibold text-bamboo-900">{{ Math.round(item.averageOccupancyRate * 100) }}%</div>
                    </div>
                    <div class="rounded-xl bg-white px-2 py-2">
                      <div class="text-[10px] text-warm-400">房型</div>
                      <div class="mt-0.5 text-xs font-semibold text-bamboo-900">{{ item.roomTypeSummaries.length }}</div>
                    </div>
                    <div class="rounded-xl bg-white px-2 py-2">
                      <div class="text-[10px] text-warm-400">房晚</div>
                      <div class="mt-0.5 text-xs font-semibold text-bamboo-900">{{ item.occupiedRoomNights }}/{{ item.totalRoomNights }}</div>
                    </div>
                  </div>
                </button>
                <button class="mt-2 text-[11px] font-semibold text-warm-400 hover:text-red-500" @click.stop="removeOccupancyHistory(item)">
                  删除记录
                </button>
              </article>
            </div>

            <div v-else class="mt-3 rounded-2xl border border-dashed border-cream-300 bg-cream-50 p-4 text-center text-xs leading-5 text-warm-500">
              暂无历史上传图表，上传表格后会自动保存在这里。
            </div>
          </div>
        </div>
      </aside>
    </section>

    <div v-if="pendingOccupancyImport" class="fixed inset-0 z-50 flex items-center justify-center bg-bamboo-950/45 p-4" @click.self="closeOccupancyPreview">
      <section class="w-full max-w-5xl overflow-hidden rounded-3xl border border-cream-300 bg-white shadow-2xl">
        <div class="flex items-start justify-between gap-4 border-b border-cream-200 p-5">
          <div>
            <h2 class="text-lg font-semibold text-bamboo-950">确认导入房态表</h2>
            <p class="mt-1 text-sm text-warm-500">{{ pendingOccupancyFileName }} 已解析完成，请确认后写入工作台数据。</p>
          </div>
          <button class="rounded-xl border border-cream-300 p-2 text-warm-500 hover:bg-cream-50" @click="closeOccupancyPreview">
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="grid gap-4 p-5 md:grid-cols-4">
          <div class="rounded-2xl bg-cream-50 p-4">
            <div class="text-xs text-warm-500">数据周期</div>
            <div class="mt-2 text-base font-semibold text-bamboo-950">{{ pendingOccupancyImport.dateRange || '-' }}</div>
          </div>
          <div class="rounded-2xl bg-cream-50 p-4">
            <div class="text-xs text-warm-500">平均出租率</div>
            <div class="mt-2 text-2xl font-semibold text-bamboo-950">{{ Math.round(pendingOccupancyImport.averageOccupancyRate * 100) }}%</div>
          </div>
          <div class="rounded-2xl bg-cream-50 p-4">
            <div class="text-xs text-warm-500">占用房晚</div>
            <div class="mt-2 text-base font-semibold text-bamboo-950">{{ pendingOccupancyImport.occupiedRoomNights }} / {{ pendingOccupancyImport.totalRoomNights }}</div>
          </div>
          <div class="rounded-2xl bg-cream-50 p-4">
            <div class="text-xs text-warm-500">房型数量</div>
            <div class="mt-2 text-2xl font-semibold text-bamboo-950">{{ pendingOccupancyImport.roomTypeSummaries.length }}</div>
          </div>
        </div>

        <div class="max-h-[220px] overflow-y-auto px-5 pb-5">
          <div class="mb-2 text-sm font-semibold text-bamboo-950">房型汇总</div>
          <table class="w-full text-left text-sm">
            <thead class="sticky top-0 bg-white text-xs text-warm-500">
              <tr class="border-b border-cream-200">
                <th class="py-2 pr-3 font-semibold">房型</th>
                <th class="py-2 pr-3 font-semibold">房量</th>
                <th class="py-2 pr-3 font-semibold">天数</th>
                <th class="py-2 pr-3 font-semibold">占用房晚</th>
                <th class="py-2 pr-3 font-semibold">平均出租率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="room in pendingOccupancyImport.roomTypeSummaries" :key="room.roomTypeName" class="border-b border-cream-100 last:border-0">
                <td class="py-3 pr-3 font-semibold text-bamboo-950">{{ room.roomTypeName }}</td>
                <td class="py-3 pr-3 text-warm-700">{{ room.totalRooms }}</td>
                <td class="py-3 pr-3 text-warm-700">{{ room.days }}</td>
                <td class="py-3 pr-3 text-warm-700">{{ room.occupiedRoomNights }}</td>
                <td class="py-3 pr-3 font-semibold text-bamboo-800">{{ Math.round(room.averageOccupancyRate * 100) }}%</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="max-h-[280px] overflow-y-auto border-t border-cream-200 px-5 py-5">
          <div class="mb-2 text-sm font-semibold text-bamboo-950">识别明细</div>
          <table class="w-full text-left text-sm">
            <thead class="sticky top-0 bg-white text-xs text-warm-500">
              <tr class="border-b border-cream-200">
                <th class="py-2 pr-3 font-semibold">日期</th>
                <th class="py-2 pr-3 font-semibold">房型</th>
                <th class="py-2 pr-3 font-semibold">总房数</th>
                <th class="py-2 pr-3 font-semibold">占用</th>
                <th class="py-2 pr-3 font-semibold">剩余可售</th>
                <th class="py-2 pr-3 font-semibold">出租率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pendingOccupancyImport.records" :key="`${record.date}-${record.roomTypeName}`" class="border-b border-cream-100 last:border-0">
                <td class="py-3 pr-3 text-warm-700">{{ record.date }}</td>
                <td class="py-3 pr-3 font-semibold text-bamboo-950">{{ record.roomTypeName }}</td>
                <td class="py-3 pr-3 text-warm-700">{{ record.totalRooms }}</td>
                <td class="py-3 pr-3 text-warm-700">{{ record.occupiedRooms }}</td>
                <td class="py-3 pr-3 text-warm-700">{{ record.remainingRooms }}</td>
                <td class="py-3 pr-3 font-semibold text-bamboo-800">{{ Math.round(record.occupancyRate * 100) }}%</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="flex justify-end gap-3 border-t border-cream-200 p-5">
          <button class="btn-secondary" :disabled="importingOccupancy" @click="closeOccupancyPreview">取消</button>
          <button class="btn-primary" :disabled="importingOccupancy" @click="confirmOccupancyImport">
            <Loader2 v-if="importingOccupancy" class="h-4 w-4 animate-spin" />
            确认导入
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.setup-page .input-field {
  min-height: 36px;
  padding-top: 0.45rem;
  padding-bottom: 0.45rem;
}

.setup-page textarea.input-field {
  min-height: 88px;
}

.setup-page .setup-nearby-field,
.setup-page .setup-audience-field {
  min-height: 88px;
  resize: vertical;
}

.setup-page .label {
  margin-bottom: 0.35rem;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>

