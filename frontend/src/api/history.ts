import api from './index'

export function getGenerationHistory(moduleKey?: string, limit = 100) {
  const params = new URLSearchParams({ limit: String(limit) })
  if (moduleKey) params.set('moduleKey', moduleKey)
  return api.get(`/api/ai/history?${params.toString()}`)
}

export function getGenerationHistoryDetail(id: number, moduleKey?: string) {
  const params = new URLSearchParams()
  if (moduleKey) params.set('moduleKey', moduleKey)
  const query = params.toString()
  return api.get(`/api/ai/history/${id}${query ? `?${query}` : ''}`)
}

export function getLatestPreset(moduleKey: string) {
  return api.get(`/api/ai/history/latest-preset?moduleKey=${encodeURIComponent(moduleKey)}`)
}

export function deleteGenerationHistory(ids: number[]) {
  return api.delete('/api/ai/history', { data: { ids } })
}
