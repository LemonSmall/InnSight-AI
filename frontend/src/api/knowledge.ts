import api from './index'

export function submitKnowledgeText(content: string) {
  return api.post('/api/knowledge/text', { content })
}

export function uploadKnowledgeFile(file: File) {
  const form = new FormData()
  form.append('file', file)
  return api.post('/api/knowledge/files', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getKnowledgeFiles(limit = 50) {
  return api.get(`/api/knowledge/files?limit=${limit}`)
}

export function confirmKnowledgeJob(jobId: number) {
  return api.post(`/api/knowledge/jobs/${jobId}/confirm`)
}

export function cancelKnowledgeJob(jobId: number) {
  return api.post(`/api/knowledge/jobs/${jobId}/cancel`)
}

export function updateKnowledgeItem(itemId: number, data: Record<string, any>) {
  return api.put(`/api/knowledge/items/${itemId}`, data)
}

export function deleteKnowledgeItem(itemId: number) {
  return api.delete(`/api/knowledge/items/${itemId}`)
}

export function getKnowledgeItems(category?: string, limit = 100) {
  const params = new URLSearchParams({ limit: String(limit) })
  if (category) params.set('category', category)
  return api.get(`/api/knowledge/items?${params.toString()}`)
}

export function getPendingKnowledgeJobs() {
  return api.get('/api/knowledge/jobs/pending')
}
