<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHotelStore } from '@/stores/hotel'
import { useAuthStore } from '@/stores/auth'
import { useAiJobsStore } from '@/stores/aiJobs'
import { safeUiText } from '@/utils/uiText'
import {
  BedDouble,
  Bot,
  BookOpenCheck,
  CalendarDays,
  ChevronDown,
  CheckCircle2,
  CloudSun,
  Coins,
  History,
  Hotel,
  Image,
  Instagram,
  LayoutDashboard,
  Lightbulb,
  LogOut,
  ListChecks,
  Loader2,
  MessageCircleHeart,
  MessageSquareText,
  Newspaper,
  Settings,
  Star,
  Video,
  WandSparkles,
  X,
} from 'lucide-vue-next'

const store = useHotelStore()
const auth = useAuthStore()
const aiJobs = useAiJobsStore()
const router = useRouter()
const route = useRoute()

const displayName = computed(() => {
  const name = safeUiText(store.config.name, '未设置酒店')
  const city = safeUiText(store.config.city, '未设置城市')
  return `${name} / ${city}`
})

const pageTitle = computed(() => {
  const map: Record<string, string> = {
    '/dashboard': '今日工作台',
    '/create': 'AI 创作中心',
    '/knowledge': '酒店资料中心',
    '/history': '内容历史',
    '/brain': 'AI 店长',
    '/xhs': '小红书创作',
    '/wechat': '朋友圈文案',
    '/article': '公众号推文',
    '/poster': '营销海报',
    '/video': '视频口播',
    '/pricing': '智能定价',
    '/pricing/result': '智能定价方案',
    '/strategy': '周期营销策略',
    '/strategy/result': '营销策略方案',
    '/setup': '基础信息',
    '/setup/occupancy-history': '房态导入',
    '/rooms': '房型定价',
    '/review': '好评引导',
    '/reply': '点评回复',
    '/credits': '算力中心',
    '/profile': '账号设置',
  }
  if (route.path.startsWith('/history/')) return '生成记录详情'
  return map[route.path] || '宿识家工作台'
})

const pageIcon = computed(() => {
  const map: Record<string, any> = {
    '/dashboard': LayoutDashboard,
    '/create': WandSparkles,
    '/knowledge': BookOpenCheck,
    '/history': History,
    '/brain': Bot,
    '/xhs': Instagram,
    '/wechat': MessageCircleHeart,
    '/article': Newspaper,
    '/poster': Image,
    '/video': Video,
    '/pricing': Coins,
    '/pricing/result': Coins,
    '/strategy': Lightbulb,
    '/strategy/result': Lightbulb,
    '/setup': Settings,
    '/rooms': BedDouble,
    '/review': Star,
    '/reply': MessageSquareText,
    '/credits': Coins,
    '/profile': Settings,
  }
  if (route.path.startsWith('/history/')) return History
  return map[route.path] || Hotel
})

const todayInfo = computed(() => {
  const now = new Date()
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(now)
})

const weatherInfo = computed(() => store.weather)
const weatherLabel = computed(() => {
  const weather = weatherInfo.value
  if (!weather || (!weather.weather && !weather.temperature)) return ''
  const city = safeUiText(weather.city || store.config.poiCity || store.config.city, '')
  const weatherText = safeUiText(weather.weather, '')
  const temp = weather.temperature ? `${weather.temperature}℃` : ''
  return [city, weatherText, temp].filter(Boolean).join(' ')
})

function loadWeatherQuietly() {
  store.fetchWeather().catch(() => {})
}

onMounted(() => {
  loadWeatherQuietly()
  aiJobs.startPolling()
  document.addEventListener('click', clickOutside)
})

onUnmounted(() => {
  aiJobs.stopPolling()
  document.removeEventListener('click', clickOutside)
})

watch(
  () => [store.config.poiAdcode, store.config.poiCity, store.config.city],
  () => loadWeatherQuietly()
)

const showDropdown = ref(false)

function toggleDropdown() {
  showDropdown.value = !showDropdown.value
}

function closeDropdown() {
  showDropdown.value = false
}

function goProfile() {
  closeDropdown()
  router.push('/profile')
}

function handleLogout() {
  closeDropdown()
  auth.logout()
  router.push('/login')
}

function getInitials(name: string): string {
  return name ? name.slice(0, 1) : '店'
}

function clickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.user-dropdown')) closeDropdown()
  if (!target.closest('.ai-job-panel')) aiJobs.showPanel = false
}
</script>

