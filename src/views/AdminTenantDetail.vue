<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { IdCard, Loader2, RefreshCw } from 'lucide-vue-next'

const route = useRoute()
const tenant = ref<any>(null)
const loading = ref(true)
const ledger = ref<any[]>([])

const tierLabels: Record<string, string> = { trial: '试用版', basic: '基础版', pro: '专业版', flagship: '旗舰版' }

onMounted(load)

async function load() {
  loading.value = true
  try {
    const tenantId = route.query.id || 1
    const { data: td } = await api.get(`/api/admin/tenants/${tenantId}`)
    tenant.value = td.data || td
    const { data: ld } = await api.get('/api/admin/ledger')
    ledger.value = (ld.data || []).slice(0, 10)
  } catch { /* */ }
  loading.value = false
}

function typeColor(t: string) { return t === 'recharge' ? 'text-emerald-400' : 'text-red-400' }
</script>

<template>
  <div class="p-5 space-y-4">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><IdCard class="w-4 h-4 text-indigo-400" />租户详情/配置</div>
      <button class="flex items-center gap-1 text-[10px] text-gray-500 hover:text-gray-300" @click="load"><RefreshCw class="w-3 h-3" />刷新</button>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20"><Loader2 class="w-5 h-5 animate-spin text-gray-600" /></div>

    <template v-else-if="tenant">
      <!-- 基本信息 -->
      <div class="grid grid-cols-2 gap-3">
        <div class="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-2">
          <div class="text-xs font-medium text-gray-300 mb-3">基本信息</div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">酒店名称</span><span class="text-gray-200">{{ tenant.name }}</span></div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">类型</span><span class="text-gray-200">{{ tenant.type }}</span></div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">城市</span><span class="text-gray-200">{{ tenant.city }}</span></div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">房间数</span><span class="text-gray-200">{{ tenant.total_rooms }}</span></div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">特色标签</span><span class="text-gray-200">{{ tenant.tags }}</span></div>
        </div>
        <div class="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-2">
          <div class="text-xs font-medium text-gray-300 mb-3">账户信息</div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">套餐</span><span class="text-indigo-400">{{ tierLabels[tenant.tier] || tenant.tier }}</span></div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">算力余额</span><span :class="tenant.balance < 500 ? 'text-red-400' : 'text-gray-200'">{{ tenant.balance }}</span></div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">预警阈值</span><span class="text-gray-200">{{ tenant.alert_threshold }}</span></div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">QPS限制</span><span class="text-gray-200">{{ tenant.qps_limit }}</span></div>
          <div class="flex justify-between text-xs"><span class="text-gray-500">状态</span><span :class="tenant.status === 'active' ? 'text-emerald-400' : 'text-amber-400'">{{ tenant.status === 'active' ? '正常' : '预警' }}</span></div>
          <div class="flex gap-2 mt-3">
            <button class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500">修改配置</button>
            <button class="px-3 py-1.5 rounded bg-emerald-600 text-white text-[10px] hover:bg-emerald-500">充值</button>
          </div>
        </div>
      </div>

      <!-- 消耗流水 -->
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <div class="text-xs font-medium text-gray-300 mb-3">最近消耗流水</div>
        <table class="w-full text-xs">
          <thead>
            <tr class="border-b border-gray-800 text-gray-500 text-left">
              <th class="py-2 px-3">时间</th><th class="py-2 px-3">类型</th><th class="py-2 px-3">金额</th><th class="py-2 px-3">余额</th><th class="py-2 px-3">备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in ledger" :key="l.id" class="border-b border-gray-800/50">
              <td class="py-2 px-3 text-gray-500">{{ (l.created_at || '').slice(0, 16) }}</td>
              <td class="py-2 px-3 text-gray-300">{{ l.type === 'recharge' ? '充值' : '消耗' }}</td>
              <td class="py-2 px-3 font-mono" :class="typeColor(l.type)">{{ l.type === 'recharge' ? '+' : '' }}{{ l.amount }}</td>
              <td class="py-2 px-3 text-gray-500 font-mono">{{ l.balance_after }}</td>
              <td class="py-2 px-3 text-gray-600">{{ l.detail }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
