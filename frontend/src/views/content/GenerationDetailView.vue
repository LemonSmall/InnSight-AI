<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Copy, Download, ExternalLink, FileText, Loader2, RotateCcw, Trash2, X } from 'lucide-vue-next'
import { deleteGenerationHistory, getGenerationHistoryDetail } from '@/api/history'
import AiGenerationPreview from '@/components/ai/AiGenerationPreview.vue'
import { elementNode, exportMarkdown, exportPdfElement, safeFilename } from '@/utils/exportDocument'
import { clearAiPageState } from '@/utils/aiPageState'
import { copyTextToClipboard } from '@/utils/clipboard'
import {
  formatHistoryTime,
  friendlyConfig,
  historyTitle,
  imageUrl,
  moduleHistoryRoute,
  moduleLabel,
  promptText,
  resultText,
  reuseGeneration,
  type HistoryItem,
} from '@/utils/generationHistory'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const deleting = ref(false)
const deleteDialogOpen = ref(false)
const item = ref<HistoryItem | null>(null)
const errorMessage = ref('')
const toast = ref('')

const moduleKey = computed(() => String(route.params.moduleKey || ''))
const id = computed(() => Number(route.params.id || 0))
const canExportPlan = computed(() => item.value?.moduleKey === 'strategy' || item.value?.moduleKey === 'pricing')
const isXhsDetail = computed(() => item.value?.moduleKey === 'xhs')
const xhsTags = computed(() => {
  const content = item.value?.outputContent || ''
  const matches = [...String(content).matchAll(/#([^\s#]+)/g)]
  return [...new Set(matches.map(match => match[1]).filter(Boolean))]
})
const configSummaryText = computed(() => formatConfigSummary(item.value))
const backPath = computed(() => {
  const from = typeof route.query.from === 'string' ? route.query.from : ''
  return from.startsWith('/history') ? from : moduleHistoryRoute(moduleKey.value)
})

onMounted(load)
watch(() => route.fullPath, load)

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await getGenerationHistoryDetail(id.value, moduleKey.value)
    if (data?.code && data.code !== 200) {
      throw new Error(data.message || '生成记录加载失败，请稍后重试')
    }
    item.value = data.data || data || null
  } catch (error: any) {
    item.value = null
    errorMessage.value = error?.response?.data?.message || error?.message || '生成记录加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function copyContent() {
  if (!item.value) return
  const ok = await copyTextToClipboard(item.value.outputContent || resultText(item.value))
  flash(ok ? '内容已复制' : '复制失败')
}

function reuse() {
  if (!item.value) return
  reuseGeneration(item.value, router)
  flash('已复用当时配置')
}

async function exportResult(format: 'markdown' | 'pdf') {
  if (!item.value) return
  const title = historyTitle(item.value)
  const content = item.value.outputContent || resultText(item.value)
  if (format === 'markdown') {
    exportMarkdown(safeFilename(title), title, content)
    return
  }
  const ok = await exportPdfElement(title, elementNode('#generation-detail-export-content'), safeFilename(title))
  if (!ok) flash('导出内容为空，请稍后重试')
}

function requestDelete() {
  if (!item.value || deleting.value) return
  deleteDialogOpen.value = true
}

function cancelDelete() {
  if (deleting.value) return
  deleteDialogOpen.value = false
}

async function confirmDelete() {
  if (!item.value || deleting.value) return
  deleting.value = true
  try {
    await deleteGenerationHistory([item.value.id])
    if (item.value.moduleKey === 'xhs') {
      clearAiPageState('xhs')
    }
    deleteDialogOpen.value = false
    router.push(backPath.value)
  } finally {
    deleting.value = false
  }
}

function flash(message: string) {
  toast.value = message
  window.setTimeout(() => { toast.value = '' }, 1600)
}

function formatConfigSummary(current: HistoryItem | null) {
  const rows = friendlyConfig(current)
  if (!rows.length) return ''
  return rows.map(row => `${row.label}：${row.value}`).join('\n')
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden bg-cream-50/60">
    <transition name="toast">
      <div v-if="toast" class="fixed right-6 top-6 z-50 rounded-xl border border-bamboo-200 bg-white/95 px-4 py-3 text-sm font-medium text-bamboo-800 shadow-2xl backdrop-blur">{{ toast }}</div>
    </transition>

    <div class="shrink-0 border-b border-cream-300 bg-white/95 px-6 py-3 backdrop-blur">
      <div class="mx-auto flex max-w-[1500px] flex-wrap items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-3">
          <button class="icon-button" title="返回记录列表" @click="router.push(backPath)"><ArrowLeft class="h-4 w-4" /></button>
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <h1 class="truncate text-base font-semibold text-bamboo-900">{{ item ? historyTitle(item) : '生成详情' }}</h1>
              <span class="rounded-full bg-bamboo-50 px-2.5 py-1 text-[11px] text-bamboo-700">{{ moduleLabel(moduleKey) }}</span>
            </div>
            <p v-if="item" class="mt-0.5 truncate text-xs text-warm-500">{{ formatHistoryTime(item.createdAt) }} · 消耗 {{ item.costCredits || 0 }} 算力</p>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <button class="secondary-button" :disabled="!item" @click="copyContent"><Copy class="h-4 w-4" />复制结果</button>
          <button v-if="canExportPlan" class="secondary-button" :disabled="!item" @click="exportResult('markdown')"><Download class="h-4 w-4" />导出 Markdown</button>
          <button v-if="canExportPlan" class="secondary-button" :disabled="!item" @click="exportResult('pdf')"><Download class="h-4 w-4" />导出 PDF</button>
          <button class="danger-button" :disabled="!item || deleting" @click="requestDelete"><Trash2 class="h-4 w-4" />删除</button>
          <button class="primary-button" :disabled="!item" @click="reuse"><ExternalLink class="h-4 w-4" />复用配置</button>
        </div>
      </div>
    </div>

    <div v-if="loading" class="flex min-h-0 flex-1 items-center justify-center">
      <Loader2 class="h-7 w-7 animate-spin text-bamboo-700" />
    </div>

    <div v-else-if="!item" class="flex min-h-0 flex-1 flex-col items-center justify-center text-center">
      <FileText class="h-10 w-10 text-warm-300" />
      <h2 class="mt-4 text-base font-semibold text-bamboo-950">{{ errorMessage || '没有找到这条生成记录' }}</h2>
      <button class="primary-button mt-5" @click="router.push(backPath)"><RotateCcw class="h-4 w-4" />返回列表</button>
    </div>

    <div v-else class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto flex max-w-[1500px] flex-col gap-5 p-6">
        <section class="history-meta-grid" :class="{ 'xhs-history-meta-grid': isXhsDetail }">
          <section class="history-meta-card" :class="{ 'xhs-history-meta-card': isXhsDetail }">
            <div class="text-sm font-semibold text-bamboo-900">当时配置</div>
            <p v-if="configSummaryText" class="history-prompt-text">{{ configSummaryText }}</p>
            <p v-else class="mt-4 text-sm text-warm-500">这条记录没有可复用配置。</p>
          </section>

          <section class="history-meta-card" :class="{ 'xhs-history-meta-card xhs-prompt-card': isXhsDetail }">
            <div class="text-sm font-semibold text-bamboo-900">当时提示</div>
            <p class="mt-3 max-h-40 overflow-y-auto whitespace-pre-wrap text-sm leading-6 text-warm-700">{{ promptText(item) || '未记录提示词' }}</p>
          </section>
        </section>

        <main class="min-w-0 overflow-hidden rounded-2xl border border-cream-300 bg-white shadow-sm">
          <div class="border-b border-cream-200 px-7 py-5">
            <h2 class="text-sm font-semibold text-bamboo-900">生成内容展示</h2>
            <p class="mt-1 text-xs text-warm-500">按当前内容类型整理展示，保留复制和配置复用能力。</p>
          </div>
          <div id="generation-detail-export-content" class="generation-detail-content" :class="{ 'xhs-generation-detail-content': isXhsDetail }">
            <section v-if="isXhsDetail" class="xhs-detail-layout">
              <div class="xhs-image-panel">
                <div class="xhs-image-frame">
                  <img v-if="imageUrl(item)" :src="imageUrl(item)" alt="小红书配图" class="xhs-image" />
                  <div v-else class="xhs-image-empty">
                    <FileText class="h-8 w-8" />
                    <span>暂无配图</span>
                  </div>
                </div>
              </div>
              <div class="xhs-copy-panel">
                <span class="xhs-chip">小红书图文</span>
                <h2 class="xhs-copy-title">{{ historyTitle(item) }}</h2>
                <p class="xhs-copy-body">{{ resultText(item) }}</p>
                <div v-if="xhsTags.length" class="xhs-tag-list">
                  <span v-for="tag in xhsTags" :key="tag" class="xhs-tag">#{{ tag }}</span>
                </div>
              </div>
            </section>
            <AiGenerationPreview v-else :item="item" />
          </div>
        </main>
      </div>
    </div>

    <div v-if="deleteDialogOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-bamboo-950/45 p-4" @click.self="cancelDelete">
      <section class="w-full max-w-md rounded-2xl border border-cream-300 bg-white p-5 shadow-2xl">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-base font-semibold text-bamboo-950">删除生成记录</h2>
            <p class="mt-2 text-sm leading-6 text-warm-600">确定删除这条生成记录吗？删除后无法恢复。</p>
          </div>
          <button class="icon-button" :disabled="deleting" @click="cancelDelete"><X class="h-4 w-4" /></button>
        </div>
        <div class="mt-5 flex justify-end gap-2">
          <button class="secondary-button" :disabled="deleting" @click="cancelDelete">取消</button>
          <button class="danger-button" :disabled="deleting" @click="confirmDelete">
            <Loader2 v-if="deleting" class="h-4 w-4 animate-spin" />
            <Trash2 v-else class="h-4 w-4" />
            确认删除
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.primary-button,.secondary-button { display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; border-radius: 0.7rem; padding: 0.6rem 0.85rem; font-size: 0.75rem; font-weight: 600; transition: 150ms ease; }
.primary-button { background: #234d32; color: white; }
.primary-button:hover { background: #183b26; }
.secondary-button { border: 1px solid #e5d8c5; background: white; color: #5f5143; }
.secondary-button:hover { border-color: #8cac77; background: #f7faf4; }
.primary-button:disabled,.secondary-button:disabled { cursor: not-allowed; opacity: 0.5; }
.danger-button { display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; border-radius: 0.7rem; background: #b91c1c; color: white; padding: 0.6rem 0.85rem; font-size: 0.75rem; font-weight: 600; transition: 150ms ease; }
.danger-button:hover { background: #991b1b; }
.danger-button:disabled { cursor: not-allowed; opacity: 0.5; }
.icon-button { display: inline-flex; height: 2.25rem; width: 2.25rem; flex-shrink: 0; align-items: center; justify-content: center; border: 1px solid #e5d8c5; border-radius: 0.65rem; color: #776655; }
.icon-button:hover { border-color: #8cac77; background: #f7faf4; color: #234d32; }
.icon-button:disabled { cursor: not-allowed; opacity: 0.5; }

.history-meta-grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.history-meta-card {
  min-width: 0;
  border: 1px solid #eadfce;
  border-radius: 1rem;
  background: white;
  padding: 1.25rem;
  box-shadow: 0 8px 20px rgb(84 58 44 / 6%);
}

.history-prompt-text {
  margin-top: 0.75rem;
  max-height: 10rem;
  overflow-y: auto;
  white-space: pre-wrap;
  color: #5f5143;
  font-size: 0.875rem;
  line-height: 1.75;
}

.generation-detail-content {
  min-height: 780px;
  background: rgb(250 246 239 / 0.4);
  padding: 1.5rem;
}

@media (min-width: 640px) {
  .generation-detail-content {
    padding: 2rem 2.5rem;
  }
}

@media (min-width: 1280px) {
  .generation-detail-content {
    padding-inline: 3.5rem;
  }
}

.xhs-history-meta-grid {
  align-items: start;
  gap: 0.75rem;
  grid-template-columns: 1fr;
}

.xhs-history-meta-card {
  border-radius: 0.9rem;
  padding: 1rem;
}

.xhs-prompt-card p {
  margin-top: 0.65rem;
  max-height: 7rem;
  overflow-y: auto;
  white-space: pre-wrap;
  color: #5f5143;
  font-size: 0.9rem;
  line-height: 1.7;
}

.xhs-generation-detail-content {
  min-height: 0;
  padding: 1.25rem;
}
.toast-enter-active,.toast-leave-active { transition: opacity 160ms ease, transform 160ms ease; }
.toast-enter-from,.toast-leave-to { opacity: 0; transform: translateY(-8px); }

.xhs-detail-layout {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  align-items: start;
}

.xhs-image-panel,
.xhs-copy-panel {
  min-width: 0;
  border: 1px solid #eadfce;
  border-radius: 1.25rem;
  background: white;
  box-shadow: 0 12px 30px rgb(84 58 44 / 8%);
}

.xhs-image-panel {
  padding: 1rem;
}

.xhs-image-frame {
  overflow: hidden;
  border-radius: 1rem;
  background: #fff1f2;
  aspect-ratio: 3 / 4;
}

.xhs-image {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.xhs-image-empty {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  color: #d58f9b;
  font-size: 0.8rem;
}

.xhs-copy-panel {
  padding: 1.5rem;
}

.xhs-chip {
  display: inline-flex;
  border-radius: 999px;
  background: #fff1f2;
  padding: 0.35rem 0.7rem;
  color: #be5168;
  font-size: 0.72rem;
  font-weight: 700;
}

.xhs-copy-title {
  margin-top: 0.9rem;
  color: #203f2b;
  font-size: 1.45rem;
  font-weight: 800;
  line-height: 1.45;
}

.xhs-copy-body {
  margin-top: 1rem;
  white-space: pre-line;
  color: #5f5143;
  font-size: 0.95rem;
  line-height: 1.9;
}

.xhs-tag-list {
  margin-top: 1.25rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
}

.xhs-tag {
  border-radius: 999px;
  background: #fff1f2;
  padding: 0.4rem 0.75rem;
  color: #be5168;
  font-size: 0.74rem;
  font-weight: 700;
}

@media (max-width: 1024px) {
  .history-meta-grid {
    grid-template-columns: 1fr;
  }

  .xhs-detail-layout {
    grid-template-columns: 1fr;
  }
}

</style>
