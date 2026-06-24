<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlanStore } from '@/stores/plan'
import { Lightbulb, Flame, Megaphone, PartyPopper, Sun, ArrowUpRight, Calendar, FileText, Plus } from 'lucide-vue-next'

const router = useRouter()
const planStore = usePlanStore()

onMounted(() => {
  planStore.loadFromApi()
})
</script>

<template>
  <div class="max-w-4xl mx-auto p-6 space-y-6">
    <!-- Title + actions -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <Lightbulb class="w-5 h-5 text-bamboo-700" />
        <h1 class="text-lg font-semibold text-bamboo-900">周期营销策略</h1>
      </div>
      <div class="flex items-center gap-2">
        <button @click="router.push('/plans')" class="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-cream-300 text-warm-700 bg-white rounded-lg hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors">
          <FileText class="w-3.5 h-3.5" />
          营销方案记录
        </button>
        <button @click="router.push('/plan')" class="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-bamboo-800 text-bamboo-100 rounded-lg hover:bg-bamboo-900 transition-colors">
          <Plus class="w-3.5 h-3.5" />
          新建方案
        </button>
      </div>
    </div>

    <!-- Main Card -->
    <div class="card space-y-6">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <h2 class="text-base font-semibold text-bamboo-800">周期营销策略</h2>
        </div>
        <span class="badge badge-amber">
          <Calendar class="w-3 h-3" />
          距端午3天
        </span>
      </div>

      <!-- Timeline -->
      <div class="relative pl-8 space-y-0">
        <!-- Item 1 -->
        <div class="relative pb-8">
          <!-- Line -->
          <div class="absolute left-[-17px] top-8 bottom-0 w-px bg-bamboo-200" />
          <!-- Icon -->
          <div class="absolute left-[-29px] top-0 w-6 h-6 rounded-full bg-rose-50 flex items-center justify-center">
            <Flame class="w-3.5 h-3.5 text-rose-400" />
          </div>
          <!-- Content -->
          <div>
            <h3 class="text-sm font-bold text-bamboo-800">今日–明日 · 蓄水种草期</h3>
            <p class="text-sm text-warm-600 mt-1.5 leading-relaxed">
              主推「端午连住早鸟价」，内容以雨天竹林+宋风情绪种草为主，通过朋友圈+小红书双渠道分发，引导用户提前锁定端午房源。
            </p>
          </div>
        </div>

        <!-- Item 2 -->
        <div class="relative pb-8">
          <!-- Line -->
          <div class="absolute left-[-17px] top-8 bottom-0 w-px bg-bamboo-200" />
          <!-- Icon -->
          <div class="absolute left-[-29px] top-0 w-6 h-6 rounded-full bg-amber-50 flex items-center justify-center">
            <Megaphone class="w-3.5 h-3.5 text-warm-400" />
          </div>
          <!-- Content -->
          <div>
            <h3 class="text-sm font-bold text-bamboo-800">后天 · 最后冲刺期</h3>
            <p class="text-sm text-warm-600 mt-1.5 leading-relaxed">
              限时快闪特价，朋友圈+小红书同步爆发，制造紧迫感促转化。配合倒计时海报与「仅剩X间」话术，冲刺满房目标。
            </p>
          </div>
        </div>

        <!-- Item 3 -->
        <div class="relative pb-8">
          <!-- Line -->
          <div class="absolute left-[-17px] top-8 bottom-0 w-px bg-bamboo-200" />
          <!-- Icon -->
          <div class="absolute left-[-29px] top-0 w-6 h-6 rounded-full bg-purple-50 flex items-center justify-center">
            <PartyPopper class="w-3.5 h-3.5 text-purple-600" />
          </div>
          <!-- Content -->
          <div>
            <h3 class="text-sm font-bold text-bamboo-800">端午3天 · 服务口碑期</h3>
            <p class="text-sm text-warm-600 mt-1.5 leading-relaxed">
              在住客极致服务+好评引导，沉淀爆款UGC内容。趁着客人在店体验感最强的时间窗口，引导小红书/朋友圈自发传播，积累真实口碑素材。
            </p>
          </div>
        </div>

        <!-- Item 4 -->
        <div class="relative">
          <!-- Icon -->
          <div class="absolute left-[-29px] top-0 w-6 h-6 rounded-full bg-blue-50 flex items-center justify-center">
            <Sun class="w-3.5 h-3.5 text-blue-600" />
          </div>
          <!-- Content -->
          <div>
            <h3 class="text-sm font-bold text-bamboo-800">端午后 · 暑假蓄水期</h3>
            <p class="text-sm text-warm-600 mt-1.5 leading-relaxed">
              以端午好评内容为素材，开启暑假亲子向内容矩阵。提前锁定暑期亲子客群，将端午口碑转化为暑期预订转化力。
            </p>
          </div>
        </div>
      </div>

      <!-- Bottom Button -->
      <div class="flex justify-center pt-2">
        <button class="btn-primary" @click="$router.push('/plans')">
          AI生成完整节日方案
          <ArrowUpRight class="w-4 h-4" />
        </button>
      </div>
    </div>
  </div>
</template>
