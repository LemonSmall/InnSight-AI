<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/api'
import { Activity, Loader2, RefreshCw, Search } from 'lucide-vue-next'

type AiCallLog = {
  id: number
  tenantId?: number
  moduleKey?: string
  provider?: string
  appName?: string
  appType?: string
  endpoint?: string
  requestId?: string
  taskId?: number
  status?: string
  httpStatus?: number
  durationMs?: number
  inputTokens?: number
  outputTokens?: number
  creditsCost?: number
  errorCode?: string
  errorMessage?: string
  requestSummary?: string
  responseSummary?: string
  createdAt?: string
}

const logs = ref<AiCallLog[]>([])
const loading = ref(true)
const toast = ref('')
const keyword = ref('')
const statusFilter = ref('')
const moduleFilter = ref('')
const selected = ref<AiCallLog | null>(null)

const filteredLogs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return logs.value.filter((log) => {
    const matchStatus = !statusFilter.value || log.status === statusFilter.value
    const matchModule = !moduleFilter.value || log.moduleKey === moduleFilter.value
    const matchKeyword = !kw || [log.moduleKey, log.appName, log.errorCode, log.errorMessage, log.requestId]
      .some((item) => String(item || '').toLowerCase().includes(kw))
    return matchStatus && matchModule && matchKeyword
  })
})

const modules = computed(() => Array.from(new Set(logs.value.map((log) => log.moduleKey).filter(Boolean))) as string[])
const successCount = computed(() => logs.value.filter((log) => log.status === 'success').length)
const failedCount = computed(() => logs.value.filter((log) => log.status === 'failed').length)
const successRate = computed(() => logs.value.length ? Math.round((successCount.value / logs.value.length) * 100) : 0)
const avgDuration = computed(() => {
  const durations = logs.value.map((log) => Number(log.durationMs || 0)).filter((value) => value > 0)
  if (!durations.length) return 0
  return Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length)
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/api/admin/ai-call-logs', { params: { limit: 200 } })
    if (data.code && data.code !== 200) {
      logs.value = []
      flash(data.message || 'AI 调用日志加载失败')
      return
    }
    logs.value = data.data || []
  } catch (e: any) {
    logs.value = []
    flash(e?.response?.data?.message || 'AI 调用日志加载失败')
  } finally {
    loading.value = false
  }
}

function statusLabel(status?: string) {
  return status === 'success' ? '成功' : '失败'
}

