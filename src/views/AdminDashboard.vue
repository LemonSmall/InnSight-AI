<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api'
import { TrendingUp, AlertTriangle } from 'lucide-vue-next'

const stats = ref({ tenants: 0, monthly: 0, revenue: 0, warnings: 0 })
const barData = ref<{ label: string; h: number; color: string }[]>([])
const trend = ref<{ label: string; h: number }[]>([])

onMounted(async () => {
  try {
    const { data } = await api.get('/api/admin/tenants')
    const list = data.data || []
    stats.value.tenants = list.length
    stats.value.warnings = list.filter((t: any) => t.status === 'warning' || t.balance < 500).length
    stats.value.monthly = 128400
    stats.value.revenue = 38520
  } catch { /* */ }

  barData.value = [
    { label: '海报/修图', h: 85, color: 'bg-indigo-700' },
    { label: '视频口播', h: 62, color: 'bg-indigo-500' },
    { label: '小红书', h: 50, color: 'bg-indigo-400' },
    { label: '朋友圈', h: 34, color: 'bg-indigo-300' },
    { label: '智慧大脑', h: 28, color: 'bg-emerald-600' },
    { label: '好评话术', h: 20, color: 'bg-emerald-400' },
    { label: '公众号', h: 14, color: 'bg-emerald-300' },
    { label: '房态识别', h: 8, color: 'bg-gray-600' },
  ]
  trend.value = [
    { label: '06-03', h: 55 }, { label: '06-04', h: 62 }, { label: '06-05', h: 48 },
    { label: '06-06', h: 80 }, { label: '06-07', h: 70 }, { label: '06-08', h: 35 },
    { label: '06-09', h: 90 },
  ]
})
</script>

<template>
  <div class="p-5 space-y-4">
    <!-- KPI cards -->
    <div class="grid grid-cols-4 gap-3">
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <div class="text-2xl font-bold text-white">{{ stats.tenants }}</div>
        <div class="text-[11px] text-gray-500">在营租户数</div>
        <div class="text-[10px] text-emerald-500 mt-1"><TrendingUp class="w-3 h-3 inline" /> +3 本月新增</div>
      </div>
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <div class="text-2xl font-bold text-white">{{ stats.monthly.toLocaleString() }}</div>
        <div class="text-[11px] text-gray-500">本月算力消耗</div>
        <div class="text-[10px] text-emerald-500 mt-1"><TrendingUp class="w-3 h-3 inline" /> +18% 环比</div>
      </div>
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <div class="text-2xl font-bold text-white">¥{{ stats.revenue.toLocaleString() }}</div>
        <div class="text-[11px] text-gray-500">本月充值流水</div>
        <div class="text-[10px] text-gray-500 mt-1">折算单价0.3元/算力</div>
      </div>
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <div class="text-2xl font-bold text-amber-400">{{ stats.warnings }}</div>
        <div class="text-[11px] text-gray-500">余额预警租户</div>
        <div class="text-[10px] text-red-400 mt-1"><AlertTriangle class="w-3 h-3 inline" /> 需关注</div>
      </div>
    </div>

    <!-- 算力消耗占比 + 告警 -->
    <div class="grid grid-cols-2 gap-3">
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <div class="text-xs font-medium text-gray-300 mb-3">各模块算力消耗占比（本月）</div>
        <div class="flex items-end gap-1.5 h-24">
          <div v-for="b in barData" :key="b.label" class="flex-1 flex flex-col items-center gap-1">
            <div :class="[b.color, 'w-full rounded-t']" :style="{ height: b.h + '%' }"></div>
            <span class="text-[8px] text-gray-600">{{ b.label }}</span>
          </div>
        </div>
      </div>
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <div class="text-xs font-medium text-gray-300 mb-3 flex items-center gap-1.5">
          <AlertTriangle class="w-3.5 h-3.5 text-amber-500" />需要关注
        </div>
        <div class="space-y-2">
          <div class="text-xs text-amber-400 bg-amber-500/10 border-l-2 border-amber-500 rounded-r p-2">「莫干山·松间民宿」算力余额仅剩 <strong>320</strong>，预计2天内耗尽</div>
          <div class="text-xs text-red-400 bg-red-500/10 border-l-2 border-red-500 rounded-r p-2">「大理·清风客栈」海报生成失败率12%，疑似图像模型超时</div>
          <div class="text-xs text-red-400 bg-red-500/10 border-l-2 border-red-500 rounded-r p-2">「观山雅集」账户已欠费，已触发熔断，所有AI调用暂停</div>
          <div class="text-xs text-emerald-400 bg-emerald-500/10 border-l-2 border-emerald-500 rounded-r p-2">本周新增3家租户均已完成首次配置，激活率100%</div>
        </div>
      </div>
    </div>

    <!-- 调用趋势 -->
    <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
      <div class="text-xs font-medium text-gray-300 mb-3">近7日平台调用趋势</div>
      <div class="flex items-end gap-2 h-20">
        <div v-for="t in trend" :key="t.label" class="flex-1 flex flex-col items-center gap-1">
          <div class="w-full bg-indigo-600/60 rounded-t" :style="{ height: t.h + '%' }"></div>
          <span class="text-[9px] text-gray-600">{{ t.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
