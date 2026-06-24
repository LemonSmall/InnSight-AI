import api from './index'

// ====== 酒店配置 ======
export function saveConfig(cfg: Record<string, any>) {
  return api.put('/api/hotel/config', cfg)
}

export function getRoomTypes() {
  return api.get('/api/hotel/rooms')
}

export function saveRoomTypes(rooms: Record<string, any>[]) {
  return api.put('/api/hotel/rooms', rooms)
}

// ====== 大盘 ======
export function getDashboard() {
  return api.get('/api/hotel/dashboard')
}

// ====== 房态分析 ======
export function getRoomStatusDetail() {
  return api.get('/api/hotel/room-status')
}

// ====== 智能定价 ======
export function getPricing(params: Record<string, string>) {
  return api.post('/api/hotel/pricing/recommend', params)
}

// ====== 算力 ======
export function getCreditBalance() {
  return api.get('/api/hotel/credits/balance')
}

export function getCreditLedger(limit = 50, type?: string) {
  return api.get(`/api/hotel/credits/ledger?limit=${limit}${type ? '&type=' + type : ''}`)
}

// ====== 在住客人 ======
export function getGuests() {
  return api.get('/api/hotel/guests')
}

// ====== 好评/回评 ======
export function generateReview(params: Record<string, string>) {
  return api.post('/api/hotel/review/generate', params)
}

export function generateReply(params: Record<string, string>) {
  return api.post('/api/hotel/reply/generate', params)
}

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
