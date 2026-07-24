<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api'
import { Package, RefreshCw, Loader2 } from 'lucide-vue-next'

const packages = ref<any[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try { const { data } = await api.get('/api/admin/packages'); packages.value = data.data || [] } catch { /* */ }
  loading.value = false
}

onMounted(load)

const tierColors: Record<string, string> = {
  trial: 'text-gray-400 bg-gray-800',
  basic: 'text-blue-400 bg-blue-500/10',
  'basic,pro': 'text-green-400 bg-green-500/10',
  'pro,flagship': 'text-purple-400 bg-purple-500/10',
  flagship: 'text-purple-400 bg-purple-500/10',
}
</script>

<template>
  <div class="p-6 space-y-5">
    <div class="flex items-center justify-between">
      <h1 class="text-sm font-semibold text-gray-200 flex items-center gap-2"><Package class="w-4 h-4 text-indigo-400" />算力充值套餐</h1>
      <button @click="load" class="flex items-center gap-1.5 text-xs text-gray-400 hover:text-gray-200 transition-colors">
        <RefreshCw class="w-3 h-3" />刷新
      </button>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20"><Loader2 class="w-6 h-6 animate-spin text-gray-600" /></div>

    <div v-else class="grid grid-cols-4 gap-4">
      <div v-for="p in packages" :key="p.id" class="bg-gray-900 border border-gray-800 rounded-xl p-6 text-center hover:border-indigo-500/30 transition-colors">
        <div class="text-xs font-semibold text-gray-300 mb-3">{{ p.name }}</div>
        <div class="text-3xl font-bold text-white mb-1">{{ p.credits }}</div>
        <div class="text-[10px] text-gray-500 mb-4">算力</div>
        <div class="text-xl font-semibold text-indigo-400 mb-3">¥{{ p.price_rmb }}</div>
        <span v-if="p.applicable_tiers" :class="['text-[10px] px-2 py-1 rounded-full', tierColors[p.applicable_tiers] || 'text-gray-400 bg-gray-800']">
          {{ p.applicable_tiers }}
        </span>
      </div>
    </div>
  </div>
</template>
