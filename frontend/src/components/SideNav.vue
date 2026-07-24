<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  BookOpenCheck,
  Brain,
  ChevronLeft,
  ChevronRight,
  Coins,
  History,
  Home,
  Image,
  Instagram,
  LayoutDashboard,
  Lightbulb,
  MessageCircleHeart,
  MessageSquareText,
  Newspaper,
  Settings,
  Star,
  Video,
  WandSparkles,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(localStorage.getItem('sideNavCollapsed') === '1')
const showLabels = ref(!collapsed.value)

interface NavSection {
  label: string
  roles: string[]
  items: NavItem[]
}

interface NavItem {
  icon: any
  label: string
  route: string
  dot?: boolean
  desc?: string
  primary?: boolean
}

const allSections: NavSection[] = [
  {
    label: '初始化配置',
    roles: ['admin'],
    items: [
      { icon: Settings, label: '基础信息', route: '/setup', desc: '门店资料' },
      { icon: Home, label: '房型定价', route: '/rooms', desc: '房型维护' },
    ],
  },
  {
    label: '经营工作台',
    roles: ['admin', 'manager'],
    items: [
      { icon: LayoutDashboard, label: '今日工作台', route: '/dashboard', desc: '运营总览' },
      { icon: Brain, label: 'AI 店长', route: '/brain', desc: '经营问答' },
      { icon: BookOpenCheck, label: '资料中心', route: '/knowledge', desc: '更新知识库' },
      { icon: History, label: '内容历史', route: '/history', desc: '复用生成' },
      { icon: Coins, label: '智能定价', route: '/pricing', desc: '价格建议' },
      { icon: Lightbulb, label: '营销策略', route: '/strategy', desc: '周期规划' },
    ],
  },
  {
    label: 'AI 内容发布',
    roles: ['admin', 'manager', 'marketing'],
    items: [
      { icon: WandSparkles, label: 'AI 创作中心', route: '/create', desc: '统一生成入口', primary: true },
      { icon: Instagram, label: '小红书', route: '/xhs', desc: '图文种草' },
      { icon: MessageCircleHeart, label: '朋友圈', route: '/wechat', desc: '日常发布' },
      { icon: Newspaper, label: '公众号', route: '/article', desc: '长文推送' },
      { icon: Image, label: '营销海报', route: '/poster', desc: '图片物料' },
      { icon: Video, label: '视频口播', route: '/video', desc: '脚本分镜' },
    ],
  },
  {
    label: '前台客服',
    roles: ['admin', 'manager', 'front_desk'],
    items: [
      { icon: Star, label: '好评引导', route: '/review', desc: '评价模板' },
      { icon: MessageSquareText, label: '点评回复', route: '/reply', desc: '平台回复' },
    ],
  },
]

const sections = computed(() => {
  const role = auth.role
  if (!role) return []
  return allSections
    .filter(section => section.roles.includes(role))
    .map(section => ({
      label: section.label,
      items: section.items.filter(item => auth.canAccess(item.route)),
    }))
    .filter(section => section.items.length > 0)
})

const mobileItems = computed(() => {
  const preferredRoutes = ['/dashboard', '/create', '/brain', '/history', '/setup']
  const items = sections.value.flatMap(section => section.items)
  return preferredRoutes
    .map(path => items.find(item => item.route === path))
    .filter((item): item is NavItem => Boolean(item))
})

watch(collapsed, (value) => {
  localStorage.setItem('sideNavCollapsed', value ? '1' : '0')
})

function isActive(item: NavItem): boolean {
  if (item.route === '/create') return route.path === '/create'
  if (item.route === '/setup') return route.path.startsWith('/setup')
  if (item.route === '/strategy') return route.path.startsWith('/strategy')
  return route.path === item.route
}

function navigate(item: NavItem) {
  router.push(item.route)
}

function toggleCollapsed() {
  if (collapsed.value) {
    collapsed.value = false
    showLabels.value = true
    return
  }
  showLabels.value = false
  requestAnimationFrame(() => {
    collapsed.value = true
  })
}
</script>

