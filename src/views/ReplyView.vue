<script setup lang="ts">
import { ref } from 'vue'
import { MessageSquareText, Copy } from 'lucide-vue-next'
import { generateReply } from '@/api/hotel'

const reviewType = ref('五星好评·夸环境')
const replyStyle = ref('温暖亲切')
const copied = ref(false)
const loading = ref(false)

const replyText = ref('')

async function doGenerate() {
  loading.value = true
  try {
    const { data: res } = await generateReply({ reviewType: reviewType.value, style: replyStyle.value })
    const d = res.data || res
    replyText.value = d.reply || ''
  } catch {
    replyText.value = '感谢您选择松间·山野，您的认可是我们最大的动力。期待在下一个季节与您重逢，祝您旅途平安！'
  } finally {
    loading.value = false
  }
}

async function copyReply() {
  if (!replyText.value) return
  try {
    await navigator.clipboard.writeText(replyText.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch { /* fallback */ }
}

const reviewTypes = ['五星好评·夸环境', '五星好评·夸服务', '四星·有小建议', '差评·需挽回']
const replyStyles = ['温暖亲切', '专业正式', '活泼有趣']
</script>

<template>
  <div class="card space-y-5">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2.5">
        <MessageSquareText class="w-5 h-5 text-bamboo-700" />
        <h2 class="text-base font-semibold text-bamboo-800">AI回评话术</h2>
      </div>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div>
        <label class="label">评价类型</label>
        <select v-model="reviewType" @change="doGenerate" class="input-field">
          <option v-for="t in reviewTypes" :key="t" :value="t">{{ t }}</option>
        </select>
      </div>
      <div>
        <label class="label">回复风格</label>
        <select v-model="replyStyle" @change="doGenerate" class="input-field">
          <option v-for="s in replyStyles" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
    </div>

    <div class="space-y-2">
      <textarea v-model="replyText" rows="5" class="input-field resize-none" :placeholder="loading ? '生成中...' : ''"></textarea>
      <div class="flex items-center gap-2">
        <button @click="copyReply" class="btn-ghost text-sm">
          <Copy class="w-3.5 h-3.5" />
          {{ copied ? '已复制' : '复制话术' }}
        </button>
      </div>
    </div>

    <button @click="doGenerate" :disabled="loading" class="btn-primary">
      {{ loading ? '生成中...' : 'AI重新生成' }}
    </button>
  </div>
</template>
