<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Users } from 'lucide-vue-next'
import { getGuests } from '@/api/hotel'

const router = useRouter()
const guests = ref<any[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const { data: res } = await getGuests()
    const list = res.data || res
    if (Array.isArray(list)) {
      guests.value = list
    }
  } catch {
    // 回退
    guests.value = [
      { roomNumber: '101', guestType: '情侣', source: '小红书引流', nights: 2, checkoutDate: '2026-06-13' },
      { roomNumber: '203', guestType: '家庭', source: '美团引流', nights: 3, checkoutDate: '2026-06-13' },
      { roomNumber: '301', guestType: '家庭', source: '携程引流', nights: 1, checkoutDate: '2026-06-13' },
    ]
  } finally {
    loading.value = false
  }
})

function guestLabel(g: any): string {
  const parts = [g.roomNumber]
  if (g.guestType) parts.push(g.guestType)
  return parts.join(' · ')
}

function sourceLabel(g: any): string {
  const parts = [g.source || '']
  if (g.nights) parts.push(`${g.nights}晚`)
  if (g.checkoutDate) parts.push(`${g.checkoutDate}离店`)
  return parts.filter(Boolean).join(' · ')
}
</script>

<template>
  <div class="card space-y-5">
    <div class="flex items-center gap-2.5">
      <Users class="w-5 h-5 text-bamboo-700" />
      <h2 class="text-base font-semibold text-bamboo-800">今日在住客动态</h2>
    </div>

    <div v-if="loading" class="text-center py-8 text-xs text-warm-500">加载中...</div>

    <div v-else class="divide-y divide-cream-200/60 -mx-0">
      <div
        v-for="(guest, i) in guests"
        :key="i"
        class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 py-4 first:pt-0 last:pb-0"
      >
        <div class="space-y-0.5">
          <div class="text-sm font-medium text-bamboo-800">{{ guestLabel(guest) }}</div>
          <div class="text-xs text-warm-600">{{ sourceLabel(guest) }}</div>
        </div>
        <div class="flex items-center gap-2 flex-wrap">
          <button @click="router.push('/review')" class="btn-primary text-xs">好评引导</button>
          <button class="btn-ghost text-xs">离店关怀</button>
        </div>
      </div>

      <div v-if="guests.length === 0" class="py-8 text-center text-xs text-warm-500">
        暂无在住客人记录
      </div>
    </div>
  </div>
</template>
