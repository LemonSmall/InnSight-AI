import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'

export interface PlanKpi {
  value: string
  label: string
  color: string
}

export interface PlanTask {
  channel: string
  channelColor: string
  content: string
}

export interface PlanPhase {
  id: string
  emoji: string
  dotBg: string
  title: string
  dateRange: string
  badgeLabel: string
  badgeClass: 'green' | 'amber' | 'rose' | 'blue' | 'purple'
  tasks: PlanTask[]
}

export interface PlanChannel {
  icon: string
  iconBg: string
  iconColor: string
  name: string
  sub: string
  items: string[]
  tags: { label: string; badgeClass: string }[]
}

export interface PlanPricing {
  stage: string
  stageBadge: 'amber' | 'rose' | 'blue' | 'green'
  prices: string[]
  logic: string
}

export interface PlanActivity {
  icon: string
  iconColor: string
  title: string
  desc: string
  goal: string
  tag: string
  badgeClass: string
}

export interface PlanCopy {
  label: string
  content: string
}

export interface PlanAlert {
  html: string
  bgClass: 'bamboo' | 'amber' | 'purple'
}

export interface MarketingPlan {
  id: string
  name: string
  festival: string
  status: 'draft' | 'active' | 'completed'
  hotelName: string
  period: string
  target: string
  tags: string[]
  kpis: PlanKpi[]
  phases: PlanPhase[]
  channels: PlanChannel[]
  pricings: PlanPricing[]
  activities: PlanActivity[]
  copyExamples: PlanCopy[]
  alertNote: string
  alerts: PlanAlert[]
  createdAt: string
  updatedAt: string
}

interface MarketingPlanRow {
  id: number | string
  name?: string
  festival?: string
  status?: string
  hotelName?: string
  period?: string
  target?: string
  tags?: string | string[]
  kpis?: string | PlanKpi[]
  phases?: string | PlanPhase[]
  channels?: string | PlanChannel[]
  pricings?: string | PlanPricing[]
  activities?: string | PlanActivity[]
  alertNote?: string
  alerts?: string | PlanAlert[]
  createdAt?: string
  updatedAt?: string
}

function unwrap<T = any>(response: any): T {
  return response?.data?.data ?? response?.data ?? response
}

function parseArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function normalizeStatus(status?: string): MarketingPlan['status'] {
  return status === 'active' || status === 'completed' ? status : 'draft'
}

function normalizeDate(value?: string): string {
  return value ? value.slice(0, 10) : ''
}

function normalizePlan(row: MarketingPlanRow): MarketingPlan {
  return {
    id: String(row.id),
    name: row.name || '未命名方案',
    festival: row.festival || '',
    status: normalizeStatus(row.status),
    hotelName: row.hotelName || '',
    period: row.period || '',
    target: row.target || '',
    tags: parseArray<string>(row.tags),
    kpis: parseArray<PlanKpi>(row.kpis),
    phases: parseArray<PlanPhase>(row.phases),
    channels: parseArray<PlanChannel>(row.channels),
    pricings: parseArray<PlanPricing>(row.pricings),
    activities: parseArray<PlanActivity>(row.activities),
    copyExamples: [],
    alertNote: row.alertNote || '',
    alerts: parseArray<PlanAlert>(row.alerts),
    createdAt: normalizeDate(row.createdAt),
    updatedAt: normalizeDate(row.updatedAt),
  }
}

function serializeArray(value: unknown): string {
  return JSON.stringify(Array.isArray(value) ? value : [])
}

function toPayload(plan: Partial<MarketingPlan>) {
  const payload: Record<string, unknown> = { ...plan }
  if ('tags' in payload) payload.tags = serializeArray(payload.tags)
  if ('kpis' in payload) payload.kpis = serializeArray(payload.kpis)
  if ('phases' in payload) payload.phases = serializeArray(payload.phases)
  if ('channels' in payload) payload.channels = serializeArray(payload.channels)
  if ('pricings' in payload) payload.pricings = serializeArray(payload.pricings)
  if ('activities' in payload) payload.activities = serializeArray(payload.activities)
  if ('alerts' in payload) payload.alerts = serializeArray(payload.alerts)
  delete payload.id
  delete payload.copyExamples
  delete payload.createdAt
  delete payload.updatedAt
  return payload
}

export const usePlanStore = defineStore('plan', () => {
  const plans = ref<MarketingPlan[]>([])
  const loading = ref(false)
  const error = ref('')

  const activePlans = computed(() => plans.value.filter(p => p.status === 'active'))
  const completedPlans = computed(() => plans.value.filter(p => p.status === 'completed'))
  const draftPlans = computed(() => plans.value.filter(p => p.status === 'draft'))

  async function loadFromApi() {
    loading.value = true
    error.value = ''
    try {
      const response = await api.get('/api/hotel/plans')
      const list = unwrap<MarketingPlanRow[]>(response)
      plans.value = Array.isArray(list) ? list.map(normalizePlan) : []
    } catch {
      plans.value = []
      error.value = '营销方案加载失败，请稍后重试'
      throw new Error(error.value)
    } finally {
      loading.value = false
    }
  }

  function getById(id: string): MarketingPlan | undefined {
    return plans.value.find(p => p.id === id)
  }

  async function create(name: string, festival: string): Promise<MarketingPlan> {
    error.value = ''
    try {
      const response = await api.post('/api/hotel/plans', toPayload({
        name,
        festival,
        status: 'draft',
        hotelName: '',
        tags: [],
        kpis: [],
        phases: [],
        channels: [],
        pricings: [],
        activities: [],
        alerts: [],
      }))
      const plan = normalizePlan(unwrap<MarketingPlanRow>(response))
      plans.value.unshift(plan)
      return plan
    } catch {
      error.value = '营销方案创建失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function update(id: string, patch: Partial<MarketingPlan>) {
    error.value = ''
    try {
      await api.put(`/api/hotel/plans/${id}`, toPayload(patch))
      const idx = plans.value.findIndex(p => p.id === id)
      if (idx >= 0) {
        plans.value[idx] = { ...plans.value[idx], ...patch, updatedAt: new Date().toISOString().slice(0, 10) }
      }
    } catch {
      error.value = '营销方案保存失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function remove(id: string) {
    error.value = ''
    try {
      await api.delete(`/api/hotel/plans/${id}`)
      plans.value = plans.value.filter(p => p.id !== id)
    } catch {
      error.value = '营销方案删除失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function duplicate(id: string): Promise<MarketingPlan | undefined> {
    const src = plans.value.find(p => p.id === id)
    if (!src) return
    error.value = ''
    try {
      const response = await api.post('/api/hotel/plans', toPayload({
        ...src,
        name: `${src.name}（副本）`,
        status: 'draft',
      }))
      const plan = normalizePlan(unwrap<MarketingPlanRow>(response))
      plans.value.unshift(plan)
      return plan
    } catch {
      error.value = '营销方案复制失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  return {
    plans,
    activePlans,
    completedPlans,
    draftPlans,
    loading,
    error,
    loadFromApi,
    getById,
    create,
    update,
    remove,
    duplicate,
  }
})