<template>
  <header
    class="relative z-40 flex min-h-[62px] flex-shrink-0 items-center justify-between gap-3 border-b border-cream-300/70 bg-white/90 px-4 backdrop-blur-xl lg:px-6"
  >
    <div class="flex min-w-0 items-center gap-3">
      <div class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl bg-bamboo-900 shadow-sm">
        <component :is="pageIcon" class="h-4.5 w-4.5 text-bamboo-100" />
      </div>
      <div class="min-w-0">
        <div class="flex items-center gap-2">
          <h1 class="truncate text-base font-semibold text-bamboo-950 lg:text-lg">{{ pageTitle }}</h1>
          <span class="hidden rounded-full bg-bamboo-100 px-2 py-0.5 text-[11px] font-semibold text-bamboo-800 md:inline-flex">演示版</span>
        </div>
        <div class="mt-0.5 flex min-w-0 items-center gap-2 text-xs text-warm-600">
          <span class="truncate">{{ displayName }}</span>
        </div>
      </div>
    </div>

    <div class="flex min-w-0 items-center gap-2">
      <div class="hidden max-w-[220px] items-center gap-1.5 truncate rounded-xl bg-cream-100 px-3 py-2 text-xs text-warm-600 xl:flex">
        <CloudSun class="h-3.5 w-3.5" />
        <span v-if="weatherLabel">{{ weatherLabel }}</span>
        <span v-else>{{ store.weatherLoading ? '天气读取中' : '当地天气' }}</span>
      </div>

      <div class="hidden items-center gap-1.5 rounded-xl bg-cream-100 px-3 py-2 text-xs text-warm-600 2xl:flex">
        <CalendarDays class="h-3.5 w-3.5" />
        {{ todayInfo }}
      </div>

      <div class="ai-job-panel relative">
        <button
          class="relative flex items-center gap-1.5 rounded-xl border border-cream-300 bg-white px-3 py-2 text-xs font-medium text-warm-700 transition-colors hover:border-bamboo-300 hover:bg-bamboo-50 hover:text-bamboo-900"
          @click.stop="aiJobs.togglePanel"
        >
          <ListChecks class="h-3.5 w-3.5" />
          <span class="hidden sm:inline">工作列表</span>
          <span
            v-if="aiJobs.runningCount"
            class="ml-0.5 rounded-full bg-bamboo-800 px-1.5 py-0.5 text-[10px] font-bold text-white"
          >
            {{ aiJobs.runningCount }}
          </span>
        </button>

        <div
          v-if="aiJobs.toastJob"
          class="absolute right-0 top-full z-40 mt-2 w-72 rounded-xl border border-bamboo-200 bg-white p-3 shadow-xl"
        >
          <div class="flex items-start gap-2">
            <CheckCircle2 class="mt-0.5 h-4 w-4 flex-shrink-0 text-bamboo-700" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-xs font-semibold text-bamboo-950">{{ aiJobs.toastJob.title }}</p>
              <p class="mt-0.5 text-[11px] text-warm-500">生成完成，可以查看结果</p>
            </div>
            <button class="rounded-lg bg-bamboo-800 px-2 py-1 text-[11px] text-white" @click.stop="aiJobs.openJob(aiJobs.toastJob)">查看</button>
          </div>
        </div>

        <div
          v-if="aiJobs.showPanel"
          class="absolute right-0 top-full z-30 mt-2 w-80 overflow-hidden rounded-xl border border-cream-200 bg-white shadow-xl"
        >
          <div class="flex items-center justify-between gap-3 border-b border-cream-100 px-4 py-3">
            <div>
              <p class="text-xs font-semibold text-bamboo-950">AI 工作列表</p>
              <p class="text-[11px] text-warm-500">{{ aiJobs.runningCount }} 个正在生成</p>
            </div>
            <button class="text-[11px] text-bamboo-700" @click.stop="aiJobs.refresh">刷新</button>
          </div>
          <div class="max-h-96 overflow-y-auto p-2">
            <div v-if="!aiJobs.jobs.length" class="px-3 py-8 text-center text-xs text-warm-500">暂无生成任务</div>
            <button
              v-for="job in aiJobs.jobs.slice(0, 12)"
              :key="job.id"
              :disabled="!job.generationId"
              class="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-cream-50 disabled:cursor-default disabled:hover:bg-transparent"
              @click.stop="aiJobs.openJob(job)"
            >
              <span class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-bamboo-50 text-bamboo-800">
                <Loader2 v-if="['pending','processing','running'].includes(job.status)" class="h-4 w-4 animate-spin" />
                <CheckCircle2 v-else-if="job.status !== 'failed'" class="h-4 w-4" />
                <History v-else class="h-4 w-4 text-red-500" />
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-xs font-medium text-bamboo-950">{{ job.title }}</span>
                <span class="mt-0.5 block text-[11px] text-warm-500">
                  {{ ['pending','processing','running'].includes(job.status) ? '正在生成' : job.status === 'failed' ? '生成失败' : '已完成' }}
                </span>
              </span>
              <span
                class="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg text-warm-400 hover:bg-cream-100 hover:text-red-500"
                title="删除这条"
                @click.stop="aiJobs.removeJob(job.id)"
              >
                <X class="h-3.5 w-3.5" />
              </span>
            </button>
          </div>
        </div>
      </div>

      <div class="user-dropdown relative">
        <button
          class="flex items-center gap-2 rounded-xl border border-transparent px-2 py-1.5 transition-colors hover:border-cream-300 hover:bg-cream-50"
          @click.stop="toggleDropdown"
        >
          <div class="flex h-8 w-8 items-center justify-center rounded-full bg-bamboo-800 text-[11px] font-bold text-white">
            {{ getInitials(auth.user?.name || '') }}
          </div>
          <span class="hidden text-xs font-medium text-warm-700 sm:block">{{ auth.user?.name || '酒店账号' }}</span>
          <ChevronDown class="h-3 w-3 text-warm-500" />
        </button>

        <div v-if="showDropdown" class="absolute right-0 top-full z-20 mt-2 w-52 overflow-hidden rounded-xl border border-cream-200 bg-white shadow-xl">
          <div class="border-b border-cream-100 px-4 py-3">
            <div class="text-xs font-medium text-bamboo-950">{{ auth.user?.name || '酒店账号' }}</div>
            <div class="text-[11px] text-warm-500">{{ auth.roleName || '未识别角色' }}</div>
          </div>
          <button class="flex w-full items-center gap-2.5 px-4 py-2.5 text-xs text-warm-600 transition-colors hover:bg-cream-50" @click="goProfile">
            <Settings class="h-3.5 w-3.5" />
            账号设置
          </button>
          <button class="flex w-full items-center gap-2.5 px-4 py-2.5 text-xs text-red-500 transition-colors hover:bg-red-50" @click="handleLogout">
            <LogOut class="h-3.5 w-3.5" />
            退出登录
          </button>
        </div>
      </div>
    </div>
  </header>
</template>
