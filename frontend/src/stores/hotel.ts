import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import api from '@/api'
import { bindHotelPoi, bindHotelPoiCandidate, deleteRoomType as deleteRoomTypeApi, getCurrentWeather, getOccupancyImportRecords, getRoomTypes, getSurroundingRecommendation, importOccupancyRecords, saveConfig as saveConfigApi, saveRoomTypes as saveRoomTypesApi, searchHotelPoi } from '@/api/hotel'
import { buildImportData, mergeOccupancyImport, type OccupancyImportData, type OccupancyMergeReport, type OccupancyRecord, type RoomOccupancySummary } from '@/utils/occupancyImport'

export interface HotelConfig {
  name: string
  type: string
  city: string
  totalRooms: number
  tags: string
  targetAudience: string
  nearby: string
  poiProvider?: string
  poiId?: string
  poiName?: string
  poiAddress?: string
  poiProvince?: string
  poiCity?: string
  poiDistrict?: string
  poiAdcode?: string
  poiLongitude?: number | string | null
  poiLatitude?: number | string | null
  poiTypeCode?: string
  poiTypeName?: string
  poiVerified?: boolean
  poiSyncedAt?: string
}

export interface HotelPoiCandidate {
  provider: string
  poiId: string
  name: string
  address: string
  province: string
  city: string
  district: string
  adcode: string
  longitude: number | string | null
  latitude: number | string | null
  typeCode: string
  typeName: string
  keytag?: string
  tel?: string
  businessArea?: string
  rating?: string
  lowestPrice?: number | string | null
}

export interface RoomType {
  id: string
  name: string
  basePrice: number
  count: number
}

const configStorageKey = 'sushijia:hotel-config'
const roomTypesStorageKey = 'sushijia:room-types'
const occupancyStorageKey = 'sushijia:occupancy-import'
const occupancyHistoryStorageKey = 'sushijia:occupancy-import-history'
const maxOccupancyHistoryItems = 20

export interface WeatherNow {
  province?: string
  city?: string
  adcode?: string
  weather?: string
  temperature?: string
  windDirection?: string
  windPower?: string
  humidity?: string
  reportTime?: string
}

export interface SurroundingRecommendation {
  provider?: string
  fallback?: boolean
  queriedAt?: string
  hotelProfileSuggestion?: {
    name?: string
    type?: string
    city?: string
    tags?: string
    targetAudience?: string
    nearby?: string
    businessArea?: string
  }
  currentHotelPrices?: any[]
  nearbyHotelPrices?: any[]
  nearbyHotPlaces?: any[]
  localEvents?: any[]
  searchEvidence?: any[]
  unavailableFields?: string[]
}

const emptyConfig: HotelConfig = {
  name: '',
  type: '',
  city: '',
  totalRooms: 0,
  tags: '',
  targetAudience: '',
  nearby: '',
}

function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7)
}

function normalizeRoomTypeRows(rows: any[]): RoomType[] {
  return rows.map((room: any) => ({
    id: String(room.id),
    name: room.name,
    basePrice: Number(room.basePrice || 0),
    count: Number(room.count ?? room.total ?? 0),
  }))
}

function normalizeRoomName(value: string) {
  return String(value || '')
    .replace(/(?:[（(]\s*\d+\s*[)）])+\s*$/g, '')
    .replace(/\s*\d+\s*间\s*$/g, '')
    .replace(/\s+/g, '')
    .trim()
}

function safeRate(numerator: number, denominator: number) {
  return denominator > 0 ? numerator / denominator : 0
}

