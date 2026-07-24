import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCreditBalance, getCreditLedger, getSubscription } from '@/api/hotel'

export interface CreditRecord {
  id: string
  date: string
  time: string
  type: 'consume' | 'recharge'
  amount: number
  balance: number
  module: string
  detail: string
  status: string
}

interface CreditLedgerRow {
  id: number | string
  type: 'consume' | 'recharge'
  amount: number
  balanceAfter: number
  moduleKey?: string
  moduleName?: string
  detail?: string
  status?: string
  createdAt?: string
}

interface TenantPlanRow {
  code?: string
  name?: string
  enabledModules?: string
  monthlyCredits?: number
  maxBranches?: number
}

interface SubscriptionOverview {
  active?: boolean
  enabledModules?: string
  plan?: TenantPlanRow | null
  subscription?: Record<string, any> | null
}

function unwrap<T = any>(response: any): T {
  return response?.data?.data ?? response?.data ?? response
}

function formatDate(value?: string): string {
  if (!value) return ''
  return value.length >= 10 ? value.slice(0, 10) : value
}

function formatTime(value?: string): string {
  if (!value || value.length < 16) return ''
  return value.slice(11, 16)
}

function mapLedger(row: CreditLedgerRow): CreditRecord {
  return {
    id: String(row.id),
    date: formatDate(row.createdAt),
    time: formatTime(row.createdAt),
    type: row.type,
    amount: Number(row.amount || 0),
    balance: Number(row.balanceAfter || 0),
    module: row.moduleName || row.moduleKey || (row.type === 'recharge' ? '充值' : 'AI调用'),
    detail: row.detail || '',
    status: row.status || 'success',
  }
}

export const useCreditsStore = defineStore('credits', () => {
  const records = ref<CreditRecord[]>([])
  const balance = ref(0)
  const todayConsume = ref(0)
  const loading = ref(false)
  const error = ref('')
  const subscription = ref<SubscriptionOverview | null>(null)

  const currentBalance = computed(() => balance.value)
  const consumeRecords = computed(() => records.value.filter(r => r.type === 'consume'))
  const rechargeRecords = computed(() => records.value.filter(r => r.type === 'recharge'))
  const currentPlan = computed(() => subscription.value?.plan || null)
  const enabledModules = computed(() => {
    const raw = subscription.value?.enabledModules || subscription.value?.plan?.enabledModules || ''
    return raw.split(',').map(item => item.trim()).filter(Boolean)
  })

  async function loadFromApi(type?: 'consume' | 'recharge') {
    loading.value = true
    error.value = ''
    let failed = false
    try {
      const balanceResponse = await getCreditBalance()
      const balanceData = unwrap<{ balance?: number; todayConsume?: number }>(balanceResponse)
      balance.value = Number(balanceData.balance || 0)
      todayConsume.value = Number(balanceData.todayConsume || 0)
    } catch {
      failed = true
      balance.value = 0
      todayConsume.value = 0
    }

    try {
      const ledgerResponse = await getCreditLedger(100, type)
      const ledgerData = unwrap<CreditLedgerRow[]>(ledgerResponse)
      records.value = Array.isArray(ledgerData) ? ledgerData.map(mapLedger) : []
    } catch {
      failed = true
      records.value = []
    }

    try {
      const subscriptionResponse = await getSubscription()
      subscription.value = unwrap<SubscriptionOverview>(subscriptionResponse)
    } catch {
      failed = true
      subscription.value = null
    }

    if (failed) {
      error.value = '算力数据加载失败，请稍后重试'
      loading.value = false
      throw new Error(error.value)
    }

    loading.value = false
  }

  async function consume() {
    await loadFromApi()
  }

  async function recharge() {
    await loadFromApi()
  }

  function canUseModule(moduleKey: string) {
    return Boolean(subscription.value?.active && enabledModules.value.includes(moduleKey))
  }

  return {
    records,
    loading,
    error,
    subscription,
    currentPlan,
    enabledModules,
    currentBalance,
    consumeRecords,
    rechargeRecords,
    todayConsume,
    loadFromApi,
    consume,
    recharge,
    canUseModule,
  }
})
