<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Hotel, Smartphone, Lock, Loader2, Eye, EyeOff, Sparkles } from 'lucide-vue-next'

const router = useRouter()
const auth = useAuthStore()

const mode = ref<'phone' | 'password'>('phone')
const phone = ref('')
const code = ref('')
const passwordPhone = ref('')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const codeSending = ref(false)
const codeCountdown = ref(0)
const errorMsg = ref('')
const toast = ref('')
let countdownTimer: ReturnType<typeof setInterval> | null = null

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const passwordPhoneValid = computed(() => /^1[3-9]\d{9}$/.test(passwordPhone.value))

async function sendCode() {
  if (!phoneValid.value || codeSending.value || codeCountdown.value > 0) return
  codeSending.value = true
  errorMsg.value = ''
  try {
    await auth.sendSms(phone.value)
    flashToast('验证码已发送')
    codeCountdown.value = 60
    countdownTimer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0 && countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || '验证码发送失败，请稍后重试'
  } finally {
    codeSending.value = false
  }
}

async function login() {
  errorMsg.value = ''
  if (mode.value === 'phone') {
    if (!phoneValid.value) { errorMsg.value = '请输入正确的手机号'; return }
    if (!code.value.trim()) { errorMsg.value = '请输入验证码'; return }
  } else {
    if (!passwordPhoneValid.value) { errorMsg.value = '请输入正确的手机号'; return }
    if (!password.value.trim()) { errorMsg.value = '请输入密码'; return }
  }

  loading.value = true
  try {
    if (mode.value === 'phone') {
      await auth.loginByPhone(phone.value, code.value)
    } else {
      await auth.loginByPassword(passwordPhone.value, password.value)
    }
    router.replace('/dashboard')
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || '登录失败，请检查账号信息'
  } finally {
    loading.value = false
  }
}

function switchMode(nextMode: 'phone' | 'password') {
  mode.value = nextMode
  errorMsg.value = ''
}

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 2000)
}
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-bamboo-50 via-cream-100 to-cream-50 flex items-center justify-center p-5">
    <div v-if="toast" class="fixed top-6 right-6 z-50 bg-bamboo-800 text-white px-5 py-3 rounded-lg shadow-lg text-sm">
      {{ toast }}
    </div>

    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-bamboo-800 shadow-lg shadow-bamboo-200 mb-4">
          <Hotel class="w-8 h-8 text-cream-100" />
        </div>
        <h1 class="text-xl font-semibold text-bamboo-900">宿识家 AI</h1>
        <p class="text-sm text-warm-600 mt-1.5">登录你的酒店运营工作台</p>
      </div>

      <div class="bg-white rounded-2xl shadow-sm border border-cream-200 p-6 space-y-5">
        <div class="flex bg-cream-100 rounded-lg p-1 gap-1">
          <button
            @click="switchMode('phone')"
            :class="[
              'flex-1 py-2 rounded-md text-sm font-medium transition-colors flex items-center justify-center gap-1.5',
              mode === 'phone' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-bamboo-700'
            ]"
          >
            <Smartphone class="w-4 h-4" />短信登录
          </button>
          <button
            @click="switchMode('password')"
            :class="[
              'flex-1 py-2 rounded-md text-sm font-medium transition-colors flex items-center justify-center gap-1.5',
              mode === 'password' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-bamboo-700'
            ]"
          >
            <Lock class="w-4 h-4" />密码登录
          </button>
        </div>

        <div v-if="mode === 'phone'" class="space-y-4">
          <div>
            <label class="text-xs font-medium text-warm-700 mb-1.5 block">手机号</label>
            <div class="relative">
              <Smartphone class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-warm-500" />
              <input
                v-model="phone"
                type="tel"
                maxlength="11"
                placeholder="请输入酒店员工手机号"
                class="w-full pl-9 pr-4 py-2.5 text-sm border border-cream-300 rounded-lg bg-cream-50 text-bamboo-950 placeholder:text-warm-400 focus:outline-none focus:border-bamboo-400 focus:bg-white transition-colors"
              />
            </div>
          </div>
          <div>
            <label class="text-xs font-medium text-warm-700 mb-1.5 block">验证码</label>
            <div class="flex gap-2">
              <div class="relative flex-1">
                <Lock class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-warm-500" />
                <input
                  v-model="code"
                  type="text"
                  maxlength="6"
                  placeholder="请输入验证码"
                  class="w-full pl-9 pr-4 py-2.5 text-sm border border-cream-300 rounded-lg bg-cream-50 text-bamboo-950 placeholder:text-warm-400 focus:outline-none focus:border-bamboo-400 focus:bg-white transition-colors"
                  @keyup.enter="login"
                />
              </div>
              <button
                @click="sendCode"
                :disabled="!phoneValid || codeCountdown > 0 || codeSending"
                class="px-4 py-2.5 rounded-lg text-sm font-medium whitespace-nowrap transition-colors border disabled:cursor-not-allowed"
                :class="phoneValid && codeCountdown === 0 && !codeSending
                  ? 'bg-bamboo-800 text-white border-bamboo-800 hover:bg-bamboo-900'
                  : 'bg-cream-100 text-warm-500 border-cream-300'"
              >
                <span v-if="codeSending"><Loader2 class="w-4 h-4 animate-spin inline" /></span>
                <span v-else-if="codeCountdown > 0">{{ codeCountdown }}s</span>
                <span v-else>获取验证码</span>
              </button>
            </div>
          </div>
        </div>

        <div v-else class="space-y-4">
          <div>
            <label class="text-xs font-medium text-warm-700 mb-1.5 block">手机号</label>
            <div class="relative">
              <Smartphone class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-warm-500" />
              <input
                v-model="passwordPhone"
                type="tel"
                maxlength="11"
                placeholder="请输入酒店员工手机号"
                class="w-full pl-9 pr-4 py-2.5 text-sm border border-cream-300 rounded-lg bg-cream-50 text-bamboo-950 placeholder:text-warm-400 focus:outline-none focus:border-bamboo-400 focus:bg-white transition-colors"
              />
            </div>
          </div>
          <div>
            <label class="text-xs font-medium text-warm-700 mb-1.5 block">密码</label>
            <div class="relative">
              <Lock class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-warm-500" />
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
                class="w-full pl-9 pr-10 py-2.5 text-sm border border-cream-300 rounded-lg bg-cream-50 text-bamboo-950 placeholder:text-warm-400 focus:outline-none focus:border-bamboo-400 focus:bg-white transition-colors"
                @keyup.enter="login"
              />
              <button @click="showPassword = !showPassword" class="absolute right-3 top-1/2 -translate-y-1/2 text-warm-500 hover:text-bamboo-700">
                <EyeOff v-if="showPassword" class="w-4 h-4" />
                <Eye v-else class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        <div v-if="errorMsg" class="text-xs text-rose-500 bg-rose-50 rounded-lg px-3 py-2">
          {{ errorMsg }}
        </div>

        <button
          @click="login"
          :disabled="loading"
          class="w-full py-2.5 rounded-lg bg-bamboo-800 text-white text-sm font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors"
        >
          <Loader2 v-if="loading" class="w-4 h-4 animate-spin" />
          <Sparkles v-else class="w-4 h-4" />
          {{ loading ? '登录中...' : '登录' }}
        </button>

        <p class="text-center text-xs text-warm-500">
          账号由酒店管理员或平台管理端创建，并自动绑定所属酒店。
        </p>
      </div>
    </div>
  </div>
</template>
