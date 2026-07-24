<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/api'
import {
  AlertTriangle,
  Activity,
  Bot,
  Coins,
  CreditCard,
  Database,
  Loader2,
  RefreshCw,
  Users,
} from 'lucide-vue-next'

type AiLog = {
  id: number
  tenantId?: number
  moduleKey?: string
  appName?: string
  status?: string
  durationMs?: number
  creditsCost?: number
  errorMessage?: string
  createdAt?: string
}

const loading = ref(true)
const tenants = ref<any[]>([])
const rules = ref<any[]>([])
const packages = ref<any[]>([])
const ledgers = ref<any[]>([])
const bindings = ref<any[]>([])
const aiLogs = ref<AiLog[]>([])
const errors = ref<string[]>([])

const today = new Date().toISOString().slice(0, 10)

const activeTenants = computed(() => tenants.value.filter(t => (t.status || 'active') === 'active').length)
const lowBalanceTenants = computed(() => tenants.value.filter(t => Number(t.balance || 0) < 500))
const totalBalance = computed(() => tenants.value.reduce((sum, item) => sum + Number(item.balance || 0), 0))
const enabledRules = computed(() => rules.value.filter(item => item.enabled !== 0 && item.enabled !== false).length)
const enabledPackages = computed(() => packages.value.filter(item => item.enabled !== 0 && item.enabled !== false).length)
const configuredAgents = computed(() => bindings.value.filter(item => item.apiKeyConfigured || item.apiKey || item.botApiKey).length)
const enabledAgents = computed(() => bindings.value.filter(item => item.enabled !== 0 && item.enabled !== false).length)
const todayLogs = computed(() => aiLogs.value.filter(item => (item.createdAt || '').slice(0, 10) === today))
const successLogs = computed(() => todayLogs.value.filter(item => item.status === 'success'))
const failedLogs = computed(() => todayLogs.value.filter(item => item.status === 'failed'))
const successRate = computed(() => todayLogs.value.length ? Math.round(successLogs.value.length / todayLogs.value.length * 100) : 0)
const avgDuration = computed(() => {
  const values = todayLogs.value.map(item => Number(item.durationMs || 0)).filter(Boolean)
  return values.length ? Math.round(values.reduce((sum, item) => sum + item, 0) / values.length) : 0
})
const todayCredits = computed(() =>
  ledgers.value
    .filter(item => item.type === 'consume' && String(item.createdAt || item.created_at || '').slice(0, 10) === today)
    .reduce((sum, item) => sum + Math.abs(Number(item.amount || 0)), 0)
)
const topFailedModules = computed(() => {
  const counter = new Map<string, number>()
  failedLogs.value.forEach(log => counter.set(log.moduleKey || 'unknown', (counter.get(log.moduleKey || 'unknown') || 0) + 1))
  return Array.from(counter.entries()).sort((a, b) => b[1] - a[1]).slice(0, 5)
})
const recentFailures = computed(() => aiLogs.value.filter(item => item.status === 'failed').slice(0, 6))

onMounted(load)

async function load() {
  loading.value = true
  errors.value = []
  await Promise.all([
    loadPart('/api/admin/tenants', tenants, '租户数据'),
    loadPart('/api/admin/billing-rules', rules, '计费规则'),
    loadPart('/api/admin/packages', packages, '充值套餐'),
    loadPart('/api/admin/ledger', ledgers, '消耗流水'),
    loadPart('/api/admin/ai-agent-bindings', bindings, '智能体绑定'),
    loadPart('/api/admin/ai-call-logs?limit=200', aiLogs, 'AI 调用日志'),
  ])
  loading.value = false
}

async function loadPart(url: string, target: typeof tenants, label: string) {
  try {
    const { data } = await api.get(url)
    target.value = data.data || []
  } catch {
    target.value = []
    errors.value.push(label)
  }
}

function getTenantName(tenantId?: number) {
  const tenant = tenants.value.find(item => Number(item.id) === Number(tenantId))
  return tenant?.name || (tenantId ? `租户 #${tenantId}` : '-')
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(5, 16)
}

