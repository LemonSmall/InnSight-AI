<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Copy, FileText, MessageSquareText, Sparkles, Trash2 } from 'lucide-vue-next'
import { collectStreamContent } from '@/api/content'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { copyTextToClipboard } from '@/utils/clipboard'
import { useHotelStore } from '@/stores/hotel'
import { buildContentAiParams } from '@/utils/aiContextParams'

const hotel = useHotelStore()
const router = useRouter()
const pageStateKey = 'reply'

const reviewType = ref('五星好评·夸环境')
const replyStyle = ref('温暖亲切')
const reviewText = ref('')
const replyText = ref('')
const copied = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const reviewTypes = ['五星好评·夸环境', '五星好评·夸服务', '四星·有小建议', '差评·需挽回']
const replyStyles = ['温暖亲切', '专业正式', '活泼有趣']

function persistState() {
  saveAiPageState(pageStateKey, {
    reviewType: reviewType.value,
    replyStyle: replyStyle.value,
    reviewText: reviewText.value,
    replyText: replyText.value,
  })
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (!restored) return
  reviewType.value = restored.reviewType || reviewType.value
  replyStyle.value = restored.replyStyle || replyStyle.value
  reviewText.value = restored.reviewText || ''
  replyText.value = restored.replyText || ''
}

async function doGenerate() {
  if (loading.value) return

  loading.value = true
  copied.value = false
  errorMessage.value = ''
  replyText.value = ''
  persistState()

  try {
    replyText.value = await collectStreamContent('reply', buildContentAiParams(hotel, 'reply', {
      reviewText: reviewText.value.trim(),
      reviewType: reviewType.value,
      style: replyStyle.value,
      theme: reviewType.value,
      message: reviewText.value.trim()
        ? `根据客人的真实评价内容，综合“${reviewType.value}”场景生成酒店点评回复`
        : `生成“${reviewType.value}”场景的酒店点评回复`,
      outputFormat: 'text',
    }), {
      onChunk(_chunk, content) {
        replyText.value = content
        persistState()
      },
    })

    persistState()
    if (!replyText.value) throw new Error('AI 调用失败')
  } catch {
    replyText.value = ''
    errorMessage.value = 'AI 调用失败，请稍后重试'
  } finally {
    loading.value = false
    persistState()
  }
}

async function copyReply() {
  if (!replyText.value) return
  try {
    const ok = await copyTextToClipboard(replyText.value)
    if (!ok) throw new Error('copy failed')
    copied.value = true
    window.setTimeout(() => { copied.value = false }, 2000)
  } catch {
    errorMessage.value = '复制失败，请稍后重试'
  }
}

function clearContent() {
  reviewText.value = ''
  replyText.value = ''
  copied.value = false
  errorMessage.value = ''
  persistState()
}

onMounted(() => {
  restoreState()
})
</script>

<template>
  <section class="reply-card">
    <div class="reply-head">
      <div class="title-wrap">
        <MessageSquareText class="h-5 w-5 text-bamboo-700" />
        <div>
          <h2>点评回复</h2>
          <p>选择评价类型和回复语气，生成可直接使用的平台回复文案。</p>
        </div>
      </div>
      <button
        @click="router.push('/history/reply')"
        class="mini-action"
      >
        <FileText class="h-3 w-3" />生成记录
      </button>
    </div>

    <div class="reply-grid">
      <label class="field">
        <span>评价类型</span>
        <select v-model="reviewType" class="input-field">
          <option v-for="item in reviewTypes" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>

      <label class="field">
        <span>回复风格</span>
        <select v-model="replyStyle" class="input-field">
          <option v-for="item in replyStyles" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
    </div>

    <label class="field editor-wrap">
      <span>客户评价内容 <em>可选</em></span>
      <textarea
        v-model="reviewText"
        rows="4"
        class="reply-editor review-source"
        placeholder="把客人在平台上的评价粘贴到这里。填写后，AI 会结合评价原文、评价类型和回复风格综合生成回复。"
        @input="persistState"
      />
    </label>

    <div class="editor-wrap result-wrap">
      <div class="result-head">
        <span>生成回复</span>
        <small>{{ reviewText.trim() ? '已结合客户评价内容' : '未填写客户评价内容，将按评价类型生成' }}</small>
      </div>
      <textarea
        v-model="replyText"
        rows="7"
        class="reply-editor"
        :placeholder="loading ? 'AI 生成中...' : '生成结果会显示在这里'"
      />
      <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
      <div class="action-row">
        <button class="primary-button" :disabled="loading" @click="doGenerate">
          <Sparkles class="h-4 w-4" />
          {{ loading ? 'AI生成中...' : 'AI生成回复' }}
        </button>
        <button @click="copyReply" :disabled="!replyText" class="ghost-button">
          <Copy class="h-3.5 w-3.5" />
          {{ copied ? '已复制' : '复制话术' }}
        </button>
        <button @click="clearContent" :disabled="loading || (!reviewText && !replyText)" class="ghost-button danger-ghost">
          <Trash2 class="h-3.5 w-3.5" />
          清空内容
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.reply-card {
  border-radius: 1rem;
  border: 1px solid #e7dccb;
  background: #fff;
  padding: 1.25rem;
  box-shadow: 0 10px 24px rgb(64 49 35 / 5%);
}

