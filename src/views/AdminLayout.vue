<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import {
  LayoutDashboard, Building, IdCard, Network, Bolt, Wallet, Receipt,
  FileText, Palette, ThumbsUp, ShieldCheck, Cpu, Activity, History, Users,
  Settings, MessageSquare
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

interface MenuItem { icon: any; label: string; path: string; badge?: string }

const sections: { label: string; items: MenuItem[] }[] = [
  {
    label: '总览',
    items: [{ icon: LayoutDashboard, label: '平台总览', path: '/admin' }],
  },
  {
    label: '租户管理',
    items: [
      { icon: Building, label: '酒店账户列表', path: '/admin/tenants' },
      { icon: IdCard, label: '租户详情/配置', path: '/admin/tenant-detail' },
      { icon: Network, label: '连锁/多门店', path: '/admin/chains' },
    ],
  },
  {
    label: '算力计费',
    items: [
      { icon: Bolt, label: '模块计费规则', path: '/admin/billing' },
      { icon: Wallet, label: '充值套餐与权限', path: '/admin/recharge' },
      { icon: Receipt, label: '消耗流水/账单', path: '/admin/ledger' },
    ],
  },
  {
    label: 'AI 内容引擎',
    items: [
      { icon: FileText, label: '主模板中心', path: '/admin/prompts', badge: '8' },
      { icon: Palette, label: '风格库管理', path: '/admin/styles' },
      { icon: ThumbsUp, label: '内容反馈闭环', path: '/admin/feedback', badge: '12' },
      { icon: ShieldCheck, label: '内容合规审查', path: '/admin/moderation' },
      { icon: Cpu, label: '模型与渠道配置', path: '/admin/models' },
    ],
  },
  {
    label: '系统',
    items: [
      { icon: Activity, label: '调用日志/监控', path: '/admin/logs' },
      { icon: History, label: '操作审计日志', path: '/admin/audit' },
      { icon: Users, label: '管理员与权限', path: '/admin/roles' },
    ],
  },
  {
    label: '配置',
    items: [
      { icon: Settings, label: 'AI API 配置', path: '/admin/ai' },
      { icon: MessageSquare, label: '短信服务配置', path: '/admin/sms' },
    ],
  },
]

function isActive(path: string) { return route.path === path }
function navigate(path: string) { router.push(path) }

// Extra top-right info
import { ref, onMounted } from 'vue'
import api from '@/api'

const totalTenants = ref(0)
const monthlyConsume = ref(0)

onMounted(async () => {
  try {
    const { data } = await api.get('/api/admin/tenants')
    const list = data.data || []
    totalTenants.value = list.length
    monthlyConsume.value = list.reduce((s: number, t: any) => s + (t.balance || 0), 0)
  } catch { /* */ }
})
</script>

<template>
  <div class="h-screen flex bg-gray-950 text-gray-200">
    <!-- 侧边栏 -->
    <aside class="w-48 flex-shrink-0 border-r border-gray-800 flex flex-col bg-gray-900 overflow-y-auto">
      <nav class="flex-1 py-3 space-y-0">
        <template v-for="(section, si) in sections" :key="si">
          <div class="text-[10px] font-semibold text-gray-500 uppercase tracking-wider px-4 py-2">
            {{ section.label }}
          </div>
          <button
            v-for="item in section.items" :key="item.path"
            @click="navigate(item.path)"
            :class="[
              'flex items-center gap-2.5 px-4 py-2 text-xs w-full text-left transition-colors relative',
              isActive(item.path)
                ? 'bg-indigo-600/10 text-indigo-400 font-medium'
                : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
            ]"
          >
            <span v-if="isActive(item.path)" class="absolute left-0 top-1 bottom-1 w-0.5 bg-indigo-500 rounded-r-full" />
            <component :is="item.icon" class="w-4 h-4 flex-shrink-0" />
            <span class="truncate flex-1">{{ item.label }}</span>
            <span v-if="item.badge" class="text-[9px] bg-red-500/20 text-red-400 px-1.5 py-0.5 rounded-full font-medium">{{ item.badge }}</span>
          </button>
          <div v-if="si < sections.length - 1" class="mx-3 my-2 border-b border-gray-800/50" />
        </template>
      </nav>
    </aside>

    <!-- 右侧内容 -->
    <div class="flex-1 flex flex-col min-w-0 overflow-hidden">
      <!-- 顶部 -->
      <header class="h-11 flex items-center justify-between px-5 border-b border-gray-800 bg-gray-900/50 flex-shrink-0">
        <div class="flex items-center gap-2">
          <div class="w-6 h-6 rounded-md bg-indigo-600 flex items-center justify-center">
            <span class="text-white text-[10px] font-bold">S</span>
          </div>
          <span class="text-xs font-medium text-gray-300">宿营家 AI · 运营管理后台</span>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-[10px] text-gray-500 flex items-center gap-1">
            <Building class="w-3 h-3" />{{ totalTenants }} 家在营租户
          </span>
          <span class="text-[10px] text-gray-500 flex items-center gap-1">
            <Bolt class="w-3 h-3" />本月消耗 {{ monthlyConsume.toLocaleString() }} 算力
          </span>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="flex-1 overflow-auto">
        <router-view />
      </main>
    </div>
  </div>
</template>
