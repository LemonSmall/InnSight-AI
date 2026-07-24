<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Database, Loader2, Pencil, RefreshCw, Search, Trash2, X } from 'lucide-vue-next'
import { deleteKnowledgeItem, getKnowledgeItems, updateKnowledgeItem } from '@/api/knowledge'

type KnowledgeItem = {
  id: number
  category: string
  title: string
  content: string
  effectiveFrom?: string
  effectiveTo?: string
  updatedAt?: string
  sourceName?: string
  status?: string
}

const items = ref<KnowledgeItem[]>([])
const loading = ref(false)
const saving = ref(false)
const toast = ref('')
const activeCategory = ref('')
const keyword = ref('')
const editOpen = ref(false)
const deleteOpen = ref(false)
const selectedItem = ref<KnowledgeItem | null>(null)
const editForm = ref({ category: '', title: '', content: '', effectiveFrom: '', effectiveTo: '' })

const categories = [
  { key: '', label: '全部资料' },
  { key: 'room', label: '房型资料' },
  { key: 'facility', label: '设施服务' },
  { key: 'policy', label: '政策时间' },
  { key: 'promotion', label: '活动促销' },
  { key: 'temporary_notice', label: '临时通知' },
]

const filteredItems = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return items.value.filter(item => {
    const matchCategory = !activeCategory.value || item.category === activeCategory.value
    const text = `${item.title || ''} ${item.content || ''} ${item.sourceName || ''}`.toLowerCase()
    return matchCategory && (!key || text.includes(key))
  })
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await getKnowledgeItems(undefined, 200)
    items.value = data.data || []
  } catch {
    flash('知识库加载失败')
  } finally {
    loading.value = false
  }
}

function categoryCount(key: string) {
  return key ? items.value.filter(item => item.category === key).length : items.value.length
}

function categoryLabel(value: string) {
  return categories.find(item => item.key === value)?.label || value || '资料'
}

function openEdit(item: KnowledgeItem) {
  selectedItem.value = item
  editForm.value = {
    category: item.category,
    title: item.title,
    content: item.content,
    effectiveFrom: toDateInput(item.effectiveFrom),
    effectiveTo: toDateInput(item.effectiveTo),
  }
  editOpen.value = true
}

