import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'
import { saveConfig as saveConfigApi, saveRoomTypes as saveRoomTypesApi } from '@/api/hotel'

export interface HotelConfig {
  name: string
  type: string
  city: string
  totalRooms: number
  tags: string
  targetAudience: string
  nearby: string
}

export interface RoomType {
  id: string
  name: string
  basePrice: number
  count: number
}

export interface RoomItem {
  number: string
  status: 'sold' | 'free' | 'dirty' | 'repair'
}

export interface RoomStatus {
  roomTypeId: string
  rooms: RoomItem[]
}

export interface FutureRoomTypeStatus {
  name: string
  occupied: number
  available: number
  overbooked: number
}

export interface FutureDailyStatus {
  date: string
  dayOfWeek: string
  rooms: FutureRoomTypeStatus[]
  totalOccupied: number
  totalAvailable: number
}

const defaultConfig: HotelConfig = {
  name: '松间·山野民宿',
  type: '精品民宿',
  city: '浙江·莫干山',
  totalRooms: 12,
  tags: '竹林景观、私汤温泉、无边泳池、有机早餐',
  targetAudience: '面向长三角城市中产，主打情侣度假与亲子出行，提供轻奢逃离体验。',
  nearby: '距莫干山景区5分钟·竹林徒步·茶园采摘',
}

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7)
}

const defaultRoomTypes: RoomType[] = [
  { id: generateId(), name: '竹语大床房', basePrice: 888, count: 4 },
  { id: generateId(), name: '山景套房', basePrice: 1388, count: 5 },
  { id: generateId(), name: '亲子家庭房', basePrice: 1688, count: 3 },
]

const defaultRoomStatuses: RoomStatus[] = [
  { roomTypeId: defaultRoomTypes[0].id, rooms: [
    { number: '101', status: 'sold' },
    { number: '102', status: 'sold' },
    { number: '103', status: 'free' },
    { number: '104', status: 'dirty' },
  ]},
  { roomTypeId: defaultRoomTypes[1].id, rooms: [
    { number: '201', status: 'sold' },
    { number: '202', status: 'sold' },
    { number: '203', status: 'sold' },
    { number: '204', status: 'sold' },
    { number: '205', status: 'free' },
  ]},
  { roomTypeId: defaultRoomTypes[2].id, rooms: [
    { number: '301', status: 'sold' },
    { number: '302', status: 'free' },
    { number: '303', status: 'repair' },
  ]},
]

