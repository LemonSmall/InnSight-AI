<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCreditsStore } from '@/stores/credits'
import {
  ArrowLeft, Zap, TrendingDown, TrendingUp, Search,
  Calendar, Clock
} from 'lucide-vue-next'

const router = useRouter()
const store = useCreditsStore()

const tab = ref<'consume' | 'recharge'>('consume')
const searchQuery = ref('')
const pageError = ref('')

onMounted(() => {
  reload()
})

async function reload() {
  pageError.value = ''
  try {
    await store.loadFromApi()
  } catch {
    pageError.value = store.error || '算力数据加载失败，请稍后重试'
  }
}

const list = computed(() => {
  const source = tab.value === 'consume' ? store.consumeRecords : store.rechargeRecords
  if (!searchQuery.value) return source
  const q = searchQuery.value.toLowerCase()
  return source.filter(r =>
    r.module.toLowerCase().includes(q) ||
    r.detail.toLowerCase().includes(q)
  )
})

// 按日期分组
const grouped = computed(() => {
  const groups: { date: string; items: typeof list.value }[] = []
  for (const r of list.value) {
    const last = groups[groups.length - 1]
    if (last && last.date === r.date) {
      last.items.push(r)
    } else {
      groups.push({ date: r.date || '未知日期', items: [r] })
    }
  }
  return groups
})
</script>

<template>
  <div class="p-6 space-y-5">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <button @click="router.back()" class="w-7 h-7 flex items-center justify-center rounded-lg border border-cream-300 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors">
          <ArrowLeft class="w-4 h-4" />
        </button>
        <h1 class="text-lg font-semibold text-bamboo-900">算力流水</h1>
      </div>
    </div>

    <!-- 余额卡片 -->
    <div class="card !p-5">
      <div class="flex items-center justify-between">
        <div>
          <div class="text-xs text-warm-500 mb-1">当前算力余额</div>
          <div class="text-3xl font-bold text-bamboo-800">{{ store.currentBalance.toLocaleString() }}</div>
        </div>
        <div class="text-right">
          <div class="text-xs text-bamboo-600 mb-1">今日消耗</div>
          <div class="text-xl font-semibold text-rose-500 flex items-center justify-end gap-1">
            <TrendingDown class="w-4 h-4" />
            {{ store.todayConsume }} 算力
          </div>
        </div>
      </div>
    </div>

    <!-- Tab + Search -->
    <div class="flex items-center justify-between gap-3">
      <div class="flex gap-1 bg-cream-100 rounded-lg p-0.5">
        <button
          @click="tab = 'consume'"
          :class="[
            'px-4 py-1.5 rounded-md text-xs font-medium transition-colors',
            tab === 'consume' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-warm-700'
          ]"
        >
          <TrendingDown class="w-3 h-3 inline mr-1" />
          消耗记录
        </button>
        <button
          @click="tab = 'recharge'"
          :class="[
            'px-4 py-1.5 rounded-md text-xs font-medium transition-colors',
            tab === 'recharge' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-warm-700'
          ]"
        >
          <TrendingUp class="w-3 h-3 inline mr-1" />
          充值记录
        </button>
      </div>

      <div class="relative">
        <Search class="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-warm-400" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索模块或内容..."
          class="text-[12px] pl-7 pr-3 py-1.5 rounded-lg border border-cream-300 bg-white text-bamboo-950 placeholder:text-warm-400 focus:outline-none focus:border-bamboo-400 w-48"
        />
      </div>
    </div>

    <div v-if="pageError" class="card border-rose-200 bg-rose-50 text-rose-700 text-sm flex items-center justify-between">
      <span>{{ pageError }}</span>
      <button @click="reload" class="btn-ghost text-sm">重新加载</button>
    </div>

    <!-- 流水列表 -->
    <div class="card !p-0 overflow-hidden">
      <div v-if="store.loading" class="py-16 text-center text-warm-500 text-sm">
        加载中...
      </div>
      <div v-else-if="list.length === 0" class="py-16 text-center text-warm-500 text-sm">
        暂无记录
      </div>
      <template v-else>
        <div v-for="group in grouped" :key="group.date">
          <!-- 日期标题 -->
          <div class="px-4 py-2 bg-cream-50 border-b border-cream-100 flex items-center gap-2">
            <Calendar class="w-3 h-3 text-warm-500" />
            <span class="text-[11px] font-medium text-warm-600">{{ group.date }}</span>
            <span class="text-[10px] text-warm-400">{{ group.items.length }} 条</span>
          </div>

          <!-- 条目 -->
          <div
            v-for="record in group.items"
            :key="record.id"
            class="flex items-center justify-between px-4 py-3 border-b border-cream-100 last:border-b-0 hover:bg-cream-50/50 transition-colors"
          >
            <div class="flex items-center gap-3 min-w-0">
              <div
                :class="[
                  'w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0',
                  record.type === 'consume' ? 'bg-rose-50 text-rose-500' : 'bg-bamboo-50 text-bamboo-600'
                ]"
              >
                <TrendingDown v-if="record.type === 'consume'" class="w-4 h-4" />
                <TrendingUp v-else class="w-4 h-4" />
              </div>
              <div class="min-w-0">
                <div class="flex items-center gap-1.5">
                  <span class="text-[11px] font-medium bg-cream-100 text-warm-700 px-1.5 py-0.5 rounded">
                    {{ record.module }}
                  </span>
                  <span class="text-[10px] text-warm-400 flex items-center gap-0.5">
                    <Clock class="w-2.5 h-2.5" />{{ record.time }}
                  </span>
                </div>
                <div class="text-xs text-warm-600 mt-0.5 truncate">{{ record.detail }}</div>
              </div>
            </div>

            <div class="text-right flex-shrink-0 ml-4">
              <div
                :class="[
                  'text-sm font-semibold',
                  record.type === 'consume' ? 'text-rose-500' : 'text-bamboo-600'
                ]"
              >
                {{ record.type === 'consume' ? '' : '+' }}{{ record.amount }}
              </div>
              <div class="text-[10px] text-warm-400">余额 {{ record.balance }}</div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