function rebuildOccupancyData(data: OccupancyImportData, records: OccupancyRecord[]): OccupancyImportData | null {
  if (!records.length) return null
  const sortedRecords = [...records].sort((a, b) => a.date.localeCompare(b.date) || a.roomTypeName.localeCompare(b.roomTypeName))
  const dates = Array.from(new Set(sortedRecords.map(record => record.date))).sort()
  const totalRoomNights = sortedRecords.reduce((sum, record) => sum + record.totalRooms, 0)
  const occupiedRoomNights = sortedRecords.reduce((sum, record) => sum + record.occupiedRooms, 0)
  const remainingRoomNights = sortedRecords.reduce((sum, record) => sum + record.remainingRooms, 0)
  const grouped = new Map<string, OccupancyRecord[]>()
  for (const record of sortedRecords) {
    const key = normalizeRoomName(record.roomTypeName)
    grouped.set(key, [...(grouped.get(key) || []), record])
  }
  const roomTypeSummaries: RoomOccupancySummary[] = Array.from(grouped.entries()).map(([roomTypeName, rows]) => {
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

  return {
    ...data,
    dateRange: dates.length ? `${dates[0]} 至 ${dates[dates.length - 1]}` : '',
    records: sortedRecords,
    roomTypeSummaries,
    averageOccupancyRate: safeRate(occupiedRoomNights, totalRoomNights),
    totalRoomNights,
    occupiedRoomNights,
    remainingRoomNights,
  }
}

function loadStoredConfig(): HotelConfig {
  try {
    const raw = window.localStorage?.getItem(configStorageKey)
    const parsed = raw ? JSON.parse(raw) : null
    return parsed && typeof parsed === 'object' ? { ...emptyConfig, ...parsed } : { ...emptyConfig }
  } catch {
    return { ...emptyConfig }
  }
}

function saveStoredConfig(data: HotelConfig) {
  try {
    window.localStorage?.setItem(configStorageKey, JSON.stringify(data))
  } catch {
    // Local persistence is best-effort only.
  }
}

function loadStoredRoomTypes(): RoomType[] {
  try {
    const raw = window.localStorage?.getItem(roomTypesStorageKey)
    const rows = raw ? JSON.parse(raw) : []
    return Array.isArray(rows)
      ? rows.map(room => ({
          id: String(room.id || generateId()),
          name: String(room.name || ''),
          basePrice: Number(room.basePrice || 0),
          count: Number(room.count || 0),
        }))
      : []
  } catch {
    return []
  }
}

function saveStoredRoomTypes(data: RoomType[]) {
  try {
    window.localStorage?.setItem(roomTypesStorageKey, JSON.stringify(data))
  } catch {
    // Local persistence is best-effort only.
  }
}

function loadStoredOccupancy(): OccupancyImportData | null {
  try {
    const raw = window.localStorage?.getItem(occupancyStorageKey)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function saveStoredOccupancy(data: OccupancyImportData | null) {
  try {
    if (!data) window.localStorage?.removeItem(occupancyStorageKey)
    else window.localStorage?.setItem(occupancyStorageKey, JSON.stringify(data))
  } catch {
    // Local persistence is best-effort only.
  }
}

function loadStoredOccupancyHistory(): OccupancyImportData[] {
  try {
    const raw = window.localStorage?.getItem(occupancyHistoryStorageKey)
    const rows = raw ? JSON.parse(raw) : []
    return Array.isArray(rows) ? rows : []
  } catch {
    return []
  }
}

function saveStoredOccupancyHistory(data: OccupancyImportData[]) {
  try {
    window.localStorage?.setItem(occupancyHistoryStorageKey, JSON.stringify(data))
  } catch {
    // Local persistence is best-effort only.
  }
}

function regionTextFromPoi(poi: Partial<HotelPoiCandidate | HotelConfig>) {
  const province = String((poi as any).province || (poi as any).poiProvince || '').trim()
  const city = String((poi as any).city || (poi as any).poiCity || '').trim()
  const district = String((poi as any).district || (poi as any).poiDistrict || '').trim()
  return [province, city, district]
    .filter((item, index, list) => item && list.indexOf(item) === index)
    .join(' / ')
}

function inferHotelTypeFromPoi(poi: Partial<HotelPoiCandidate | HotelConfig>) {
  const text = [
    (poi as any).typeName || (poi as any).poiTypeName || '',
    (poi as any).name || (poi as any).poiName || '',
    (poi as any).address || (poi as any).poiAddress || '',
    (poi as any).keytag || '',
    (poi as any).businessArea || '',
  ].join(' ').toLowerCase()
  if (/亲子|儿童|乐园|family|kids/.test(text)) return '亲子民宿'
  if (/民宿|客栈|homestay|inn|guesthouse/.test(text)) return '精品民宿'
  if (/度假|温泉|山庄|resort|spa|villa/.test(text)) return '度假酒店'
  if (/公寓|apartment/.test(text)) return '酒店式公寓'
  if (/商务|国际|广场|business|international|plaza/.test(text)) return '商务酒店'
  return '酒店'
}

function inferTagsFromPoi(poi: Partial<HotelPoiCandidate | HotelConfig>) {
  const text = `${(poi as any).name || (poi as any).poiName || ''} ${(poi as any).address || (poi as any).poiAddress || ''} ${(poi as any).typeName || (poi as any).poiTypeName || ''}`
  const tags: string[] = []
  if (/学校|大学|学院/.test(text)) tags.push('附近学校较多')
  if (/地铁|车站|机场|高铁|火车/.test(text)) tags.push('交通便利')
  if (/商圈|CBD|广场|中心|购物/.test(text)) tags.push('商圈便利')
  if (/景区|公园|山|湖|竹|温泉/.test(text)) tags.push('周边游友好')
  if (/民宿|客栈/.test(text)) tags.push('本地体验')
  if (!tags.length) tags.push('出行便利')
  return tags.join('、')
}

function inferAudienceFromPoi(poi: Partial<HotelPoiCandidate | HotelConfig>) {
  const type = inferHotelTypeFromPoi(poi)
  const text = `${(poi as any).name || (poi as any).poiName || ''} ${(poi as any).address || (poi as any).poiAddress || ''}`
  if (/学校|大学|学院/.test(text)) return '商务差旅客、亲子家庭、探校访友、城市短途游客'
  if (type === '精品民宿') return '周末度假客、情侣客群、亲子家庭、短途旅行客'
  if (type === '度假酒店') return '亲子家庭、情侣度假、团建客群、休闲旅行客'
  if (type === '商务酒店') return '商务差旅客、会议会展客、城市短住游客'
  return '商务差旅客、亲子家庭、情侣客群、短途旅行客'
}

function inferNearbyFromPoi(poi: Partial<HotelPoiCandidate | HotelConfig>) {
  const parts = [
    (poi as any).address || (poi as any).poiAddress ? `详细地址：${(poi as any).address || (poi as any).poiAddress}` : '',
    (poi as any).businessArea ? `商圈：${(poi as any).businessArea}` : '',
    (poi as any).district || (poi as any).poiDistrict ? `所在区县：${(poi as any).district || (poi as any).poiDistrict}` : '',
    (poi as any).tel ? `联系电话：${(poi as any).tel}` : '',
  ].filter(Boolean)
  return parts.join('；') || '已绑定真实门店位置，可补充周边商圈、交通枢纽和景点信息。'
}

function withBoundProfile(base: HotelConfig, poi?: Partial<HotelPoiCandidate | HotelConfig>): HotelConfig {
  const source = poi || base
  return {
    ...base,
    name: String((source as any).name || (source as any).poiName || base.poiName || base.name || '').trim(),
    type: inferHotelTypeFromPoi(source),
    city: regionTextFromPoi(source) || base.city,
    tags: inferTagsFromPoi(source),
    targetAudience: inferAudienceFromPoi(source),
    nearby: inferNearbyFromPoi(source),
  }
}

export const useHotelStore = defineStore('hotel', () => {
  const config = ref<HotelConfig>(loadStoredConfig())
  const roomTypes = ref<RoomType[]>(loadStoredRoomTypes())
  const occupancyImport = ref<OccupancyImportData | null>(loadStoredOccupancy())
  const occupancyHistory = ref<OccupancyImportData[]>(loadStoredOccupancyHistory())
  const lastOccupancyImportReport = ref<OccupancyMergeReport | null>(null)
  const weather = ref<WeatherNow | null>(null)
  const pendingRecommendation = ref<SurroundingRecommendation | null>(null)
  const weatherLoading = ref(false)
  const loading = ref(false)
  const error = ref('')

  const totalRooms = computed(() => roomTypes.value.reduce((sum, room) => sum + Number(room.count || 0), 0))
  const occupancySummaryText = computed(() => {
    const data = occupancyImport.value
    if (!data) return ''
    const rate = Math.round(data.averageOccupancyRate * 100)
    const hotRooms = data.roomTypeSummaries.slice(0, 3).map(room => `${room.roomTypeName} ${Math.round(room.averageOccupancyRate * 100)}%`)
    return `${data.dateRange || '已上传周期'} 平均出租率 ${rate}%；房晚 ${data.occupiedRoomNights}/${data.totalRoomNights}；重点房型：${hotRooms.join('、') || '待分析'}`
  })

  async function saveConfig(cfg: HotelConfig) {
    error.value = ''
    try {
      const next = { ...cfg, totalRooms: totalRooms.value }
      await saveConfigApi(next)
      config.value = next
      saveStoredConfig(config.value)
    } catch {
      error.value = '酒店资料保存失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function saveRoomTypes(rooms: RoomType[]) {
    error.value = ''
    try {
      const { data: response } = await saveRoomTypesApi(rooms)
      const savedRows = response?.data || response
      roomTypes.value = Array.isArray(savedRows)
        ? normalizeRoomTypeRows(savedRows)
        : rooms.map(room => ({ ...room }))
      syncOccupancyToRooms(roomTypes.value)
      config.value = { ...config.value, totalRooms: totalRooms.value }
      saveStoredRoomTypes(roomTypes.value)
      saveStoredConfig(config.value)
    } catch {
      error.value = '房型保存失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  function syncOccupancyToRooms(rooms: RoomType[]) {
    if (!occupancyImport.value) return
    const roomNames = new Set(rooms.map(room => normalizeRoomName(room.name)).filter(Boolean))
    if (!roomNames.size) {
      occupancyImport.value = null
      lastOccupancyImportReport.value = null
      saveStoredOccupancy(null)
      return
    }
    const keptRecords = occupancyImport.value.records.filter(record => roomNames.has(normalizeRoomName(record.roomTypeName)))
    if (keptRecords.length === occupancyImport.value.records.length) return
    occupancyImport.value = rebuildOccupancyData(occupancyImport.value, keptRecords)
    lastOccupancyImportReport.value = null
    saveStoredOccupancy(occupancyImport.value)
  }

  async function applyOccupancyImport(data: OccupancyImportData, options: { persistRooms?: boolean } = {}) {
    let merged = mergeOccupancyImport(occupancyImport.value, data, { conflictStrategy: 'keep-existing' })
    try {
      const { data: response } = await importOccupancyRecords({
        sourceFileName: data.sourceFileName,
        importedAt: data.importedAt,
        overwrite: false,
        records: data.records,
      })
      const payload = response?.data || response
      const serverRecords = Array.isArray(payload?.records) ? payload.records : []
      const serverData = serverRecords.length
        ? buildImportData(payload?.sourceFileName || data.sourceFileName, serverRecords)
        : merged.data
      serverData.sourceFileNames = merged.data.sourceFileNames
      merged = {
        data: serverData,
        report: {
          added: Number(payload?.report?.added || 0),
          duplicates: Number(payload?.report?.duplicates || 0),
          conflicts: Number(payload?.report?.conflicts || 0),
          skippedConflicts: Number(payload?.report?.skippedConflicts || 0),
          total: Number(payload?.report?.total || data.records.length),
          conflictSamples: merged.report.conflictSamples,
        },
      }
    } catch {
      error.value = '房态数据保存失败，请稍后重试'
      throw new Error(error.value)
    }
    occupancyImport.value = merged.data
    lastOccupancyImportReport.value = merged.report
    saveStoredOccupancy(merged.data)
    addOccupancyHistory(data)

    const importedRooms = merged.data.roomTypeSummaries.map(summary => {
      const existing = roomTypes.value.find(room => normalizeRoomName(room.name) === normalizeRoomName(summary.roomTypeName))
      return {
        id: existing?.id || generateId(),
        name: existing?.name || summary.roomTypeName,
        basePrice: existing?.basePrice || 0,
        count: existing?.count || summary.totalRooms,
      }
    })
    const knownNames = new Set(importedRooms.map(room => normalizeRoomName(room.name)))
    const mergedRooms = [
      ...importedRooms,
      ...roomTypes.value.filter(room => !knownNames.has(normalizeRoomName(room.name))),
    ]
    roomTypes.value = mergedRooms
    config.value = {
      ...config.value,
      totalRooms: mergedRooms.reduce((sum, room) => sum + Number(room.count || 0), 0),
    }
    saveStoredRoomTypes(roomTypes.value)
    saveStoredConfig(config.value)

    if (options.persistRooms) {
      await saveRoomTypes(mergedRooms)
      await saveConfig(config.value)
    }
  }

  function clearOccupancyImport() {
    occupancyImport.value = null
    lastOccupancyImportReport.value = null
    saveStoredOccupancy(null)
  }

  function addOccupancyHistory(data: OccupancyImportData) {
    const next = [
      data,
      ...occupancyHistory.value.filter(item => item.importedAt !== data.importedAt),
    ].slice(0, maxOccupancyHistoryItems)
    occupancyHistory.value = next
    saveStoredOccupancyHistory(next)
  }

  function restoreOccupancyImport(data: OccupancyImportData) {
    occupancyImport.value = data
    lastOccupancyImportReport.value = null
    saveStoredOccupancy(data)
  }

  function removeOccupancyHistory(importedAt: string) {
    occupancyHistory.value = occupancyHistory.value.filter(item => item.importedAt !== importedAt)
    saveStoredOccupancyHistory(occupancyHistory.value)
  }

  function clearOccupancyHistory() {
    occupancyHistory.value = []
    saveStoredOccupancyHistory([])
  }

  async function addRoomType(room: Omit<RoomType, 'id'>) {
    await saveRoomTypes([...roomTypes.value, { ...room, id: generateId() }])
  }

  async function removeRoomType(id: string) {
    error.value = ''
    const numericId = Number(id)
    if (Number.isFinite(numericId) && numericId > 0 && String(numericId) === String(id)) {
      try {
        const { data: response } = await deleteRoomTypeApi(id)
        const rows = response?.data || response || []
        roomTypes.value = Array.isArray(rows)
          ? normalizeRoomTypeRows(rows)
          : roomTypes.value.filter(room => room.id !== id)
        syncOccupancyToRooms(roomTypes.value)
        config.value = { ...config.value, totalRooms: totalRooms.value }
        saveStoredRoomTypes(roomTypes.value)
        saveStoredConfig(config.value)
        return
      } catch {
        error.value = '房型删除失败，请稍后重试'
        throw new Error(error.value)
      }
    }
    await saveRoomTypes(roomTypes.value.filter(room => room.id !== id))
  }

  async function searchPoi(keyword: string, city?: string) {
    const { data: response } = await searchHotelPoi({ keyword, city })
    return (response.data || response || []) as HotelPoiCandidate[]
  }

  async function bindPoi(poiId: string) {
    error.value = ''
    try {
      const { data: response } = await bindHotelPoi({ poiId })
      const payload = response.data || response
      const updated = normalizeBindingResponse(payload)
      config.value = updated ? withBoundProfile({ ...emptyConfig, ...updated }) : config.value
      await saveConfig(config.value)
      await refreshRoomTypes()
      fetchWeather().catch(() => {})
      return config.value
    } catch {
      error.value = '真实酒店绑定失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function bindPoiCandidate(candidate: HotelPoiCandidate) {
    error.value = ''
    try {
      const { data: response } = await bindHotelPoiCandidate(candidate)
      const payload = response.data || response
      const updated = normalizeBindingResponse(payload)
      config.value = updated ? withBoundProfile({ ...emptyConfig, ...updated }, candidate) : config.value
      await saveConfig(config.value)
      await refreshRoomTypes()
      fetchWeather().catch(() => {})
      return config.value
    } catch {
      error.value = '真实酒店绑定失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function loadFromApi() {
    loading.value = true
    error.value = ''
    try {
      const { data: response } = await api.get('/api/hotel/dashboard')
      const dashboard = response.data || response
      if (dashboard.config) {
        config.value = { ...emptyConfig, ...dashboard.config }
        saveStoredConfig(config.value)
      }
      roomTypes.value = Array.isArray(dashboard.roomTypeStats)
        ? normalizeRoomTypeRows(dashboard.roomTypeStats)
        : roomTypes.value
      if (Array.isArray(dashboard.roomTypeStats)) {
        syncOccupancyToRooms(roomTypes.value)
        config.value = { ...config.value, totalRooms: totalRooms.value }
        saveStoredRoomTypes(roomTypes.value)
        saveStoredConfig(config.value)
      }
      await refreshOccupancyImport().catch(() => {})
    } catch {
      error.value = '酒店资料加载失败，请稍后重试'
      throw new Error(error.value)
    } finally {
      loading.value = false
    }
  }

  async function refreshRoomTypes() {
    const { data: response } = await getRoomTypes()
    const rows = response.data || response || []
    roomTypes.value = Array.isArray(rows) ? normalizeRoomTypeRows(rows) : []
    syncOccupancyToRooms(roomTypes.value)
    saveStoredRoomTypes(roomTypes.value)
  }

  async function refreshOccupancyImport() {
    const { data: response } = await getOccupancyImportRecords()
    const payload = response?.data || response
    const records = Array.isArray(payload?.records) ? payload.records : []
    if (!records.length) return
    const data = buildImportData(payload?.sourceFileName || '历史房态记录', records)
    occupancyImport.value = data
    saveStoredOccupancy(data)
  }

  async function fetchWeather() {
    if (weatherLoading.value) return weather.value
    weatherLoading.value = true
    try {
      const { data: response } = await getCurrentWeather()
      weather.value = response.data || response || null
      return weather.value
    } finally {
      weatherLoading.value = false
    }
  }

  async function fetchSurroundingRecommendation() {
    const { data: response } = await getSurroundingRecommendation()
    pendingRecommendation.value = response.data || response || null
    return pendingRecommendation.value
  }

  function clearRecommendation() {
    pendingRecommendation.value = null
  }

  function normalizeBindingResponse(payload: any) {
    if (payload?.config) {
      pendingRecommendation.value = null
      return payload.config
    }
    pendingRecommendation.value = null
    return payload
  }

  return {
    config,
    roomTypes,
    weather,
    occupancyImport,
    occupancyHistory,
    lastOccupancyImportReport,
    pendingRecommendation,
    weatherLoading,
    loading,
    error,
    totalRooms,
    occupancySummaryText,
    saveConfig,
    saveRoomTypes,
    applyOccupancyImport,
    clearOccupancyImport,
    restoreOccupancyImport,
    removeOccupancyHistory,
    clearOccupancyHistory,
    addRoomType,
    removeRoomType,
    searchPoi,
    bindPoi,
    bindPoiCandidate,
    refreshRoomTypes,
    refreshOccupancyImport,
    fetchWeather,
    fetchSurroundingRecommendation,
    clearRecommendation,
    loadFromApi,
  }
})

