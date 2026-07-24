<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  BookOpenCheck,
  CheckCircle2,
  Clock3,
  Database,
  FileText,
  FileUp,
  Loader2,
  Pencil,
  RefreshCw,
  Send,
  Sparkles,
  Trash2,
  X,
} from 'lucide-vue-next'
import AiPolishControl from '@/components/ai/AiPolishControl.vue'
import {
  cancelKnowledgeJob,
  confirmKnowledgeJob,
  deleteKnowledgeItem,
  getKnowledgeFiles,
  getKnowledgeItems,
  getPendingKnowledgeJobs,
  submitKnowledgeText,
  uploadKnowledgeFile,
  updateKnowledgeItem,
} from '@/api/knowledge'

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

type PendingJob = {
  id: number
  summary?: string
  inputText?: string
  extractedJson?: string
  createdAt?: string
}

type KnowledgeFile = {
  id: number
  originalName: string
  fileType: string
  fileSize: number
  parseStatus: 'pending' | 'processing' | 'done' | 'failed'
  parseError?: string
  createdAt?: string
}

const input = ref('')
const loading = ref(false)
const refreshing = ref(false)
const toast = ref('')
const items = ref<KnowledgeItem[]>([])
const pendingJobs = ref<PendingJob[]>([])
const files = ref<KnowledgeFile[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const activeCategory = ref('')
const editOpen = ref(false)
const deleteOpen = ref(false)
const selectedItem = ref<KnowledgeItem | null>(null)
const editForm = ref({ category: '', title: '', content: '', effectiveFrom: '', effectiveTo: '' })

const examples = [
  '二楼今天装修，201-205 房间今天不能出租',
  '早餐时间改为早上 7:00 到 10:00',
  '新增免费停车服务，可停 12 辆车',
  '本周末推出亲子家庭房套餐，含早餐和手作体验',
]

const categories = [
  { key: '', label: '全部资料' },
  { key: 'room', label: '房型资料' },
  { key: 'facility', label: '设施服务' },
  { key: 'policy', label: '政策时间' },
  { key: 'promotion', label: '活动促销' },
  { key: 'temporary_notice', label: '临时通知' },
]

const filteredItems = computed(() => {
  if (!activeCategory.value) return items.value
  return items.value.filter(item => item.category === activeCategory.value)
})

const previewItems = computed(() => filteredItems.value.slice(0, 4))

onMounted(load)

async function load() {
  refreshing.value = true
  try {
    const [{ data: itemsData }, { data: jobsData }, { data: filesData }] = await Promise.all([
      getKnowledgeItems(undefined, 200),
      getPendingKnowledgeJobs(),
      getKnowledgeFiles(30),
    ])
    items.value = itemsData.data || []
    pendingJobs.value = jobsData.data || []
    files.value = filesData.data || []
  } catch {
    flash('酒店资料加载失败')
  } finally {
    refreshing.value = false
  }
}

async function submitText() {
  const content = input.value.trim()
  if (!content || loading.value) return
  loading.value = true
  try {
    const { data } = await submitKnowledgeText(content)
    const job = data?.data
    if (job?.id || job?.jobId) {
      const jobId = job.id || job.jobId
      pendingJobs.value = [
        {
          id: jobId,
          summary: job.summary,
          inputText: job.inputText || content,
          extractedJson: job.extracted ? JSON.stringify(job.extracted) : undefined,
          createdAt: job.createdAt || new Date().toISOString(),
        },
        ...pendingJobs.value.filter(item => item.id !== jobId),
      ]
    }
    input.value = ''
    flash('已生成待确认资料')
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '提交失败')
  } finally {
    loading.value = false
  }
}

async function chooseFile(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || uploading.value) return
  uploading.value = true
  try {
    await uploadKnowledgeFile(file)
    flash('文件已整理，请确认提取结果')
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '文件处理失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}

async function confirmJob(job: PendingJob) {
  try {
    await confirmKnowledgeJob(job.id)
    flash('资料已写入知识库')
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '确认失败')
  }
}

async function cancelJob(job: PendingJob) {
  try {
    await cancelKnowledgeJob(job.id)
    flash('已取消入库')
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '取消失败')
  }
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
  loading.value = true
  try {
    await updateKnowledgeItem(selectedItem.value.id, editForm.value)
    editOpen.value = false
    flash('知识已更新')
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '保存失败')
  } finally {
    loading.value = false
  }
}

function openDelete(item: KnowledgeItem) {
  selectedItem.value = item
  deleteOpen.value = true
}

