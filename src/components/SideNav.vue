<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  Settings, Home, LayoutDashboard, Coins, Lightbulb, FileText,
  MessageCircleHeart, Instagram, Image, Video, Newspaper,
  Star, MessageSquareText, Users, Brain
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

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
}

const allSections: NavSection[] = [
  {
    label: '初始配置',
    roles: ['admin'],
    items: [
      { icon: Settings, label: '酒店基础信息', route: '/setup' },
      { icon: Home, label: '房型与定价', route: '/rooms' },
    ],
  },
  {
    label: '店长看板',
    roles: ['admin', 'manager'],
    items: [
      { icon: LayoutDashboard, label: '数字营销大盘', route: '/dashboard' },
      { icon: Coins, label: '智能定价', route: '/pricing' },
      { icon: Lightbulb, label: '周期营销策略', route: '/strategy' },
      { icon: Brain, label: '运营智慧大脑', route: '/brain', dot: true },
    ],
  },
  {
    label: '内容发布',
    roles: ['admin', 'manager', 'marketing'],
    items: [
      { icon: MessageCircleHeart, label: '朋友圈文案', route: '/wechat' },
      { icon: Instagram, label: '小红书营销', route: '/xhs', dot: true },
      { icon: Image, label: '营销海报', route: '/poster' },
      { icon: Video, label: '视频口播', route: '/video' },
      { icon: Newspaper, label: '公众号推文', route: '/article' },
    ],
  },
  {
    label: '前台客服',
    roles: ['admin', 'manager', 'front_desk'],
    items: [
      { icon: Star, label: '好评引导', route: '/review' },
      { icon: MessageSquareText, label: '回评话术', route: '/reply' },
      { icon: Users, label: '在住客管理', route: '/checkin' },
    ],
  },
]

// 根据角色过滤菜单
const sections = computed(() => {
  const role = auth.role
  if (!role) return []
  return allSections
    .filter(s => s.roles.includes(role))
    .map(s => ({
      label: s.label,
      items: s.items.filter(item => auth.canAccess(item.route)),
    }))
    .filter(s => s.items.length > 0)
})

function isActive(item: NavItem): boolean {
  return route.path === item.route
}

function navigate(item: NavItem) {
  router.push(item.route)
}
</script>

<template>
  <aside class="w-52 flex-shrink-0 border-r border-cream-300/60 bg-white/60 flex flex-col py-3 gap-0.5 overflow-y-auto hidden lg:flex">
    <template v-for="(section, i) in sections" :key="i">
      <div class="px-5 pt-3 pb-1 text-[10px] font-semibold text-warm-600/70 uppercase tracking-widest">
        {{ section.label }}
      </div>
      <button
        v-for="item in section.items"
        :key="item.route"
        @click="navigate(item)"
        :class="[
          'flex items-center gap-3 px-5 py-2 text-xs w-full text-left transition-all duration-150 relative',
          isActive(item)
            ? 'bg-bamboo-50 text-bamboo-800 font-medium'
            : 'text-warm-600 hover:bg-cream-100 hover:text-bamboo-700'
        ]"
      >
        <span v-if="isActive(item)" class="absolute left-0 top-1 bottom-1 w-0.5 bg-bamboo-700 rounded-r-full" />
        <component :is="item.icon" class="w-4 h-4 flex-shrink-0" />
        <span class="flex-1 text-left truncate">{{ item.label }}</span>
        <span v-if="item.dot" class="w-1.5 h-1.5 rounded-full bg-rose-400 flex-shrink-0" />
      </button>
      <div v-if="i < sections.length - 1" class="mx-4 my-2 border-b border-cream-200/60" />
    </template>
  </aside>

  <!-- Mobile bottom nav -->
  <nav class="lg:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-cream-300/60 flex justify-around py-1.5 z-50">
    <button
      v-for="item in sections.flatMap(s => s.items).slice(0, 6)"
      :key="item.route"
      @click="navigate(item)"
      :class="[
        'flex flex-col items-center gap-0.5 px-1 py-1 text-[10px] transition-colors',
        isActive(item) ? 'text-bamboo-800' : 'text-warm-600'
      ]"
    >
      <component :is="item.icon" class="w-4 h-4" />
      {{ item.label }}
    </button>
  </nav>
</template>
