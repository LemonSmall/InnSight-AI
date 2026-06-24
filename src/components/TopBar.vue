<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHotelStore } from '@/stores/hotel'
import { useAuthStore } from '@/stores/auth'
import { Hotel, CloudRain, User, LogOut, Settings, ChevronDown } from 'lucide-vue-next'

defineEmits<{ upload: [] }>()

const store = useHotelStore()
const auth = useAuthStore()
const router = useRouter()

const displayName = computed(() => `${store.config.name} · ${store.config.city}`)

const todayInfo = computed(() => {
  const days = ['日', '一', '二', '三', '四', '五', '六']
  const now = new Date()
  return `小雨 · 周${days[now.getDay()]} · 距端午3天`
})

const showDropdown = ref(false)

function toggleDropdown() {
  showDropdown.value = !showDropdown.value
}

function goProfile() {
  showDropdown.value = false
  router.push('/profile')
}

function handleLogout() {
  showDropdown.value = false
  auth.logout()
  router.push('/login')
}

function getInitials(name: string): string {
  return name ? name.slice(0, 1) : '?'
}

// 点击外部关闭
function clickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.user-dropdown')) {
    showDropdown.value = false
  }
}
</script>

<template>
  <header class="h-14 flex items-center justify-between px-5 border-b border-cream-300/60 bg-white/80 backdrop-blur-sm flex-shrink-0" @click="clickOutside">
    <div class="flex items-center gap-3">
      <div class="w-8 h-8 bg-bamboo-900 rounded-lg flex items-center justify-center">
        <Hotel class="w-4 h-4 text-bamboo-300" />
      </div>
      <div>
        <div class="text-sm font-serif font-semibold text-bamboo-800">宿营家 AI</div>
        <div class="text-xs text-warm-600">{{ displayName }}</div>
      </div>
    </div>
    <div class="flex items-center gap-3">
      <div class="flex items-center gap-1.5 text-xs text-warm-600 bg-cream-200/60 px-3 py-1.5 rounded-full">
        <CloudRain class="w-3.5 h-3.5" />
        {{ todayInfo }}
      </div>
      <button @click="$emit('upload')" class="btn-primary text-xs !py-1.5 !px-3">
        上传房态图
      </button>

      <!-- 用户下拉 -->
      <div class="user-dropdown relative">
        <button @click.stop="toggleDropdown" class="flex items-center gap-2 hover:bg-cream-100 rounded-lg px-2 py-1 transition-colors">
          <div class="w-7 h-7 rounded-full bg-bamboo-800 flex items-center justify-center text-white text-[11px] font-bold">
            {{ getInitials(auth.user?.name || '') }}
          </div>
          <span class="text-xs text-warm-700 hidden sm:block">{{ auth.user?.name }}</span>
          <ChevronDown class="w-3 h-3 text-warm-500" />
        </button>

        <!-- 下拉菜单 -->
        <div v-if="showDropdown" class="absolute right-0 top-full mt-1.5 w-44 bg-white border border-cream-200 rounded-xl shadow-lg z-50 overflow-hidden">
          <div class="px-4 py-3 border-b border-cream-100">
            <div class="text-xs font-medium text-warm-800">{{ auth.user?.name }}</div>
            <div class="text-[10px] text-warm-500">{{ auth.roleName }}</div>
          </div>
          <button @click="goProfile" class="w-full flex items-center gap-2.5 px-4 py-2.5 text-xs text-warm-600 hover:bg-cream-50 transition-colors">
            <Settings class="w-3.5 h-3.5" />个人中心
          </button>
          <button @click="handleLogout" class="w-full flex items-center gap-2.5 px-4 py-2.5 text-xs text-red-500 hover:bg-red-50 transition-colors">
            <LogOut class="w-3.5 h-3.5" />退出登录
          </button>
        </div>
      </div>
    </div>
  </header>
</template>