async function confirmDelete() {
  if (!selectedItem.value) return
  loading.value = true
  try {
    await deleteKnowledgeItem(selectedItem.value.id)
    deleteOpen.value = false
    flash('知识已删除')
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '删除失败')
  } finally {
    loading.value = false
  }
}

function useExample(value: string) {
  input.value = value
}

function categoryLabel(value: string) {
  return categories.find(item => item.key === value)?.label || value || '资料'
}

function categoryCount(key: string) {
  return key ? items.value.filter(item => item.category === key).length : items.value.length
}

function formatSize(value = 0) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function fileStatus(value: KnowledgeFile['parseStatus']) {
  return ({ pending: '等待处理', processing: '解析中', done: '已解析', failed: '失败' } as const)[value] || value
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

    <section class="overflow-hidden rounded-3xl border border-cream-300 bg-white shadow-sm">
      <div class="grid gap-0 xl:grid-cols-[1fr_420px]">
        <div class="p-6 lg:p-7">
          <div class="flex items-center gap-2 text-xs font-semibold text-bamboo-700">
            <BookOpenCheck class="h-4 w-4" />
            资料中心
          </div>
          <h1 class="mt-3 text-3xl font-semibold text-bamboo-950">把门店变化沉淀成 AI 可用资料</h1>
          <p class="mt-3 max-w-3xl text-sm leading-6 text-warm-600">
            一句话、文件和临时通知都会先进入待确认区。确认后才会进入本店知识库，并在 AI 店长、内容创作和营销策略生成时作为本店事实使用。
          </p>
        </div>
        <div class="border-t border-cream-200 bg-bamboo-950 p-6 text-bamboo-50 xl:border-l xl:border-t-0">
          <div class="grid grid-cols-3 gap-3">
            <div class="rounded-2xl bg-white/10 p-3">
              <div class="text-xs text-bamboo-100/60">已入库</div>
              <div class="mt-2 text-2xl font-bold">{{ items.length }}</div>
            </div>
            <div class="rounded-2xl bg-white/10 p-3">
              <div class="text-xs text-bamboo-100/60">待确认</div>
              <div class="mt-2 text-2xl font-bold">{{ pendingJobs.length }}</div>
            </div>
            <div class="rounded-2xl bg-white/10 p-3">
              <div class="text-xs text-bamboo-100/60">文件</div>
              <div class="mt-2 text-2xl font-bold">{{ files.length }}</div>
            </div>
          </div>
          <button class="mt-4 flex w-full items-center justify-center gap-2 rounded-xl bg-bamboo-100 px-3 py-2 text-xs font-semibold text-bamboo-950 hover:bg-white" @click="load">
            <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': refreshing }" />
            刷新资料
          </button>
        </div>
      </div>
    </section>

    <section class="grid gap-6 xl:grid-cols-[430px_minmax(0,1fr)]">
      <aside class="space-y-4">
        <div class="rounded-3xl border border-cream-300 bg-white p-5 shadow-sm">
          <div class="flex items-start justify-between gap-3">
            <div>
              <h2 class="text-lg font-semibold text-bamboo-950">一句话更新</h2>
              <p class="mt-1 text-xs leading-5 text-warm-500">适合房型变化、早餐时间、设施新增、活动上线和临时通知。</p>
            </div>
            <Sparkles class="h-5 w-5 text-bamboo-700" />
          </div>

          <div class="mt-4 rounded-2xl border border-cream-300 bg-cream-50 p-4">
            <div class="mb-2 flex justify-end">
              <AiPolishControl
                :source-text="input"
                scene="knowledge"
                field="oneSentenceUpdate"
                purpose="把酒店员工输入的资料更新润色成清晰、准确、方便入库的一句话，不新增任何事实。"
                @accept="input = $event"
              />
            </div>
            <textarea
              v-model="input"
              rows="7"
              class="w-full resize-none bg-transparent text-[15px] leading-7 text-bamboo-950 outline-none placeholder:text-warm-400"
              placeholder="例如：二楼今天装修，201-205 房间今天不能出租"
              @keydown.ctrl.enter.prevent="submitText"
            />
            <div class="mt-3 flex items-center justify-between border-t border-cream-200 pt-3">
              <span class="text-[11px] text-warm-500">Ctrl + Enter 快速提交</span>
              <button
                class="inline-flex items-center gap-2 rounded-lg bg-bamboo-800 px-4 py-2 text-xs font-semibold text-bamboo-50 transition hover:bg-bamboo-900 disabled:cursor-not-allowed disabled:bg-cream-300 disabled:text-warm-500"
                :disabled="loading || !input.trim()"
                @click="submitText"
              >
                <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
                <Send v-else class="h-4 w-4" />
                生成待确认
              </button>
            </div>
          </div>

          <div class="mt-4 grid gap-2">
            <button
              v-for="example in examples"
              :key="example"
              class="rounded-xl border border-cream-200 bg-white px-3 py-2.5 text-left text-xs leading-5 text-warm-600 transition hover:border-bamboo-300 hover:bg-bamboo-50 hover:text-bamboo-800"
              @click="useExample(example)"
            >
              {{ example }}
            </button>
          </div>
        </div>

        <div class="rounded-3xl border border-dashed border-cream-300 bg-cream-50 p-5">
          <div class="text-center">
            <FileUp class="mx-auto h-7 w-7 text-bamboo-700/60" />
            <p class="mt-2 text-sm font-medium text-bamboo-950">上传酒店资料文件</p>
            <p class="mt-1 text-xs leading-5 text-warm-500">支持 PDF、DOC、DOCX、TXT、Markdown，单文件不超过 20MB。</p>
            <input ref="fileInput" class="hidden" type="file" accept=".pdf,.doc,.docx,.txt,.md" @change="chooseFile" />
            <button class="mt-3 inline-flex items-center gap-2 rounded-xl bg-bamboo-800 px-4 py-2 text-xs font-semibold text-white disabled:opacity-50" :disabled="uploading" @click="fileInput?.click()">
              <Loader2 v-if="uploading" class="h-4 w-4 animate-spin" />
              <FileUp v-else class="h-4 w-4" />
              {{ uploading ? '正在提取资料' : '选择文件' }}
            </button>
          </div>
          <div class="mt-5 border-t border-cream-200 pt-4">
            <div class="mb-3 flex items-center justify-between">
              <div class="flex items-center gap-2 text-xs font-semibold text-bamboo-800">
                <FileText class="h-4 w-4" />
                上传记录
              </div>
              <span class="text-[11px] text-warm-400">最近 {{ Math.min(files.length, 3) }} 条</span>
            </div>
            <div class="space-y-2">
              <div v-for="file in files.slice(0, 3)" :key="file.id" class="rounded-2xl border border-cream-200 bg-white px-3 py-2.5">
                <p class="truncate text-xs font-medium text-bamboo-950">{{ file.originalName }}</p>
                <div class="mt-1 flex items-center justify-between text-[10px] text-warm-500">
                  <span>{{ formatSize(file.fileSize) }}</span>
                  <span :class="file.parseStatus === 'failed' ? 'text-red-600' : 'text-bamboo-700'">{{ fileStatus(file.parseStatus) }}</span>
                </div>
                <p v-if="file.parseStatus === 'failed' && file.parseError" class="mt-1 line-clamp-2 text-[10px] leading-4 text-red-600">
                  {{ file.parseError }}
                </p>
              </div>
              <div v-if="files.length === 0" class="rounded-2xl border border-dashed border-cream-300 bg-white py-6 text-center text-xs text-warm-500">
                暂无上传记录
              </div>
            </div>
          </div>
        </div>
      </aside>

      <main class="space-y-5">
        <section class="rounded-3xl border border-amber-200 bg-amber-50/70 shadow-sm">
          <div class="flex items-center justify-between border-b border-amber-100 p-5">
            <div>
              <div class="flex items-center gap-2 text-xs font-semibold text-amber-700">
                <Clock3 class="h-4 w-4" />
                待处理
              </div>
              <h2 class="mt-2 text-2xl font-semibold text-bamboo-950">待确认资料</h2>
              <p class="mt-1 text-sm text-warm-500">提交后先在这里确认。只有确认入库的资料才会被 AI 使用。</p>
            </div>
            <span class="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-700">{{ pendingJobs.length }} 条</span>
          </div>
          <div class="grid max-h-[360px] gap-3 overflow-auto p-5 xl:grid-cols-2">
            <div v-for="job in pendingJobs" :key="job.id" class="rounded-2xl border border-amber-200 bg-white p-4 shadow-sm">
              <div class="flex items-center justify-between gap-3">
                <div class="text-xs text-amber-700">{{ formatTime(job.createdAt) }}</div>
                <span class="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-medium text-amber-700">待确认</span>
              </div>
              <p class="mt-2 text-sm font-semibold text-bamboo-950">{{ job.summary || '识别到一条酒店资料更新' }}</p>
              <p class="mt-2 line-clamp-4 text-sm leading-6 text-warm-700">{{ job.inputText }}</p>
              <div class="mt-4 flex flex-wrap gap-2">
                <button class="inline-flex items-center gap-2 rounded-xl bg-amber-600 px-3 py-2 text-xs font-semibold text-white hover:bg-amber-700" @click="confirmJob(job)">
                  <CheckCircle2 class="h-3.5 w-3.5" />
                  确认入库
                </button>
                <button class="inline-flex items-center gap-2 rounded-xl border border-amber-300 bg-white px-3 py-2 text-xs font-semibold text-amber-800 hover:bg-amber-100" @click="cancelJob(job)">
                  <X class="h-3.5 w-3.5" />
                  取消入库
                </button>
              </div>
            </div>
            <div v-if="pendingJobs.length === 0" class="col-span-full rounded-2xl border border-dashed border-amber-200 bg-white/70 py-12 text-center">
              <Clock3 class="mx-auto h-6 w-6 text-amber-500/70" />
              <p class="mt-2 text-sm font-medium text-bamboo-950">暂无待确认资料</p>
              <p class="mt-1 text-xs text-warm-500">左侧提交一句话更新后，会立即出现在这里。</p>
            </div>
          </div>
        </section>

        <section class="rounded-3xl border border-cream-300 bg-white shadow-sm">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-cream-200 p-5">
            <div>
              <div class="flex items-center gap-2 text-xs font-semibold text-bamboo-700">
                <Database class="h-4 w-4" />
                本店知识库
              </div>
              <h2 class="mt-2 text-2xl font-semibold text-bamboo-950">最近确认资料</h2>
              <p class="mt-1 text-sm text-warm-500">首页只展示最近 4 条，完整资料进入知识库列表管理。</p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="category in categories.slice(0, 4)"
                  :key="category.key"
                  class="rounded-full border px-3 py-1.5 text-xs font-medium transition"
                  :class="activeCategory === category.key ? 'border-bamboo-800 bg-bamboo-800 text-white' : 'border-cream-300 bg-cream-50 text-warm-700 hover:border-bamboo-300 hover:bg-bamboo-50'"
                  @click="activeCategory = category.key"
                >
                  {{ category.label }} {{ categoryCount(category.key) }}
                </button>
              </div>
              <RouterLink to="/knowledge/library" class="rounded-full bg-bamboo-800 px-4 py-2 text-xs font-semibold text-white hover:bg-bamboo-900">
                查看全部
              </RouterLink>
            </div>
          </div>

          <div class="grid gap-4 p-5 2xl:grid-cols-2">
            <article v-for="item in previewItems" :key="item.id" class="group rounded-2xl border border-cream-200 bg-cream-50 p-5 transition hover:-translate-y-0.5 hover:border-bamboo-300 hover:bg-white hover:shadow-sm">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <span class="rounded-full bg-bamboo-100 px-2.5 py-1 text-[11px] font-medium text-bamboo-800">{{ categoryLabel(item.category) }}</span>
                  <h3 class="mt-3 text-base font-semibold text-bamboo-950">{{ item.title }}</h3>
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

            <div v-if="filteredItems.length > previewItems.length" class="col-span-full flex items-center justify-center rounded-2xl border border-dashed border-bamboo-200 bg-bamboo-50/60 py-5">
              <RouterLink to="/knowledge/library" class="text-sm font-semibold text-bamboo-800 hover:text-bamboo-950">
                还有 {{ filteredItems.length - previewItems.length }} 条资料，去完整知识库查看 →
              </RouterLink>
            </div>

            <div v-if="filteredItems.length === 0" class="col-span-full rounded-2xl border border-dashed border-cream-300 bg-cream-50 py-16 text-center text-sm text-warm-500">
              暂无资料。先用一句话告诉 AI 你的酒店最新情况。
            </div>
          </div>
        </section>

      </main>
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
          <button class="rounded-xl bg-bamboo-800 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="loading" @click="saveEdit">保存修改</button>
        </footer>
      </section>
    </div>

    <div v-if="deleteOpen" class="fixed inset-0 z-50 grid place-items-center bg-bamboo-950/45 p-4" @click.self="deleteOpen = false">
      <section class="w-full max-w-md rounded-2xl border border-cream-300 bg-white p-5 shadow-2xl">
        <h2 class="font-semibold text-bamboo-950">删除这条知识？</h2>
        <p class="mt-2 text-sm leading-6 text-warm-600">“{{ selectedItem?.title }}”将停止参与后续 AI 生成，历史调用记录仍会保留。</p>
        <div class="mt-5 flex justify-end gap-2">
          <button class="rounded-xl border border-cream-300 px-4 py-2 text-sm text-warm-700" @click="deleteOpen = false">取消</button>
          <button class="rounded-xl bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="loading" @click="confirmDelete">确认删除</button>
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
