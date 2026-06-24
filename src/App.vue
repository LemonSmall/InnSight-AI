<script setup lang="ts">
import TopBar from '@/components/TopBar.vue'
import SideNav from '@/components/SideNav.vue'
import UploadModal from '@/components/UploadModal.vue'
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const showUpload = ref(false)

const isLogin = computed(() => route.path === '/login')
const isAdmin = computed(() => route.path.startsWith('/admin'))
</script>

<template>
  <!-- 登录页：无布局 -->
  <div v-if="isLogin" class="h-screen overflow-hidden">
    <router-view />
  </div>

  <!-- 管理后台：全屏独立布局 -->
  <div v-else-if="isAdmin" class="h-screen overflow-hidden">
    <router-view />
  </div>

  <!-- 酒店管理端：正常布局 -->
  <div v-else class="h-screen flex flex-col overflow-hidden bg-cream-100">
    <TopBar @upload="showUpload = true" />
    <div class="flex flex-1 min-h-0">
      <SideNav />
      <main class="flex-1 overflow-auto p-5 lg:p-6">
        <router-view />
      </main>
    </div>
    <UploadModal v-if="showUpload" @close="showUpload = false" />
  </div>
</template>
