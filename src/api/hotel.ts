import api from './index'

// ====== 酒店配置 ======
export function saveConfig(cfg: Record<string, any>) {
  return api.put('/api/hotel/config', cfg)
}

export function searchHotelPoi(params: { keyword: string; city?: string }) {
  return api.get('/api/hotel/poi/search', { params })
}

export function bindHotelPoi(data: { poiId: string }) {
  return api.post('/api/hotel/poi/bind', data)
}

export function bindHotelPoiCandidate(data: Record<string, any>) {
  return api.post('/api/hotel/poi/bind-candidate', data)
}

export function getRegionChildren(params: { keyword?: string; subdistrict?: number }) {
  return api.get('/api/hotel/region/children', { params })
}

export function getCurrentWeather() {
  return api.get('/api/hotel/weather/current')
}

export function getSurroundingRecommendation() {
  return api.post('/api/hotel/surrounding/recommendation')
}

export function getRoomTypes() {
  return api.get('/api/hotel/rooms')
}

export function saveRoomTypes(rooms: Record<string, any>[]) {
  return api.put('/api/hotel/rooms', rooms)
}

export function deleteRoomType(id: string | number) {
  return api.delete(`/api/hotel/rooms/${encodeURIComponent(String(id))}`)
}

export function getOccupancyImportRecords() {
  return api.get('/api/hotel/occupancy-imports/current')
}

export function importOccupancyRecords(data: Record<string, any>) {
  return api.post('/api/hotel/occupancy-imports/import', data)
}

// ====== 大盘 ======
export function getDashboard() {
  return api.get('/api/hotel/dashboard')
}

// ====== 智能定价 ======
// ====== 算力 ======
export function getCreditBalance() {
  return api.get('/api/hotel/credits/balance')
}

export function getCreditLedger(limit = 50, type?: string) {
  return api.get(`/api/hotel/credits/ledger?limit=${limit}${type ? '&type=' + type : ''}`)
}

export function checkCredits(moduleKey: string) {
  return api.get(`/api/hotel/credits/check?moduleKey=${encodeURIComponent(moduleKey)}`)
}

export function getSubscription() {
  return api.get('/api/hotel/subscription')
}

// ====== 好评/回评 ======
// ====== 营销方案 ======
export function getPlans() {
  return api.get('/api/hotel/plans')
}

export function createPlan(data: Record<string, any>) {
  return api.post('/api/hotel/plans', data)
}

export function updatePlan(id: string, data: Record<string, any>) {
  return api.put(`/api/hotel/plans/${id}`, data)
}

export function deletePlan(id: string) {
  return api.delete(`/api/hotel/plans/${id}`)
}
