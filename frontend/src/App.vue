<script setup lang="ts">
import TopBar from '@/components/TopBar.vue'
import SideNav from '@/components/SideNav.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const isLogin = computed(() => route.path === '/login')
const isAdmin = computed(() => route.path.startsWith('/admin'))
const contentRoutes = ['/create', '/xhs', '/wechat', '/poster', '/video', '/article', '/review', '/reply']
const isContentRoute = computed(() => contentRoutes.some((path) => route.path.startsWith(path)))
const isFixedHeightRoute = computed(() => route.name === 'generationDetail')
</script>

<template>
  <!-- 登录页：无布局 -->
  <div v-if="isLogin" class="h-screen overflow-hidden">
    <router-view v-slot="{ Component, route }">
      <transition name="route-fade" mode="out-in">
        <component :is="Component" :key="route.fullPath" />
      </transition>
    </router-view>
  </div>

  <!-- 管理后台：全屏独立布局 -->
  <div v-else-if="isAdmin" class="h-screen overflow-hidden bg-gray-950">
    <router-view />
  </div>

  <!-- 酒店管理端：正常布局 -->
  <div v-else class="h-screen flex overflow-hidden bg-[#f6f3ec] text-bamboo-900">
    <div class="flex flex-1 min-w-0">
      <SideNav />
      <div class="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main
          class="hotel-workspace-shell min-h-0 flex-1 px-3 py-3 pb-20 sm:px-4 lg:px-5 lg:py-4 lg:pb-4"
          :class="[
            isFixedHeightRoute ? 'overflow-hidden' : 'overflow-auto',
            { 'content-studio-shell': isContentRoute }
          ]"
        >
          <router-view v-slot="{ Component, route }">
            <transition name="route-panel" mode="out-in">
              <component :is="Component" :key="route.fullPath" />
            </transition>
          </router-view>
        </main>
      </div>
    </div>
  </div>
</template>

<style scoped>
.route-fade-enter-active,
.route-fade-leave-active,
.route-panel-enter-active,
.route-panel-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.route-fade-enter-from,
.route-fade-leave-to {
  opacity: 0;
}

.route-panel-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.route-panel-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
