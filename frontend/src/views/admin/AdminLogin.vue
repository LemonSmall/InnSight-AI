<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { Eye, EyeOff, Loader2, Lock, Shield } from 'lucide-vue-next'

const router = useRouter()
const adminAuth = useAdminAuthStore()

const email = ref('admin@sushijia.ai')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const errorMsg = ref('')

async function login() {
  errorMsg.value = ''
  if (!email.value.trim()) { errorMsg.value = '请输入管理员邮箱'; return }
  if (!password.value.trim()) { errorMsg.value = '请输入密码'; return }

  loading.value = true
  try {
    await adminAuth.login(email.value.trim(), password.value.trim())
    router.push('/admin')
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

function onKeyup(e: KeyboardEvent) {
  if (e.key === 'Enter') login()
}
</script>

<template>
  <div class="min-h-screen bg-gray-950 flex items-center justify-center p-5">
    <div class="w-full max-w-sm">
      <div class="text-center mb-8">
        <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-indigo-600 shadow-lg shadow-indigo-600/20 mb-4">
          <Shield class="w-7 h-7 text-white" />
        </div>
        <h1 class="text-lg font-semibold text-white">宿营家 AI · 管理后台</h1>
        <p class="text-xs text-gray-500 mt-1">Super Admin Console</p>
      </div>

      <div class="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-4">
        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">管理员邮箱</label>
          <input
            v-model="email"
            type="text"
            placeholder="admin@sushijia.ai"
            class="w-full text-sm px-3 py-2.5 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 placeholder:text-gray-600 focus:outline-none focus:border-indigo-500 transition-colors"
          />
        </div>

        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">密码</label>
          <div class="relative">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入管理员密码"
              class="w-full text-sm px-3 py-2.5 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 placeholder:text-gray-600 focus:outline-none focus:border-indigo-500 transition-colors pr-10"
              @keyup="onKeyup"
            />
            <button @click="showPassword = !showPassword" class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-600 hover:text-gray-400">
              <EyeOff v-if="showPassword" class="w-4 h-4" />
              <Eye v-else class="w-4 h-4" />
            </button>
          </div>
        </div>

        <div v-if="errorMsg" class="text-xs text-red-400 bg-red-500/10 rounded-lg px-3 py-2 border border-red-500/20">
          {{ errorMsg }}
        </div>

        <button
          @click="login"
          :disabled="loading"
          class="w-full py-2.5 rounded-lg bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-500 disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
        >
          <Loader2 v-if="loading" class="w-4 h-4 animate-spin" />
          <Lock v-else class="w-4 h-4" />
          {{ loading ? '登录中...' : '登录管理后台' }}
        </button>
      </div>

      <p class="text-center text-[10px] text-gray-700 mt-6">
        &copy; 2026 宿营家 AI · 仅供授权管理员使用
      </p>
    </div>
  </div>
</template>