const defaultFutureStatus: FutureDailyStatus[] = [
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

export const useHotelStore = defineStore('hotel', () => {
  const config = ref<HotelConfig>(loadConfig())
  const roomTypes = ref<RoomType[]>(loadRoomTypes())
  const roomStatuses = ref<RoomStatus[]>(loadRoomStatuses())
  const futureStatus = ref<FutureDailyStatus[]>(loadFutureStatus())

  const totalSold = computed(() => {
    let sold = 0
    for (const rs of roomStatuses.value) {
      sold += rs.rooms.filter(r => r.status === 'sold').length
    }
    return sold
  })

  const totalRooms = computed(() => {
    return roomTypes.value.reduce((sum, rt) => sum + rt.count, 0)
  })

  const occupancyRate = computed(() => {
    if (totalRooms.value === 0) return 0
    return Math.round((totalSold.value / totalRooms.value) * 100)
  })

  const totalRevenue = computed(() => {
    let rev = 0
    for (const rs of roomStatuses.value) {
      const rt = roomTypes.value.find(r => r.id === rs.roomTypeId)
      if (rt) {
        rev += rt.basePrice * rs.rooms.filter(r => r.status === 'sold').length
      }
    }
    return rev
  })

  const revpar = computed(() => {
    if (totalRooms.value === 0) return 0
    return Math.round(totalRevenue.value / totalRooms.value)
  })

  async function saveConfig(cfg: HotelConfig) {
    config.value = cfg
    persist('hotel_config', cfg)
    try { await saveConfigApi(cfg) } catch { /* API 失败时本地已保存 */ }
  }

  async function saveRoomTypes(rts: RoomType[]) {
    roomTypes.value = rts
    persist('hotel_roomTypes', rts)
    try { await saveRoomTypesApi(rts) } catch { /* API 失败时本地已保存 */ }
  }

  function addRoomType(rt: Omit<RoomType, 'id'>) {
    const newRt: RoomType = { ...rt, id: generateId() }
    roomTypes.value.push(newRt)
    persist('hotel_roomTypes', roomTypes.value)
    saveRoomTypes(roomTypes.value)
  }

  function removeRoomType(id: string) {
    roomTypes.value = roomTypes.value.filter(r => r.id !== id)
    persist('hotel_roomTypes', roomTypes.value)
    saveRoomTypes(roomTypes.value)
  }

  function saveFutureStatus(data: FutureDailyStatus[]) {
    futureStatus.value = data
    persist('hotel_futureStatus', data)
  }

  /** 从后端加载酒店配置 */
  async function loadFromApi() {
    try {
      const { data: res } = await api.get('/api/hotel/dashboard')
      const dash = res.data || res

      // 更新酒店配置
      if (dash.config) {
        config.value = dash.config
        persist('hotel_config', dash.config)
      }
      // 更新房型
      if (dash.roomTypeStats) {
        roomTypes.value = dash.roomTypeStats.map((s: any) => ({
          id: String(s.id),
          name: s.name,
          basePrice: s.basePrice,
          count: s.total,
        }))
        persist('hotel_roomTypes', roomTypes.value)
      }
      // 更新房态
      if (dash.roomTypeStats) {
        const statuses: RoomStatus[] = []
        for (const s of dash.roomTypeStats) {
          const rooms: RoomItem[] = []
          for (let i = 0; i < (s.sold || 0); i++) rooms.push({ number: '', status: 'sold' })
          for (let i = 0; i < (s.free || 0); i++) rooms.push({ number: '', status: 'free' })
          for (let i = 0; i < (s.dirty || 0); i++) rooms.push({ number: '', status: 'dirty' })
          for (let i = 0; i < (s.repair || 0); i++) rooms.push({ number: '', status: 'repair' })
          statuses.push({ roomTypeId: String(s.id), rooms })
        }
        roomStatuses.value = statuses
        persist('hotel_roomStatuses', statuses)
      }
      // 更新未来7天趋势
      if (dash.futureStatus) {
        futureStatus.value = dash.futureStatus
        persist('hotel_futureStatus', dash.futureStatus)
      }
    } catch {
      // API 不可用时静默回退到 localStorage
    }
  }

  function persist(key: string, data: unknown) {
    try {
      localStorage.setItem(key, JSON.stringify(data))
    } catch {}
  }

  function loadConfig(): HotelConfig {
    try {
      const d = localStorage.getItem('hotel_config')
      return d ? JSON.parse(d) : defaultConfig
    } catch { return defaultConfig }
  }

  function loadRoomTypes(): RoomType[] {
    try {
      const d = localStorage.getItem('hotel_roomTypes')
      return d ? JSON.parse(d) : defaultRoomTypes
    } catch { return defaultRoomTypes }
  }

  function loadRoomStatuses(): RoomStatus[] {
    try {
      const d = localStorage.getItem('hotel_roomStatuses')
      return d ? JSON.parse(d) : defaultRoomStatuses
    } catch { return defaultRoomStatuses }
  }

  function loadFutureStatus(): FutureDailyStatus[] {
    try {
      const d = localStorage.getItem('hotel_futureStatus')
      return d ? JSON.parse(d) : defaultFutureStatus
    } catch { return defaultFutureStatus }
  }

  return {
    config, roomTypes, roomStatuses, futureStatus,
    totalSold, totalRooms, occupancyRate, totalRevenue, revpar,
    saveConfig, saveRoomTypes, addRoomType, removeRoomType, saveFutureStatus,
    loadFromApi,
  }
})
