<script setup lang="ts">
import { ref } from 'vue'
import { Check, Loader2, RefreshCw, Sparkles, X } from 'lucide-vue-next'
import { collectStreamContent } from '@/api/content'
import { buildContentAiParams } from '@/utils/aiContextParams'

const props = withDefaults(defineProps<{
  sourceText: string
  scene: string
  field: string
  style?: string
  purpose?: string
  disabled?: boolean
}>(), {
  style: '自然、清晰、适合酒店经营场景',
  purpose: '优化表达，不改变原文中的事实、价格、日期、地点和服务承诺',
  disabled: false,
})

const emit = defineEmits<{ accept: [value: string] }>()
const open = ref(false)
const generating = ref(false)
const draft = ref('')
const error = ref('')

async function show() {
  if (!props.sourceText.trim() || props.disabled) return
  open.value = true
  await generate()
}

async function generate() {
  generating.value = true
  draft.value = ''
  error.value = ''
  try {
    draft.value = await collectStreamContent('polish', buildContentAiParams(null, 'polish', {
      sourceText: props.sourceText,
      message: props.sourceText,
      scene: props.scene,
      field: props.field,
      style: props.style,
      purpose: props.purpose,
      immutableFacts: [],
      outputFormat: 'text',
    }), {
      onChunk(_chunk, content) {
        draft.value = content
      },
    })
  } catch (cause: any) {
    error.value = cause?.message || '润色失败'
  } finally {
    generating.value = false
  }
}

function accept() {
  if (!draft.value.trim()) return
  emit('accept', draft.value.trim())
  open.value = false
}
</script>

<template>
  <button
    type="button"
    class="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-medium text-bamboo-700 transition hover:bg-bamboo-50 disabled:cursor-not-allowed disabled:opacity-40"
    :disabled="disabled || !sourceText.trim()"
    title="AI 润色"
    @click="show"
  >
    <Sparkles class="h-3.5 w-3.5" />
    AI 润色
  </button>

  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[80] grid place-items-center bg-bamboo-950/45 p-4" @click.self="open = false">
      <section class="w-full max-w-3xl overflow-hidden rounded-2xl border border-cream-300 bg-white shadow-2xl">
        <header class="flex items-center justify-between border-b border-cream-200 px-5 py-4">
          <div>
            <h2 class="font-semibold text-bamboo-950">AI 润色建议</h2>
            <p class="mt-1 text-xs text-warm-500">确认采纳后才会替换原内容。</p>
          </div>
          <button type="button" class="rounded-lg p-2 text-warm-500 hover:bg-cream-100" @click="open = false"><X class="h-4 w-4" /></button>
        </header>

        <div class="grid gap-4 p-5 md:grid-cols-2">
          <div>
            <div class="mb-2 text-xs font-semibold text-warm-600">原内容</div>
            <div class="min-h-48 whitespace-pre-wrap rounded-xl border border-cream-300 bg-cream-50 p-4 text-sm leading-7 text-warm-700">{{ sourceText }}</div>
          </div>
          <div>
            <div class="mb-2 flex items-center justify-between">
              <span class="text-xs font-semibold text-warm-600">润色结果</span>
              <span v-if="generating" class="inline-flex items-center gap-1 text-[11px] text-bamboo-700"><Loader2 class="h-3 w-3 animate-spin" />生成中</span>
            </div>
            <div class="min-h-48 whitespace-pre-wrap rounded-xl border border-bamboo-200 bg-bamboo-50/50 p-4 text-sm leading-7 text-bamboo-950">
              {{ draft || (generating ? '正在润色...' : error) }}
            </div>
          </div>
        </div>

        <footer class="flex flex-wrap justify-end gap-2 border-t border-cream-200 px-5 py-4">
          <button type="button" class="rounded-xl border border-cream-300 px-4 py-2 text-sm text-warm-700" @click="open = false">取消</button>
          <button type="button" class="inline-flex items-center gap-2 rounded-xl border border-bamboo-300 px-4 py-2 text-sm text-bamboo-800 disabled:opacity-50" :disabled="generating" @click="generate"><RefreshCw class="h-4 w-4" />重新生成</button>
          <button type="button" class="inline-flex items-center gap-2 rounded-xl bg-bamboo-800 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="generating || !draft.trim()" @click="accept"><Check class="h-4 w-4" />采纳</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
