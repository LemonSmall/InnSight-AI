import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { generateContent, getTaskResult } from '@/api/content'
import { getGenerationHistory } from '@/api/history'
import { moduleLabel, moduleDetailRoute, type HistoryItem } from '@/utils/generationHistory'
import router from '@/router'

type JobStatus = 'pending' | 'processing' | 'success' | 'done' | 'failed'

export interface AiJob {
  id: string
  taskId?: number
  generationId?: number
  moduleKey: string
  title: string
  status: JobStatus
  createdAt: number
  completedAt?: number
  errorMsg?: string
}

const STORAGE_KEY = 'sushijia_ai_jobs'
const DONE_SEEN_KEY = 'sushijia_ai_jobs_done_seen'
const RUNNING = new Set(['pending', 'processing', 'running'])
const FINISHED = new Set(['success', 'done', 'failed'])

export const useAiJobsStore = defineStore('aiJobs', () => {
  const jobs = ref<AiJob[]>(loadJobs())
  const showPanel = ref(false)
  const toastJob = ref<AiJob | null>(null)
  const polling = ref(false)
  let pollTimer: ReturnType<typeof setInterval> | null = null

  const runningJobs = computed(() => jobs.value.filter(job => RUNNING.has(job.status)))
  const finishedJobs = computed(() => jobs.value.filter(job => FINISHED.has(job.status)))
  const runningCount = computed(() => runningJobs.value.length)

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(jobs.value.slice(0, 80)))
  }

  async function submit(moduleKey: string, params: Record<string, any>, title?: string) {
    const localJob: AiJob = {
      id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      moduleKey,
      title: title || `${moduleLabel(moduleKey)}生成`,
      status: 'pending',
      createdAt: Date.now(),
    }
    upsertJob(localJob)
    const { data } = await generateContent(moduleKey, params)
    const payload = data?.data || data || {}
    localJob.taskId = Number(payload.taskId || 0) || undefined
    localJob.status = 'processing'
    upsertJob(localJob)
    startPolling()
    return localJob
  }

  async function refresh() {
    await Promise.allSettled([refreshTasks(), refreshHistory()])
    persist()
  }

  async function refreshTasks() {
    const taskJobs = jobs.value.filter(job => job.taskId && RUNNING.has(job.status))
    await Promise.all(taskJobs.map(async job => {
      const { data } = await getTaskResult(Number(job.taskId))
      const payload = data?.data || data || {}
      const status = normalizeStatus(payload.status || job.status)
      upsertJob({
        ...job,
        status,
        generationId: Number(payload.generationId || job.generationId || 0) || job.generationId,
        errorMsg: payload.errorMsg || job.errorMsg,
        completedAt: FINISHED.has(status) ? Date.now() : job.completedAt,
      })
    }))
  }

  async function refreshHistory() {
    const { data } = await getGenerationHistory(undefined, 60)
    const list: HistoryItem[] = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : []
    const seenDone = loadSeenDone()
    for (const item of list) {
      const status = normalizeStatus(item.status)
      const job: AiJob = {
        id: historyJobId(item),
        generationId: item.id,
        moduleKey: item.moduleKey,
        title: item.title || `${moduleLabel(item.moduleKey)}生成`,
        status,
        createdAt: item.createdAt ? new Date(item.createdAt).getTime() : Date.now(),
        completedAt: item.completedAt ? new Date(item.completedAt).getTime() : undefined,
        errorMsg: item.errorMsg,
      }
      const before = jobs.value.find(current => current.id === job.id)
      upsertJob(job)
      if (!before && FINISHED.has(status)) continue
      if (before && RUNNING.has(before.status) && FINISHED.has(status) && status !== 'failed' && !seenDone.has(job.id)) {
        toastJob.value = job
        seenDone.add(job.id)
        saveSeenDone(seenDone)
        setTimeout(() => {
          if (toastJob.value?.id === job.id) toastJob.value = null
        }, 5000)
      }
    }
    jobs.value = jobs.value
      .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
      .slice(0, 80)
  }

  function upsertJob(job: AiJob) {
    const index = job.generationId
      ? jobs.value.findIndex(item => item.generationId === job.generationId || item.id === job.id)
      : jobs.value.findIndex(item => item.id === job.id)
    let keptIndex = index
    if (index >= 0) jobs.value[index] = { ...jobs.value[index], ...job }
    else {
      jobs.value.unshift(job)
      keptIndex = 0
    }
    if (job.generationId) {
      jobs.value = jobs.value.filter((item, itemIndex) => (
        itemIndex === keptIndex || item.generationId !== job.generationId
      ))
    }
    persist()
  }

  function startPolling() {
    if (pollTimer) return
    polling.value = true
    refresh().catch(() => {})
    pollTimer = setInterval(() => refresh().catch(() => {}), 3500)
  }

  function stopPolling() {
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = null
    polling.value = false
  }

  function togglePanel() {
    showPanel.value = !showPanel.value
  }

  function openJob(job: AiJob) {
    if (!job.generationId) return
    router.push(moduleDetailRoute({
      id: job.generationId,
      moduleKey: job.moduleKey,
      title: job.title,
      status: job.status,
    } as HistoryItem))
    showPanel.value = false
    toastJob.value = null
  }

  function removeJob(jobId: string) {
    jobs.value = jobs.value.filter(job => job.id !== jobId)
    persist()
  }

  return {
    jobs,
    showPanel,
    toastJob,
    polling,
    runningJobs,
    finishedJobs,
    runningCount,
    submit,
    refresh,
    startPolling,
    stopPolling,
    togglePanel,
    openJob,
    removeJob,
  }
})

function normalizeStatus(value: any): JobStatus {
  const status = String(value || '').toLowerCase()
  if (status === 'done') return 'done'
  if (status === 'success') return 'success'
  if (status === 'failed' || status === 'error') return 'failed'
  if (status === 'pending') return 'pending'
  return 'processing'
}

function historyJobId(item: HistoryItem) {
  return `history-${item.moduleKey}-${item.id}`
}

function loadJobs(): AiJob[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function loadSeenDone() {
  try {
    return new Set<string>(JSON.parse(localStorage.getItem(DONE_SEEN_KEY) || '[]'))
  } catch {
    return new Set<string>()
  }
}

function saveSeenDone(values: Set<string>) {
  localStorage.setItem(DONE_SEEN_KEY, JSON.stringify([...values].slice(-200)))
}