function statusClass(status?: string) {
  return status === 'success' ? 'bg-emerald-500/10 text-emerald-300' : 'bg-red-500/10 text-red-300'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function flash(message: string) {
  toast.value = message
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
            <Activity class="h-5 w-5 text-indigo-400" />
            AI 调用日志
          </div>
          <p class="mt-1 text-sm text-gray-500">监控 Dify 调用成功率、耗时、失败原因与模块绑定情况。</p>
        </div>
        <button class="btn-secondary" @click="load">
          <RefreshCw class="h-4 w-4" />
          刷新
        </button>
      </div>

      <div class="mt-5 grid gap-3 md:grid-cols-4">
        <div class="metric">
          <span>最近调用</span>
          <strong>{{ logs.length }}</strong>
        </div>
        <div class="metric">
          <span>成功率</span>
          <strong :class="successRate < 90 && logs.length ? 'text-amber-300' : ''">{{ successRate }}%</strong>
        </div>
        <div class="metric">
          <span>失败次数</span>
          <strong :class="failedCount > 0 ? 'text-red-300' : ''">{{ failedCount }}</strong>
        </div>
        <div class="metric">
          <span>平均耗时</span>
          <strong>{{ avgDuration }}ms</strong>
        </div>
      </div>
    </section>

    <section class="rounded-lg border border-gray-800 bg-gray-900">
      <div class="grid gap-3 border-b border-gray-800 p-4 md:grid-cols-[1fr_180px_180px]">
        <div class="relative">
          <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500" />
          <input v-model="keyword" class="input pl-9" placeholder="搜索模块、智能体、错误、requestId" />
        </div>
        <select v-model="moduleFilter" class="input">
          <option value="">全部模块</option>
          <option v-for="module in modules" :key="module" :value="module">{{ module }}</option>
        </select>
        <select v-model="statusFilter" class="input">
          <option value="">全部状态</option>
          <option value="success">成功</option>
          <option value="failed">失败</option>
        </select>
      </div>

      <div v-if="loading" class="flex justify-center py-16">
        <Loader2 class="h-6 w-6 animate-spin text-gray-500" />
      </div>

      <div v-else class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="text-xs text-gray-500">
            <tr class="border-b border-gray-800">
              <th class="px-4 py-3 font-medium">时间</th>
              <th class="px-4 py-3 font-medium">模块</th>
              <th class="px-4 py-3 font-medium">智能体</th>
              <th class="px-4 py-3 font-medium">状态</th>
              <th class="px-4 py-3 font-medium">HTTP</th>
              <th class="px-4 py-3 font-medium">耗时</th>
              <th class="px-4 py-3 font-medium">错误摘要</th>
              <th class="px-4 py-3 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in filteredLogs" :key="log.id" class="border-b border-gray-800/70 hover:bg-gray-800/40">
              <td class="px-4 py-3 font-mono text-gray-400">{{ formatTime(log.createdAt) }}</td>
              <td class="px-4 py-3 font-mono text-gray-200">{{ log.moduleKey || '-' }}</td>
              <td class="px-4 py-3">
                <div class="text-gray-100">{{ log.appName || '-' }}</div>
                <div class="text-xs text-gray-500">{{ log.provider || '-' }} · {{ log.appType || '-' }}</div>
              </td>
              <td class="px-4 py-3">
                <span :class="['rounded-full px-2 py-1 text-xs', statusClass(log.status)]">{{ statusLabel(log.status) }}</span>
              </td>
              <td class="px-4 py-3 font-mono text-gray-400">{{ log.httpStatus || '-' }}</td>
              <td class="px-4 py-3 font-mono text-gray-300">{{ log.durationMs || 0 }}ms</td>
              <td class="max-w-sm truncate px-4 py-3 text-red-300/80">{{ log.errorMessage || '-' }}</td>
              <td class="px-4 py-3 text-right">
                <button class="text-indigo-300 hover:text-indigo-200" @click="selected = log">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="filteredLogs.length === 0" class="py-12 text-center text-sm text-gray-500">暂无调用日志</div>
      </div>
    </section>

    <div v-if="selected" class="fixed inset-0 z-40 flex items-center justify-center bg-black/70 p-4" @click.self="selected = null">
      <div class="max-h-[86vh] w-full max-w-4xl overflow-hidden rounded-lg border border-gray-700 bg-gray-900 shadow-2xl">
        <div class="flex items-center justify-between border-b border-gray-800 p-4">
          <div>
            <h2 class="font-semibold text-gray-100">调用详情</h2>
            <p class="mt-1 font-mono text-xs text-gray-500">{{ selected.requestId || '-' }}</p>
          </div>
          <button class="rounded-md px-3 py-1.5 text-sm text-gray-400 hover:bg-gray-800" @click="selected = null">关闭</button>
        </div>
        <div class="grid max-h-[72vh] gap-4 overflow-y-auto p-4 md:grid-cols-2">
          <div class="detail-block">
            <h3>请求摘要</h3>
            <pre>{{ selected.requestSummary || '-' }}</pre>
          </div>
          <div class="detail-block">
            <h3>响应摘要</h3>
            <pre>{{ selected.responseSummary || '-' }}</pre>
          </div>
          <div class="detail-block md:col-span-2">
            <h3>错误信息</h3>
            <pre>{{ selected.errorCode || '-' }} {{ selected.errorMessage || '' }}</pre>
          </div>
        </div>
      </div>
    </div>
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
.detail-block {
  min-width: 0;
}
.detail-block h3 {
  margin-bottom: 0.5rem;
  font-size: 0.75rem;
  color: #9ca3af;
}
.detail-block pre {
  max-height: 18rem;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border-radius: 0.5rem;
  border: 1px solid #1f2937;
  background: #030712;
  padding: 0.75rem;
  font-size: 0.75rem;
  color: #d1d5db;
}
</style>
