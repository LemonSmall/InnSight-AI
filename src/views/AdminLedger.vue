<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api'
import { Receipt, Download, Loader2 } from 'lucide-vue-next'

function showMsg(msg: string) { alert(msg) }

const records = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  loading.value = true
  try { const { data } = await api.get('/api/admin/ledger'); records.value = data.data || [] } catch { /* */ }
  loading.value = false
})

function typeStyle(t: string) {
  if (t === 'recharge') return 'text-emerald-400'
  if (t === 'consume') return 'text-red-400'
  return 'text-gray-400'
}
</script>

<template>
  <div class="p-5 space-y-4">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Receipt class="w-4 h-4 text-blue-400" />算力消耗流水</div>
      <button class="px-3 py-1.5 rounded border border-gray-700 text-gray-400 text-[10px] hover:bg-gray-800 flex items-center gap-1" @click="showMsg('导出功能即将上线')"><Download class="w-3 h-3" />导出Excel</button>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20"><Loader2 class="w-5 h-5 animate-spin text-gray-600" /></div>

    <div v-else class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead>
          <tr class="border-b border-gray-800 text-gray-500 text-left">
            <th class="py-2.5 px-4 font-medium">时间</th>
            <th class="py-2.5 px-4 font-medium">酒店</th>
            <th class="py-2.5 px-4 font-medium">调用模块</th>
            <th class="py-2.5 px-4 font-medium">算力变动</th>
            <th class="py-2.5 px-4 font-medium">余额</th>
            <th class="py-2.5 px-4 font-medium">状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in records" :key="r.id" class="border-b border-gray-800/50 hover:bg-gray-800/30">
            <td class="py-2.5 px-4 text-gray-500">{{ (r.created_at || '').slice(0, 16) }}</td>
            <td class="py-2.5 px-4 text-gray-400">租户#{{ r.tenant_id }}</td>
            <td class="py-2.5 px-4 text-gray-300">{{ r.module_name || r.detail }}</td>
            <td class="py-2.5 px-4 font-mono" :class="typeStyle(r.type)">{{ r.type === 'recharge' ? '+' : '' }}{{ r.amount }}</td>
            <td class="py-2.5 px-4 text-gray-500 font-mono">{{ r.balance_after }}</td>
            <td class="py-2.5 px-4"><span :class="r.status === 'success' ? 'text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded text-[10px]' : 'text-red-400 bg-red-500/10 px-1.5 py-0.5 rounded text-[10px]'">{{ r.status === 'success' ? '成功' : r.status }}</span></td>
          </tr>
        </tbody>
      </table>
      <div v-if="records.length === 0" class="text-center py-8 text-gray-600 text-xs">暂无流水记录</div>
    </div>
  </div>
</template>