.reply-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.title-wrap {
  display: flex;
  gap: 0.75rem;
}

.title-wrap h2 {
  color: #1d3c29;
  font-size: 1rem;
  font-weight: 700;
}

.title-wrap p {
  margin-top: 0.2rem;
  color: #8b7460;
  font-size: 0.75rem;
  line-height: 1.6;
}

.mini-action,
.ghost-button,
.primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  border-radius: 0.7rem;
  transition: 150ms ease;
}

.mini-action {
  flex-shrink: 0;
  border: 1px solid #e7dccb;
  background: #fff;
  padding: 0.45rem 0.7rem;
  color: #7b6654;
  font-size: 0.7rem;
  font-weight: 600;
}

.reply-grid {
  margin-top: 1rem;
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.field {
  display: grid;
  gap: 0.45rem;
}

.field span {
  color: #7d6958;
  font-size: 0.74rem;
  font-weight: 700;
}

.field em {
  margin-left: 0.35rem;
  color: #b1864b;
  font-size: 0.68rem;
  font-style: normal;
  font-weight: 600;
}

.input-field,
.reply-editor {
  width: 100%;
  border-radius: 0.8rem;
  border: 1px solid #e7dccb;
  background: #fff;
  color: #1d3c29;
  font-size: 0.84rem;
}

.input-field {
  padding: 0.7rem 0.8rem;
}

.reply-editor {
  min-height: 12rem;
  resize: vertical;
  padding: 0.9rem 1rem;
  line-height: 1.8;
}

.review-source {
  min-height: 7rem;
  background: #fffdf8;
}

.editor-wrap {
  margin-top: 1rem;
}

.action-row {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  justify-content: flex-start;
}

.ghost-button {
  border: 1px solid #e7dccb;
  background: #fff;
  padding: 0.55rem 0.85rem;
  color: #6b5848;
  font-size: 0.76rem;
  font-weight: 600;
}

.danger-ghost {
  border-color: #fecaca;
  color: #dc2626;
}

.danger-ghost:hover {
  background: #fff1f2;
}

.primary-button {
  width: fit-content;
  border: 0;
  background: #234d32;
  padding: 0.72rem 0.95rem;
  color: #fff7e7;
  font-size: 0.84rem;
  font-weight: 700;
}

.result-wrap {
  border-radius: 0.95rem;
  border: 1px solid #f0e7dc;
  background: #fffdf8;
  padding: 0.9rem;
}

.result-head {
  margin-bottom: 0.6rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.result-head span {
  color: #1d3c29;
  font-size: 0.8rem;
  font-weight: 700;
}

.result-head small {
  color: #a17a44;
  font-size: 0.7rem;
}

.ghost-button:disabled,
.primary-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.error-text {
  margin-top: 0.5rem;
  color: #dc2626;
  font-size: 0.8rem;
}

@media (max-width: 760px) {
  .reply-card {
    padding: 1rem;
  }

  .reply-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .mini-action {
    width: 100%;
  }

  .reply-grid {
    grid-template-columns: 1fr;
  }

  .action-row {
    justify-content: stretch;
  }

  .ghost-button {
    width: 100%;
  }

  .primary-button {
    width: 100%;
  }
}
</style>
