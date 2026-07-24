<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/api'
import { Loader2, ReceiptText, RefreshCw, Search } from 'lucide-vue-next'

type Ledger = {
  id: number
  tenantId?: number
  type?: string
  amount?: number
  balanceAfter?: number
  moduleKey?: string
  moduleName?: string
  detail?: string
  status?: string
  createdAt?: string
}

const records = ref<Ledger[]>([])
const loading = ref(true)
const keyword = ref('')
const toast = ref('')

const filteredRecords = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return records.value
  return records.value.filter((record) =>
    [record.tenantId, record.moduleName, record.moduleKey, record.detail, record.status]
      .some((item) => String(item || '').toLowerCase().includes(kw))
  )
})

const rechargeTotal = computed(() =>
  records.value.filter((record) => record.type === 'recharge').reduce((sum, record) => sum + Number(record.amount || 0), 0)
)
const consumeTotal = computed(() =>
  records.value.filter((record) => record.type === 'consume').reduce((sum, record) => sum + Math.abs(Number(record.amount || 0)), 0)
)
const failedCount = computed(() => records.value.filter((record) => record.status && record.status !== 'success').length)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/api/admin/ledger')
    records.value = data.data || []
  } catch {
    flash('流水加载失败')
  } finally {
    loading.value = false
  }
}

function typeLabel(type?: string) {
  if (type === 'recharge') return '充值'
  if (type === 'consume') return '消耗'
  return type || '-'
}

function amountClass(type?: string) {
  if (type === 'recharge') return 'text-emerald-300'
  if (type === 'consume') return 'text-red-300'
  return 'text-gray-300'
}

function statusLabel(status?: string) {
  if (status === 'success') return '成功'
  if (status === 'failed') return '失败'
  if (status === 'melted') return '熔断'
  return status || '-'
}

function statusClass(status?: string) {
  if (status === 'success') return 'bg-emerald-500/10 text-emerald-300'
  if (status === 'failed') return 'bg-red-500/10 text-red-300'
  if (status === 'melted') return 'bg-amber-500/10 text-amber-300'
  return 'bg-gray-700 text-gray-300'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function flash(msg: string) {
  toast.value = msg
  setTimeout(() => (toast.value = ''), 2000)
}
</script>

<template>
  <div class="space-y-5">
    <div v-if="toast" class="fixed right-5 top-5 z-50 rounded-md bg-indigo-600 px-4 py-2 text-sm text-white shadow-lg">
      {{ toast }}
    </div>

    <section class="rounded-lg border border-gray-800 bg-gray-900 p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2 text-base font-semibold text-gray-100">
            <ReceiptText class="h-5 w-5 text-indigo-400" />
            消耗流水
          </div>
          <p class="mt-1 text-sm text-gray-500">记录充值、AI 调用扣费、失败与熔断等算力变动。</p>
        </div>
        <button class="btn-secondary" @click="load">
          <RefreshCw class="h-4 w-4" />
          刷新
        </button>
      </div>

      <div class="mt-5 grid gap-3 md:grid-cols-3">
        <div class="metric">
          <span>累计充值</span>
          <strong class="text-emerald-300">+{{ rechargeTotal.toLocaleString() }}</strong>
        </div>
        <div class="metric">
          <span>累计消耗</span>
          <strong class="text-red-300">-{{ consumeTotal.toLocaleString() }}</strong>
        </div>
        <div class="metric">
          <span>异常记录</span>
          <strong :class="failedCount > 0 ? 'text-amber-300' : ''">{{ failedCount }}</strong>
        </div>
      </div>
    </section>

    <section class="rounded-lg border border-gray-800 bg-gray-900">
      <div class="border-b border-gray-800 p-4">
        <div class="relative max-w-sm">
          <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500" />
          <input v-model="keyword" class="input pl-9" placeholder="搜索租户、模块、状态、备注" />
        </div>
      </div>

      <div v-if="loading" class="flex justify-center py-16">
        <Loader2 class="h-6 w-6 animate-spin text-gray-500" />
      </div>

      <div v-else class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="text-xs text-gray-500">
            <tr class="border-b border-gray-800">
              <th class="px-4 py-3 font-medium">时间</th>
              <th class="px-4 py-3 font-medium">租户</th>
              <th class="px-4 py-3 font-medium">类型</th>
              <th class="px-4 py-3 font-medium">模块</th>
              <th class="px-4 py-3 font-medium">算力变动</th>
              <th class="px-4 py-3 font-medium">变动后余额</th>
              <th class="px-4 py-3 font-medium">状态</th>
              <th class="px-4 py-3 font-medium">备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in filteredRecords" :key="record.id" class="border-b border-gray-800/70 hover:bg-gray-800/40">
              <td class="px-4 py-3 text-gray-400">{{ formatTime(record.createdAt) }}</td>
              <td class="px-4 py-3 font-mono text-gray-300">#{{ record.tenantId || '-' }}</td>
              <td class="px-4 py-3 text-gray-300">{{ typeLabel(record.type) }}</td>
              <td class="px-4 py-3">
                <div class="text-gray-100">{{ record.moduleName || '-' }}</div>
                <div class="text-xs text-gray-500">{{ record.moduleKey || '-' }}</div>
              </td>
              <td class="px-4 py-3 font-mono" :class="amountClass(record.type)">
                {{ record.type === 'recharge' ? '+' : '' }}{{ record.amount || 0 }}
              </td>
              <td class="px-4 py-3 font-mono text-gray-300">{{ Number(record.balanceAfter || 0).toLocaleString() }}</td>
              <td class="px-4 py-3">
                <span :class="['rounded-full px-2 py-1 text-xs', statusClass(record.status)]">{{ statusLabel(record.status) }}</span>
              </td>
              <td class="max-w-md px-4 py-3 text-gray-500">{{ record.detail || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="filteredRecords.length === 0" class="py-12 text-center text-sm text-gray-500">暂无流水记录</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  border-radius: 0.5rem;
  border: 1px solid #374151;
  padding: 0.625rem 0.875rem;
  color: #d1d5db;
  font-size: 0.875rem;
}
.btn-secondary:hover {
  background: #1f2937;
}
.input {
  width: 100%;
  border-radius: 0.5rem;
  border: 1px solid #374151;
  background: #111827;
  padding: 0.625rem 0.75rem;
  color: #f3f4f6;
  outline: none;
}
.input:focus {
  border-color: #6366f1;
}
.metric {
  border-radius: 0.5rem;
  border: 1px solid #1f2937;
  background: #111827;
  padding: 1rem;
}
.metric span {
  display: block;
  font-size: 0.75rem;
  color: #6b7280;
}
.metric strong {
  margin-top: 0.375rem;
  display: block;
  font-size: 1.5rem;
  color: #f3f4f6;
}
</style>
