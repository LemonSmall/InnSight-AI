<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Nfc, Gift, Copy } from 'lucide-vue-next'
import { generateReview } from '@/api/hotel'

const selectedType = ref<'couple' | 'family' | 'biz' | null>(null)
const selectedIncentive = ref<number | null>(null)
const copied = ref(false)
const loading = ref(false)

const reviews = ref<Record<string, string>>({})
const currentReview = computed(() => {
  return selectedType.value ? reviews.value[selectedType.value] || '' : ''
})

async function selectType(type: 'couple' | 'family' | 'biz') {
  selectedType.value = type
  loading.value = true
  try {
    const { data: res } = await generateReview({ guestType: type })
    const d = res.data || res
    reviews.value[type] = d.review || ''
  } catch {
    // 回退
    const fallback: Record<string, string> = {
      couple: '和男朋友来莫干山过周末，选了这家竹林里的民宿真的选对了！\n\n房间能看到整片竹海，私汤泡完出来听竹叶雨声，整个人都松弛了...',
      family: '带娃来莫干山避暑，住了三晚松间，孩子不肯走了哈哈哈。\n\n竹林里的空气太好了...',
      biz: '出差顺路住了一晚，没想到这么惊喜。环境清幽、隔音好、WiFi稳定，早餐品质远超连锁酒店...',
    }
    reviews.value[type] = fallback[type] || ''
  } finally {
    loading.value = false
  }
}

async function copyReview() {
  if (!currentReview.value) return
  try {
    await navigator.clipboard.writeText(currentReview.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch { /* fallback */ }
}

function selectIncentive(index: number) {
  selectedIncentive.value = selectedIncentive.value === index ? null : index
}

const guestTypes = [
  { type: 'couple' as const, label: '情侣客群 · 小红书引流 · 2晚' },
  { type: 'family' as const, label: '亲子家庭 · 美团引流 · 3晚' },
  { type: 'biz' as const, label: '商务出行 · 携程引流 · 1晚' },
]

const incentives = [
  { title: '离店礼品卡 + 好评返现', desc: '赠茶叶/竹制品，附好评返20元微信截图', badge: '转化率最高', badgeClass: 'badge-rose' },
  { title: '手写欢迎信 + NFC引导', desc: '房间放手写信，附上评价引导卡', badge: '新客转化好', badgeClass: 'badge-green' },
  { title: '微信复购优惠码', desc: '好评后发8.8折码+引导添加店主微信', badge: '沉淀私域', badgeClass: 'badge-purple' },
]
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
    <!-- LEFT CARD: NFC 好评引导 -->
    <div class="card space-y-5">
      <div class="flex items-center gap-2.5">
        <Nfc class="w-5 h-5 text-bamboo-700" />
        <h2 class="text-base font-semibold text-bamboo-800">碰一碰好评引导</h2>
      </div>

      <!-- NFC Card -->
      <div class="bg-cream-100/80 border border-cream-300/60 rounded-xl p-6 flex flex-col items-center gap-3">
        <div class="w-16 h-16 bg-bamboo-800 rounded-full flex items-center justify-center">
          <Nfc class="w-8 h-8 text-cream-100" />
        </div>
        <span class="text-sm font-medium text-bamboo-800">手机靠近即跳转评价</span>
        <span class="text-xs text-warm-600">NFC贴片放前台 / 桌面 / 房卡袋</span>
      </div>

      <!-- Section Label -->
      <div class="label">按客群生成个性化好评</div>

      <!-- Guest Type Cards -->
      <div class="space-y-2">
        <button
          v-for="item in guestTypes"
          :key="item.type"
          @click="selectType(item.type)"
          :class="[
            'w-full text-left p-3 rounded-lg border transition-all duration-150 text-sm',
            selectedType === item.type
              ? 'border-bamboo-500 bg-bamboo-50 text-bamboo-800'
              : 'border-cream-300 bg-white text-warm-600 hover:border-bamboo-300 hover:bg-cream-50'
          ]"
        >
          {{ item.label }}
          <span v-if="loading && selectedType === item.type" class="ml-2 text-xs text-warm-500">生成中...</span>
        </button>
      </div>

      <!-- Review Output -->
      <div v-if="selectedType" class="space-y-3">
        <div class="bg-bamboo-50 border border-bamboo-200 rounded-lg p-4 text-sm text-bamboo-800 whitespace-pre-line leading-relaxed">
          {{ currentReview }}
        </div>
        <button @click="copyReview" class="btn-ghost text-sm">
          <Copy class="w-3.5 h-3.5" />
          {{ copied ? '已复制' : '一键复制给客人' }}
        </button>
      </div>
    </div>

    <!-- RIGHT CARD: 好评激励方案 -->
    <div class="card space-y-5">
      <div class="flex items-center gap-2.5">
        <Gift class="w-5 h-5 text-bamboo-700" />
        <h2 class="text-base font-semibold text-bamboo-800">好评激励方案</h2>
      </div>

      <div class="space-y-3">
        <button
          v-for="(item, i) in incentives"
          :key="i"
          @click="selectIncentive(i)"
          :class="[
            'w-full text-left p-4 rounded-lg border transition-all duration-150',
            selectedIncentive === i
              ? 'border-bamboo-500 bg-bamboo-50'
              : 'border-cream-300 bg-white hover:border-bamboo-300 hover:bg-cream-50'
          ]"
        >
          <div class="flex items-center justify-between mb-1">
            <span class="text-sm font-medium text-bamboo-800">{{ item.title }}</span>
            <span :class="['badge', item.badgeClass]">{{ item.badge }}</span>
          </div>
          <span class="text-xs text-warm-600">{{ item.desc }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