function numberLike(value: any) {
  return Number(value || 0).toLocaleString()
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-base font-semibold text-gray-100">平台总览</h1>
        <p class="mt-1 text-xs text-gray-500">基于真实租户、计费、流水和 AI 调用日志展示运营状态。</p>
      </div>
      <button class="btn-secondary" @click="load">
        <RefreshCw class="h-4 w-4" />
        刷新
      </button>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-24">
      <Loader2 class="h-6 w-6 animate-spin text-gray-600" />
    </div>

    <template v-else>
      <div v-if="errors.length" class="rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-200">
        以下数据加载失败：{{ errors.join('、') }}
      </div>

      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <div class="metric-card">
          <div class="metric-title"><Users class="h-4 w-4 text-indigo-400" />在营租户</div>
          <div class="metric-value">{{ activeTenants }}</div>
          <div class="metric-note">总租户 {{ tenants.length }}，低余额 {{ lowBalanceTenants.length }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-title"><Activity class="h-4 w-4 text-emerald-400" />今日调用</div>
          <div class="metric-value">{{ todayLogs.length }}</div>
          <div class="metric-note">成功 {{ successLogs.length }}，失败 {{ failedLogs.length }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-title"><Coins class="h-4 w-4 text-amber-400" />今日消耗</div>
          <div class="metric-value">{{ numberLike(todayCredits) }}</div>
          <div class="metric-note">平台总余额 {{ numberLike(totalBalance) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-title"><Bot class="h-4 w-4 text-cyan-400" />AI 成功率</div>
          <div class="metric-value" :class="successRate < 90 && todayLogs.length ? 'text-amber-300' : ''">{{ successRate }}%</div>
          <div class="metric-note">平均耗时 {{ avgDuration }}ms</div>
        </div>
      </div>

      <div class="grid gap-4 xl:grid-cols-3">
        <section class="rounded-lg border border-gray-800 bg-gray-900 xl:col-span-2">
          <div class="section-head">
            <h2>最近失败调用</h2>
            <RouterLink class="text-xs text-indigo-300 hover:text-indigo-200" to="/admin/logs">查看全部</RouterLink>
          </div>
          <div v-if="recentFailures.length === 0" class="empty">暂无失败调用</div>
          <div v-else class="divide-y divide-gray-800">
            <div v-for="log in recentFailures" :key="log.id" class="flex items-center justify-between gap-4 px-5 py-4">
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <span class="font-mono text-sm text-gray-100">{{ log.moduleKey || '-' }}</span>
                  <span class="rounded-full bg-red-500/10 px-2 py-0.5 text-xs text-red-300">失败</span>
                </div>
                <div class="mt-1 truncate text-xs text-gray-500">{{ log.errorMessage || 'AI 调用失败' }}</div>
              </div>
              <div class="text-right text-xs text-gray-500">
                <div>{{ getTenantName(log.tenantId) }}</div>
                <div class="font-mono">{{ formatTime(log.createdAt) }}</div>
              </div>
            </div>
          </div>
        </section>

        <section class="rounded-lg border border-gray-800 bg-gray-900">
          <div class="section-head">
            <h2>失败模块排行</h2>
            <AlertTriangle class="h-4 w-4 text-amber-400" />
          </div>
          <div v-if="topFailedModules.length === 0" class="empty">今日暂无失败模块</div>
          <div v-else class="space-y-3 p-5">
            <div v-for="[module, count] in topFailedModules" :key="module">
              <div class="mb-1 flex justify-between text-xs">
                <span class="font-mono text-gray-200">{{ module }}</span>
                <span class="text-red-300">{{ count }} 次</span>
              </div>
              <div class="h-2 overflow-hidden rounded-full bg-gray-800">
                <div class="h-full bg-red-400" :style="{ width: `${Math.min(100, count / Math.max(1, failedLogs.length) * 100)}%` }" />
              </div>
            </div>
          </div>
        </section>
      </div>

      <div class="grid gap-4 xl:grid-cols-3">
        <section class="rounded-lg border border-gray-800 bg-gray-900 xl:col-span-2">
          <div class="section-head">
            <h2>低余额租户</h2>
            <RouterLink class="text-xs text-indigo-300 hover:text-indigo-200" to="/admin/tenants">处理账户</RouterLink>
          </div>
          <div v-if="lowBalanceTenants.length === 0" class="empty">暂无低余额租户</div>
          <div v-else class="divide-y divide-gray-800">
            <div v-for="tenant in lowBalanceTenants.slice(0, 6)" :key="tenant.id" class="flex items-center justify-between px-5 py-4">
              <div>
                <div class="text-sm text-gray-100">{{ tenant.name }}</div>
                <div class="mt-1 text-xs text-gray-500">{{ tenant.city || '-' }} · {{ tenant.type || '酒店/民宿' }}</div>
              </div>
              <div class="font-mono text-sm text-amber-300">{{ numberLike(tenant.balance) }}</div>
            </div>
          </div>
        </section>

        <section class="rounded-lg border border-gray-800 bg-gray-900 p-5">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="text-sm font-medium text-gray-200">配置完整度</h2>
            <Database class="h-4 w-4 text-indigo-400" />
          </div>
          <div class="space-y-4">
            <div>
              <div class="progress-label"><span>充值套餐</span><span>{{ enabledPackages }}/{{ packages.length }}</span></div>
              <div class="progress"><div class="bg-indigo-500" :style="{ width: packages.length ? `${enabledPackages / packages.length * 100}%` : '0%' }" /></div>
            </div>
            <div>
              <div class="progress-label"><span>计费规则</span><span>{{ enabledRules }}/{{ rules.length }}</span></div>
              <div class="progress"><div class="bg-cyan-500" :style="{ width: rules.length ? `${enabledRules / rules.length * 100}%` : '0%' }" /></div>
            </div>
            <div>
              <div class="progress-label"><span>智能体密钥</span><span>{{ configuredAgents }}/{{ bindings.length }}</span></div>
              <div class="progress"><div class="bg-emerald-500" :style="{ width: bindings.length ? `${configuredAgents / bindings.length * 100}%` : '0%' }" /></div>
            </div>
            <div>
              <div class="progress-label"><span>启用智能体</span><span>{{ enabledAgents }}/{{ bindings.length }}</span></div>
              <div class="progress"><div class="bg-amber-500" :style="{ width: bindings.length ? `${enabledAgents / bindings.length * 100}%` : '0%' }" /></div>
            </div>
          </div>
        </section>
      </div>

      <section class="rounded-lg border border-gray-800 bg-gray-900">
        <div class="section-head">
          <h2>最近算力流水</h2>
          <RouterLink class="text-xs text-indigo-300 hover:text-indigo-200" to="/admin/ledger">查看流水</RouterLink>
        </div>
        <div v-if="ledgers.length === 0" class="empty">暂无流水记录</div>
        <div v-else class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="text-xs text-gray-500">
              <tr class="border-b border-gray-800">
                <th class="px-5 py-3 font-medium">租户</th>
                <th class="px-5 py-3 font-medium">类型</th>
                <th class="px-5 py-3 font-medium">模块</th>
                <th class="px-5 py-3 font-medium">变动</th>
                <th class="px-5 py-3 font-medium">说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in ledgers.slice(0, 8)" :key="item.id" class="border-b border-gray-800/60">
                <td class="px-5 py-3 text-gray-200">{{ getTenantName(item.tenantId || item.tenant_id) }}</td>
                <td class="px-5 py-3 text-gray-500">{{ item.type === 'recharge' ? '充值' : '消耗' }}</td>
                <td class="px-5 py-3 font-mono text-gray-400">{{ item.moduleKey || item.module_key || '-' }}</td>
                <td class="px-5 py-3 font-mono" :class="Number(item.amount || 0) >= 0 ? 'text-emerald-300' : 'text-red-300'">
                  {{ Number(item.amount || 0) >= 0 ? '+' : '' }}{{ item.amount || 0 }}
                </td>
                <td class="px-5 py-3 text-gray-500">{{ item.detail || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
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
.metric-card {
  border-radius: 0.5rem;
  border: 1px solid #1f2937;
  background: #111827;
  padding: 1.25rem;
}
.metric-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #6b7280;
  font-size: 0.75rem;
}
.metric-value {
  margin-top: 0.75rem;
  color: #f3f4f6;
  font-size: 1.875rem;
  font-weight: 650;
}
.metric-note {
  margin-top: 0.25rem;
  color: #6b7280;
  font-size: 0.75rem;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #1f2937;
  padding: 1rem 1.25rem;
}
.section-head h2 {
  color: #e5e7eb;
  font-size: 0.875rem;
  font-weight: 600;
}
.empty {
  padding: 2rem 1.25rem;
  text-align: center;
  color: #6b7280;
  font-size: 0.875rem;
}
.progress-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.375rem;
  color: #9ca3af;
  font-size: 0.75rem;
}
.progress {
  height: 0.5rem;
  overflow: hidden;
  border-radius: 999px;
  background: #1f2937;
}
.progress div {
  height: 100%;
}
</style>
