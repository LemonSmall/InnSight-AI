import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'

export interface CreditRecord {
  id: string
  date: string
  time: string
  type: 'consume' | 'recharge'
  amount: number
  balance: number
  module: string
  detail: string
}

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7)
}

const seedRecords: CreditRecord[] = [
  { id: generateId(), date: '06-11', time: '14:22', type: 'consume', amount: -8, balance: 1228, module: '朋友圈文案', detail: '生成三档文案（早/中/晚）' },
  { id: generateId(), date: '06-11', time: '10:08', type: 'consume', amount: -10, balance: 1236, module: '小红书营销', detail: '生成3个选题+完整图文' },
  { id: generateId(), date: '06-11', time: '09:15', type: 'recharge', amount: 500, balance: 1246, module: '充值', detail: '月度套餐 · 500算力' },
  { id: generateId(), date: '06-10', time: '20:45', type: 'consume', amount: -30, balance: 746, module: '营销海报', detail: '生成端午特惠海报' },
  { id: generateId(), date: '06-10', time: '16:30', type: 'consume', amount: -12, balance: 776, module: '抖音口播', detail: '生成今日口播文案' },
  { id: generateId(), date: '06-10', time: '11:20', type: 'consume', amount: -8, balance: 788, module: '朋友圈文案', detail: '生成三档文案' },
  { id: generateId(), date: '06-10', time: '08:55', type: 'recharge', amount: 200, balance: 796, module: '充值', detail: '周套餐 · 200算力' },
  { id: generateId(), date: '06-09', time: '15:12', type: 'consume', amount: -15, balance: 596, module: '公众号推文', detail: '生成端午推文' },
  { id: generateId(), date: '06-09', time: '10:40', type: 'consume', amount: -20, balance: 611, module: 'AI修图', detail: '单张图片美化' },
  { id: generateId(), date: '06-08', time: '18:05', type: 'consume', amount: -10, balance: 631, module: '小红书营销', detail: '生成3个选题+图文' },
  { id: generateId(), date: '06-08', time: '09:30', type: 'recharge', amount: 500, balance: 641, module: '充值', detail: '月度套餐 · 500算力' },
  { id: generateId(), date: '06-07', time: '14:18', type: 'consume', amount: -8, balance: 141, module: '朋友圈文案', detail: '生成三档文案' },
  { id: generateId(), date: '06-07', time: '11:50', type: 'consume', amount: -30, balance: 149, module: '营销海报', detail: '生成促销海报' },
  { id: generateId(), date: '06-06', time: '20:15', type: 'consume', amount: -12, balance: 179, module: '抖音口播', detail: '生成周末口播文案' },
  { id: generateId(), date: '06-06', time: '09:00', type: 'recharge', amount: 200, balance: 191, module: '充值', detail: '周套餐 · 200算力' },
  { id: generateId(), date: '06-05', time: '16:40', type: 'consume', amount: -10, balance: 401, module: '小红书营销', detail: '生成选题+图文' },
  { id: generateId(), date: '06-05', time: '08:30', type: 'consume', amount: -8, balance: 411, module: '朋友圈文案', detail: '生成三档文案' },
  { id: generateId(), date: '06-04', time: '17:55', type: 'recharge', amount: 500, balance: 419, module: '充值', detail: '首充赠送 · 500算力' },
]

export const useCreditsStore = defineStore('credits', () => {
  const records = ref<CreditRecord[]>(loadRecords())

  const currentBalance = computed(() => {
    if (records.value.length === 0) return 0
    return records.value[0].balance
  })

  const consumeRecords = computed(() => records.value.filter(r => r.type === 'consume'))
  const rechargeRecords = computed(() => records.value.filter(r => r.type === 'recharge'))

  const todayConsume = computed(() => {
    const today = new Date()
    const md = `${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    return consumeRecords.value
      .filter(r => r.date === md)
      .reduce((s, r) => s + Math.abs(r.amount), 0)
  })

  function consume(amount: number, module: string, detail: string) {
    // 后端已扣减，前端只刷新
    loadFromApi()
  }

  function recharge(amount: number, detail: string) {
    loadFromApi()
  }

  /** 从后端加载算力数据 */
  async function loadFromApi() {
    try {
      const { data: res } = await api.get('/api/hotel/credits/balance')
      const d = res.data || res
      if (d.balance !== undefined) {
        // 更新当前余额到第一条记录的balance
        // 简化处理：直接更新种子数据的第一条
        const today = todayStr()
        const time = nowTime()
        if (records.value.length > 0) {
          records.value[0].balance = d.balance
        }
      }
      // 同时拉取流水
      try {
        const { data: ledger } = await api.get('/api/hotel/credits/ledger?limit=50')
        const list = ledger.data || ledger
        if (Array.isArray(list) && list.length > 0) {
          // 将后端流水映射为前端格式
          records.value = list.map((r: any) => ({
            id: String(r.id),
            date: r.createdAt ? r.createdAt.slice(5, 10) : todayStr(),
            time: r.createdAt ? r.createdAt.slice(11, 16) : nowTime(),
            type: r.type as 'consume' | 'recharge',
            amount: r.amount,
            balance: r.balanceAfter,
            module: r.moduleName || r.moduleKey || r.module || '',
            detail: r.detail || '',
          }))
        }
      } catch { /* 静默回退 */ }
    } catch { /* 静默回退到本地数据 */ }
  }

  function todayStr(): string {
    const d = new Date()
    return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  function nowTime(): string {
    const d = new Date()
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  function persist() {
    try { localStorage.setItem('credits_records', JSON.stringify(records.value)) } catch {}
  }

  function loadRecords(): CreditRecord[] {
    try {
      const d = localStorage.getItem('credits_records')
      return d ? JSON.parse(d) : seedRecords
    } catch { return seedRecords }
  }

  return {
    records, currentBalance, consumeRecords, rechargeRecords,
    todayConsume, consume, recharge,
  }
})