async function saveEdit() {
  if (!selectedItem.value) return
  if (!editForm.value.title.trim() || !editForm.value.content.trim()) {
    flash('标题和内容不能为空')
    return
  }
  saving.value = true
  try {
    await updateKnowledgeItem(selectedItem.value.id, editForm.value)
    editOpen.value = false
    flash('知识已更新')
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function openDelete(item: KnowledgeItem) {
  selectedItem.value = item
  deleteOpen.value = true
}

async function confirmDelete() {
  if (!selectedItem.value) return
  saving.value = true
  try {
    await deleteKnowledgeItem(selectedItem.value.id)
    deleteOpen.value = false
    flash('知识已删除')
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '删除失败')
  } finally {
    saving.value = false
  }
}

function toDateInput(value?: string) {
  return value ? value.slice(0, 10) : ''
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function flash(message: string) {
  toast.value = message
  window.setTimeout(() => (toast.value = ''), 1800)
}
</script>

<template>
  <div class="space-y-6 pb-8">
    <transition name="toast">
      <div v-if="toast" class="fixed right-6 top-6 z-50 rounded-lg bg-bamboo-900 px-4 py-2 text-sm text-bamboo-50 shadow-xl">
        {{ toast }}
      </div>
    </transition>

    <section class="rounded-3xl border border-cream-300 bg-white p-6 shadow-sm">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <RouterLink to="/knowledge" class="inline-flex items-center gap-2 text-xs font-semibold text-bamboo-700 hover:text-bamboo-950">
            <ArrowLeft class="h-4 w-4" />
            返回资料中心
          </RouterLink>
          <div class="mt-4 flex items-center gap-2 text-xs font-semibold text-bamboo-700">
            <Database class="h-4 w-4" />
            本店知识库
          </div>
          <h1 class="mt-2 text-3xl font-semibold text-bamboo-950">全部已确认资料</h1>
          <p class="mt-2 text-sm text-warm-500">统一查看、筛选、编辑和删除所有会进入 AI 上下文的本店资料。</p>
        </div>
        <button class="inline-flex items-center gap-2 rounded-xl bg-bamboo-800 px-4 py-2 text-sm font-semibold text-white hover:bg-bamboo-900 disabled:opacity-60" :disabled="loading" @click="load">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
          刷新
        </button>
      </div>

      <div class="mt-6 grid gap-3 lg:grid-cols-[1fr_320px]">
        <div class="flex flex-wrap gap-2">
          <button
            v-for="category in categories"
            :key="category.key"
            class="rounded-full border px-4 py-2 text-sm font-medium transition"
            :class="activeCategory === category.key ? 'border-bamboo-800 bg-bamboo-800 text-white' : 'border-cream-300 bg-cream-50 text-warm-700 hover:border-bamboo-300 hover:bg-bamboo-50'"
            @click="activeCategory = category.key"
          >
            {{ category.label }} {{ categoryCount(category.key) }}
          </button>
        </div>
        <label class="flex items-center gap-2 rounded-2xl border border-cream-300 bg-cream-50 px-4 py-2">
          <Search class="h-4 w-4 text-warm-400" />
          <input v-model="keyword" class="w-full bg-transparent text-sm text-bamboo-950 outline-none placeholder:text-warm-400" placeholder="搜索标题、内容或来源" />
        </label>
      </div>
    </section>

    <section class="rounded-3xl border border-cream-300 bg-white shadow-sm">
      <div class="flex items-center justify-between border-b border-cream-200 px-5 py-4">
        <div class="text-sm font-semibold text-bamboo-950">资料列表</div>
        <div class="text-xs text-warm-500">当前 {{ filteredItems.length }} 条</div>
      </div>

      <div v-if="loading" class="grid place-items-center py-24 text-sm text-warm-500">
        <Loader2 class="mb-3 h-6 w-6 animate-spin text-bamboo-700" />
        正在加载知识库
      </div>

      <div v-else class="grid gap-4 p-5 xl:grid-cols-2">
        <article v-for="item in filteredItems" :key="item.id" class="rounded-2xl border border-cream-200 bg-cream-50 p-5 transition hover:border-bamboo-300 hover:bg-white hover:shadow-sm">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <span class="rounded-full bg-bamboo-100 px-2.5 py-1 text-[11px] font-medium text-bamboo-800">{{ categoryLabel(item.category) }}</span>
              <h2 class="mt-3 text-base font-semibold text-bamboo-950">{{ item.title }}</h2>
            </div>
            <div class="flex shrink-0 items-center gap-1">
              <button class="rounded-lg p-2 text-warm-500 hover:bg-bamboo-100 hover:text-bamboo-800" title="编辑知识" @click="openEdit(item)"><Pencil class="h-4 w-4" /></button>
              <button class="rounded-lg p-2 text-warm-500 hover:bg-red-50 hover:text-red-600" title="删除知识" @click="openDelete(item)"><Trash2 class="h-4 w-4" /></button>
            </div>
          </div>
          <p class="mt-4 whitespace-pre-wrap text-sm leading-7 text-warm-700">{{ item.content }}</p>
          <div class="mt-4 flex flex-wrap gap-x-4 gap-y-1 border-t border-cream-200 pt-3 text-[11px] text-warm-400">
            <span>更新于 {{ formatTime(item.updatedAt) }}</span>
            <span v-if="item.effectiveFrom || item.effectiveTo">有效期 {{ toDateInput(item.effectiveFrom) || '即时' }} 至 {{ toDateInput(item.effectiveTo) || '长期' }}</span>
            <span v-if="item.sourceName">来源 {{ item.sourceName }}</span>
          </div>
        </article>

        <div v-if="filteredItems.length === 0" class="col-span-full rounded-2xl border border-dashed border-cream-300 bg-cream-50 py-20 text-center text-sm text-warm-500">
          没有匹配的资料
        </div>
      </div>
    </section>

    <div v-if="editOpen" class="fixed inset-0 z-50 grid place-items-center bg-bamboo-950/45 p-4" @click.self="editOpen = false">
      <section class="w-full max-w-3xl rounded-2xl border border-cream-300 bg-white shadow-2xl">
        <header class="flex items-center justify-between border-b border-cream-200 px-5 py-4">
          <div>
            <h2 class="font-semibold text-bamboo-950">编辑知识</h2>
            <p class="mt-1 text-xs text-warm-500">修改后会立即更新 AI 可用资料。</p>
          </div>
          <button class="rounded-lg p-2 text-warm-500 hover:bg-cream-100" @click="editOpen = false"><X class="h-4 w-4" /></button>
        </header>
        <div class="grid gap-4 p-5">
          <label class="grid gap-1.5 text-xs font-medium text-warm-700">分类
            <select v-model="editForm.category" class="studio-input">
              <option v-for="category in categories.slice(1)" :key="category.key" :value="category.key">{{ category.label }}</option>
            </select>
          </label>
          <label class="grid gap-1.5 text-xs font-medium text-warm-700">标题<input v-model="editForm.title" class="studio-input" /></label>
          <label class="grid gap-1.5 text-xs font-medium text-warm-700">知识内容<textarea v-model="editForm.content" rows="8" class="studio-input resize-none" /></label>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="grid gap-1.5 text-xs font-medium text-warm-700">生效日期<input v-model="editForm.effectiveFrom" type="date" class="studio-input" /></label>
            <label class="grid gap-1.5 text-xs font-medium text-warm-700">失效日期<input v-model="editForm.effectiveTo" type="date" class="studio-input" /></label>
          </div>
        </div>
        <footer class="flex justify-end gap-2 border-t border-cream-200 px-5 py-4">
          <button class="rounded-xl border border-cream-300 px-4 py-2 text-sm text-warm-700" @click="editOpen = false">取消</button>
          <button class="rounded-xl bg-bamboo-800 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="saving" @click="saveEdit">保存修改</button>
        </footer>
      </section>
    </div>

    <div v-if="deleteOpen" class="fixed inset-0 z-50 grid place-items-center bg-bamboo-950/45 p-4" @click.self="deleteOpen = false">
      <section class="w-full max-w-md rounded-2xl border border-cream-300 bg-white p-5 shadow-2xl">
        <h2 class="font-semibold text-bamboo-950">删除这条知识？</h2>
        <p class="mt-2 text-sm leading-6 text-warm-600">“{{ selectedItem?.title }}”将停止参与后续 AI 生成，历史调用记录仍会保留。</p>
        <div class="mt-5 flex justify-end gap-2">
          <button class="rounded-xl border border-cream-300 px-4 py-2 text-sm text-warm-700" @click="deleteOpen = false">取消</button>
          <button class="rounded-xl bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="saving" @click="confirmDelete">确认删除</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