<template>
  <aside
    class="hidden flex-shrink-0 flex-col overflow-hidden border-r border-cream-300/70 bg-white/92 py-3 shadow-[8px_0_24px_rgba(62,53,40,0.04)] backdrop-blur-xl transition-[width] duration-300 ease-out lg:flex"
    :class="collapsed ? 'w-[72px] px-0' : 'w-[220px] px-2.5'"
  >
    <div
      class="overflow-hidden"
      :class="collapsed ? 'mx-auto mb-2 mt-0.5 flex h-11 w-11 items-center justify-center rounded-xl border border-bamboo-100 bg-bamboo-100 text-bamboo-900 shadow-none' : 'mb-2 rounded-2xl border border-bamboo-100 bg-bamboo-950 p-3 text-bamboo-50 shadow-sm'"
    >
      <div class="flex items-center gap-3" :class="showLabels ? '' : 'w-full justify-center gap-0'">
        <div
          class="flex flex-shrink-0 items-center justify-center rounded-xl"
          :class="showLabels ? 'h-10 w-10 bg-bamboo-100 text-bamboo-900' : 'h-11 w-11 bg-transparent text-bamboo-900'"
        >
          <Brain :class="showLabels ? 'h-5 w-5' : 'h-[22px] w-[22px]'" />
        </div>
        <div
          class="min-w-0 overflow-hidden whitespace-nowrap transition-opacity duration-150"
          :class="showLabels ? 'opacity-100 delay-100' : 'pointer-events-none w-0 opacity-0 delay-0'"
        >
          <div class="truncate text-sm font-semibold">宿识家 AI</div>
          <div class="text-[12px] text-bamboo-100/70">酒店运营工作台</div>
        </div>
      </div>
    </div>

    <button
      class="mb-2 flex items-center justify-center gap-2 rounded-xl border border-cream-300 bg-white text-[11px] font-semibold text-warm-600 transition-colors hover:border-bamboo-300 hover:bg-white hover:text-bamboo-800"
      :class="showLabels ? 'h-8 w-full bg-cream-50' : 'mx-auto h-10 w-10'"
      :title="collapsed ? '展开导航' : '收起导航'"
      @click="toggleCollapsed"
    >
      <component :is="collapsed ? ChevronRight : ChevronLeft" class="h-4 w-4" />
      <span
        class="overflow-hidden whitespace-nowrap transition-opacity duration-150"
        :class="showLabels ? 'opacity-100 delay-100' : 'w-0 opacity-0 delay-0'"
      >
        收起导航
      </span>
    </button>

    <div class="flex-1 overflow-y-auto pr-0.5">
      <template v-for="(section, sectionIndex) in sections" :key="section.label">
        <div
          class="overflow-hidden whitespace-nowrap px-2 pb-1 pt-1.5 text-[11px] font-semibold tracking-wide text-warm-500 transition-opacity duration-150 first:pt-0"
          :class="showLabels ? 'opacity-100 delay-100' : 'h-0 py-0 opacity-0 delay-0'"
        >
          {{ section.label }}
        </div>
        <div v-if="!showLabels" class="mx-auto my-1.5 h-px w-8 bg-cream-300 first:hidden" />
        <button
          v-for="item in section.items"
          :key="item.route"
          :title="showLabels ? undefined : item.label"
          :class="[
            'group mb-0.5 flex items-center gap-2 rounded-xl text-left transition-colors duration-150',
            showLabels ? 'w-full px-2.5 py-1.5' : 'mx-auto h-10 w-10 justify-center px-0 py-0',
            isActive(item)
              ? 'bg-bamboo-100 text-bamboo-950 shadow-sm'
              : item.primary
                ? 'bg-amber-50 text-bamboo-900 hover:bg-amber-100'
                : 'text-warm-600 hover:bg-cream-100 hover:text-bamboo-800'
          ]"
          @click="navigate(item)"
        >
          <div
            class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg transition-colors"
            :class="isActive(item) ? 'bg-bamboo-800 text-bamboo-50' : item.primary ? 'bg-amber-100 text-amber-700' : showLabels ? 'bg-white text-warm-500 group-hover:text-bamboo-800' : 'bg-white text-warm-500 group-hover:text-bamboo-800'"
          >
            <component :is="item.icon" class="h-4 w-4" />
          </div>
          <span
            class="min-w-0 flex-1 overflow-hidden whitespace-nowrap transition-opacity duration-150"
            :class="showLabels ? 'opacity-100 delay-100' : 'w-0 opacity-0 delay-0'"
          >
            <span class="block truncate text-[13px] font-semibold">{{ item.label }}</span>
          </span>
          <span
            v-if="item.dot"
            class="h-2 w-2 flex-shrink-0 rounded-full bg-rose-400 transition-opacity duration-150"
            :class="showLabels ? 'opacity-100 delay-100' : 'opacity-0 delay-0'"
          />
        </button>
        <div v-if="sectionIndex < sections.length - 1 && showLabels" class="mx-2 my-1.5 border-b border-cream-300/90" />
      </template>
    </div>

    <button
      class="mt-2 flex items-center gap-2 rounded-xl border border-cream-300 bg-white px-2 py-2 text-left transition-colors hover:border-bamboo-300 hover:bg-white"
      :class="showLabels ? 'w-full bg-cream-50' : 'mx-auto h-10 w-10 justify-center px-0 py-0'"
      :title="showLabels ? undefined : '算力中心'"
      @click="router.push('/credits')"
    >
      <Coins class="h-4 w-4 text-amber-600" />
      <span
        class="overflow-hidden whitespace-nowrap transition-opacity duration-150"
        :class="showLabels ? 'opacity-100 delay-100' : 'w-0 opacity-0 delay-0'"
      >
        <span class="block text-[13px] font-semibold text-bamboo-950">算力中心</span>
      </span>
    </button>
  </aside>

  <nav class="fixed bottom-0 left-0 right-0 z-50 flex justify-around border-t border-cream-300/60 bg-white px-2 py-1.5 shadow-[0_-8px_24px_rgba(62,53,40,0.06)] lg:hidden">
    <button
      v-for="item in mobileItems"
      :key="item.route"
      :class="[
        'flex min-w-0 flex-1 flex-col items-center gap-0.5 rounded-xl px-1 py-1 text-[10px] transition-colors',
        isActive(item) ? 'text-bamboo-800' : 'text-warm-600'
      ]"
      @click="navigate(item)"
    >
      <component :is="item.icon" class="h-4 w-4" />
      {{ item.label }}
    </button>
  </nav>
</template>
