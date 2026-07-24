<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Activity,
  Bot,
  Building2,
  ChevronRight,
  Coins,
  CreditCard,
  Gauge,
  LayoutDashboard,
  MessageSquare,
  ReceiptText,
  Settings,
  Wallet,
} from 'lucide-vue-next'
import api from '@/api'

interface MenuItem {
  icon: any
  label: string
  path: string
  note?: string
}

const route = useRoute()
const router = useRouter()

const sections: { label: string; items: MenuItem[] }[] = [
  {
    label: '总览',
    items: [
      { icon: LayoutDashboard, label: '平台总览', path: '/admin', note: '运营指标' },
    ],
  },
  {
    label: '租户与酒店',
    items: [
      { icon: Building2, label: '酒店账户', path: '/admin/tenants', note: '资料、套餐、余额' },
    ],
  },
  {
    label: 'AI 内容引擎',
    items: [
      { icon: Bot, label: '智能体与功能绑定', path: '/admin/ai', note: 'Dify App 绑定' },
      { icon: Activity, label: 'AI 调用日志', path: '/admin/logs', note: '成功率与失败原因' },
    ],
  },
  {
    label: '算力与计费',
    items: [
      { icon: Coins, label: '模块计费规则', path: '/admin/billing', note: '单次消耗' },
      { icon: CreditCard, label: '充值套餐', path: '/admin/recharge', note: '套餐权益' },
      { icon: ReceiptText, label: '消耗流水', path: '/admin/ledger', note: '扣费记录' },
    ],
  },
  {
    label: '系统配置',
    items: [
      { icon: MessageSquare, label: '短信配置', path: '/admin/sms', note: '通知通道' },
    ],
  },
]

const totalTenants = ref(0)
const lowBalanceTenants = ref(0)
const totalBalance = ref(0)

onMounted(loadHeaderMetrics)

async function loadHeaderMetrics() {
  try {
    const { data } = await api.get('/api/admin/tenants')
    const tenants = data.data || []
    totalTenants.value = tenants.length
    lowBalanceTenants.value = tenants.filter((item: any) => Number(item.balance || 0) < 500).length
    totalBalance.value = tenants.reduce((sum: number, item: any) => sum + Number(item.balance || 0), 0)
  } catch {
    totalTenants.value = 0
    lowBalanceTenants.value = 0
    totalBalance.value = 0
  }
}

function isActive(path: string) {
  return route.path === path
}

function navigate(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="flex h-screen bg-gray-950 text-gray-200">
    <aside class="flex w-64 flex-shrink-0 flex-col border-r border-gray-800 bg-gray-900">
      <div class="flex h-14 items-center gap-3 border-b border-gray-800 px-5">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600 text-sm font-bold text-white">
          S
        </div>
        <div>
          <div class="text-sm font-semibold text-gray-100">宿识家 AI</div>
          <div class="text-[11px] text-gray-500">平台运营后台</div>
        </div>
      </div>

      <nav class="flex-1 overflow-y-auto py-4">
        <template v-for="section in sections" :key="section.label">
          <div class="px-4 pb-2 pt-4 text-[11px] font-medium text-gray-500 first:pt-0">
            {{ section.label }}
          </div>
          <button
            v-for="item in section.items"
            :key="item.path"
            class="w-full px-3"
            @click="navigate(item.path)"
          >
            <div
              class="group flex items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors"
              :class="isActive(item.path) ? 'bg-indigo-600/15 text-indigo-300' : 'text-gray-400 hover:bg-gray-800 hover:text-gray-100'"
            >
              <component :is="item.icon" class="h-4 w-4 flex-shrink-0" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-xs font-medium">{{ item.label }}</div>
                <div class="truncate text-[10px] text-gray-600">{{ item.note }}</div>
              </div>
              <ChevronRight v-if="isActive(item.path)" class="h-3.5 w-3.5 flex-shrink-0" />
            </div>
          </button>
        </template>
      </nav>

      <div class="border-t border-gray-800 p-4">
        <div class="space-y-2 rounded-lg border border-gray-800 bg-gray-950 p-3">
          <div class="flex items-center justify-between text-[11px]">
            <span class="text-gray-500">在营租户</span>
            <span class="font-mono text-gray-200">{{ totalTenants }}</span>
          </div>
          <div class="flex items-center justify-between text-[11px]">
            <span class="text-gray-500">低余额</span>
            <span :class="lowBalanceTenants > 0 ? 'text-amber-300' : 'text-gray-400'" class="font-mono">
              {{ lowBalanceTenants }}
            </span>
          </div>
        </div>
      </div>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
      <header class="flex h-14 flex-shrink-0 items-center justify-between border-b border-gray-800 bg-gray-900/60 px-6">
        <div class="flex items-center gap-2 text-xs text-gray-400">
          <Gauge class="h-4 w-4 text-indigo-400" />
          <span>AI SaaS 运营控制台</span>
        </div>

        <div class="flex items-center gap-5 text-[11px] text-gray-500">
          <span class="flex items-center gap-1.5">
            <Building2 class="h-3.5 w-3.5" />
            {{ totalTenants }} 家租户
          </span>
          <span class="flex items-center gap-1.5">
            <Wallet class="h-3.5 w-3.5" />
            {{ totalBalance.toLocaleString() }} 总算力余额
          </span>
          <button class="flex items-center gap-1.5 text-indigo-300 hover:text-indigo-200" @click="navigate('/admin/ai')">
            <Settings class="h-3.5 w-3.5" />
            AI 配置
          </button>
        </div>
      </header>

      <main class="flex-1 overflow-auto p-6">
        <router-view />
      </main>
    </div>
  </div>
</template>
