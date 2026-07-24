<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  BookOpenCheck,
  FileText,
  History,
  Image,
  Instagram,
  MessageCircleHeart,
  Newspaper,
  Sparkles,
  Video,
} from 'lucide-vue-next'
import { getGenerationHistory } from '@/api/history'
import { getKnowledgeItems } from '@/api/knowledge'
import { useHotelStore } from '@/stores/hotel'
import { useCreditsStore } from '@/stores/credits'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'

interface ModuleOption {
  key: string
  label: string
  desc: string
  icon: any
  route: string
  tone: string
  fields: string[]
  outputs: string[]
}

const modules: ModuleOption[] = [
  { key: 'xhs', label: '小红书图文', desc: '真实种草内容与配图建议', icon: Instagram, route: '/xhs', tone: 'rose', fields: ['内容方向', '目标语气', '图片比例', '补充要求'], outputs: ['标题方案', '正文与标签', '配图建议'] },
  { key: 'wechat', label: '朋友圈文案', desc: '按发布时段组织私域内容', icon: MessageCircleHeart, route: '/wechat', tone: 'green', fields: ['发布时段', '文案风格', '内容长度', '补充要求'], outputs: ['分时段文案', '配图建议', '发布时间'] },
  { key: 'article', label: '公众号推文', desc: '完整长文与推送结构', icon: Newspaper, route: '/article', tone: 'blue', fields: ['文章主题', '文章风格', '内容长度', '参考文件'], outputs: ['文章标题', '摘要与正文', '头图建议'] },
  { key: 'video', label: '短视频脚本', desc: '口播、分镜和发布建议', icon: Video, route: '/video', tone: 'violet', fields: ['核心卖点', '创作视角', '视频风格', '目标时长'], outputs: ['口播脚本', '镜头安排', 'BGM 建议'] },
  { key: 'poster', label: '营销海报', desc: '按用途和比例生成真实图片', icon: Image, route: '/poster', tone: 'amber', fields: ['营销场景', '画面描述', '目标客群', '图片比例'], outputs: ['海报图片', '下载素材', '生成记录'] },
  { key: 'reply', label: '点评回复', desc: '针对真实评价组织回复', icon: FileText, route: '/reply', tone: 'slate', fields: ['评价原文', '评价类型', '回复语气', '补充要求'], outputs: ['平台回复', '服务补救表达', '沟通建议'] },
]

const router = useRouter()
const hotel = useHotelStore()
const credits = useCreditsStore()
const selectedKey = ref('xhs')
const knowledgeCount = ref(0)
const historyItems = ref<any[]>([])
const loadingHistory = ref(false)

const selected = computed(() => modules.find(item => item.key === selectedKey.value) || modules[0])
const hotelSummary = computed(() => [hotel.config.name, hotel.config.city, hotel.config.type].filter(Boolean).join(' / ') || '酒店基础资料尚未完善')

function selectModule(module: ModuleOption) {
  selectedKey.value = module.key
  saveAiPageState('create-center', { selectedModuleKey: module.key })
  loadHistory()
}

function openWorkspace() {
  router.push(selected.value.route)
}

async function loadHistory() {
  loadingHistory.value = true
  try {
    const { data: response } = await getGenerationHistory(selected.value.key, 5)
    const list = response.data || response
    historyItems.value = Array.isArray(list) ? list.slice(0, 5) : []
  } catch {
    historyItems.value = []
  } finally {
    loadingHistory.value = false
  }
}

async function loadSummary() {
  try {
    const { data: response } = await getKnowledgeItems(undefined, 200)
    const list = response.data || response
    knowledgeCount.value = Array.isArray(list) ? list.length : 0
  } catch {
    knowledgeCount.value = 0
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(5, 16) : ''
}

onMounted(() => {
  const restored = loadAiPageState<{ selectedModuleKey?: string }>('create-center')
  if (restored?.selectedModuleKey && modules.some(item => item.key === restored.selectedModuleKey)) {
    selectedKey.value = restored.selectedModuleKey
  }
  if (!hotel.config.name) hotel.loadFromApi().catch(() => {})
  credits.loadFromApi().catch(() => {})
  loadSummary()
  loadHistory()
})
</script>

<template>
  <div class="mx-auto max-w-[1480px] space-y-5 pb-8">
    <header class="flex flex-wrap items-end justify-between gap-5 border-b border-cream-300 pb-5">
      <div>
        <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-700"><Sparkles class="h-4 w-4" />AI 创作中心</div>
        <h1 class="mt-2 text-3xl font-semibold text-bamboo-950">选择任务，进入对应创作工作台</h1>
        <p class="mt-2 text-sm leading-6 text-warm-600">每项功能使用独立智能体和专属参数，创作中心不再维护重复的通用表单。</p>
      </div>
      <div class="flex flex-wrap gap-2 text-xs text-warm-600">
        <span class="rounded-lg border border-cream-300 bg-white px-3 py-2">{{ hotelSummary }}</span>
        <span class="rounded-lg border border-cream-300 bg-white px-3 py-2">{{ knowledgeCount }} 条有效知识</span>
        <span class="rounded-lg border border-cream-300 bg-white px-3 py-2">{{ credits.currentBalance || 0 }} 算力</span>
      </div>
    </header>

    <div class="create-center-layout grid gap-5">
      <section class="create-center-modules">
        <div class="mb-3 flex items-center justify-between"><h2 class="text-sm font-semibold text-bamboo-950">内容类型</h2><span class="text-xs text-warm-500">{{ modules.length }} 项能力</span></div>
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
          <button
            v-for="module in modules"
            :key="module.key"
            class="min-h-[126px] rounded-xl border bg-white p-4 text-left transition hover:-translate-y-0.5 hover:border-bamboo-300 hover:shadow-sm"
            :class="selected.key === module.key ? 'border-bamboo-700 ring-1 ring-bamboo-700' : 'border-cream-300'"
            @click="selectModule(module)"
          >
            <span class="flex h-9 w-9 items-center justify-center rounded-lg bg-bamboo-50 text-bamboo-800"><component :is="module.icon" class="h-5 w-5" /></span>
            <span class="mt-3 block text-sm font-semibold text-bamboo-950">{{ module.label }}</span>
            <span class="mt-1 block text-xs leading-5 text-warm-500">{{ module.desc }}</span>
          </button>
        </div>
      </section>

      <main class="create-center-main overflow-hidden rounded-xl border border-cream-300 bg-white shadow-sm">
        <div class="flex flex-wrap items-start justify-between gap-4 border-b border-cream-300 px-6 py-5">
          <div class="flex items-start gap-4">
            <span class="flex h-12 w-12 items-center justify-center rounded-xl bg-bamboo-900 text-white"><component :is="selected.icon" class="h-5 w-5" /></span>
            <div><div class="text-xs font-semibold text-bamboo-700">当前创作任务</div><h2 class="mt-1 text-2xl font-semibold text-bamboo-950">{{ selected.label }}</h2><p class="mt-1 text-sm text-warm-500">{{ selected.desc }}</p></div>
          </div>
          <button class="inline-flex items-center gap-2 rounded-xl bg-bamboo-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-bamboo-800" @click="openWorkspace">进入工作台<ArrowRight class="h-4 w-4" /></button>
        </div>

        <div class="grid gap-8 p-6 md:grid-cols-2">
          <section>
            <h3 class="text-sm font-semibold text-bamboo-950">需要配置</h3>
            <div class="mt-3 space-y-2">
              <div v-for="(field, index) in selected.fields" :key="field" class="flex items-center gap-3 rounded-lg bg-cream-50 px-3 py-2.5 text-sm text-warm-700"><span class="grid h-6 w-6 place-items-center rounded-md bg-white text-xs font-semibold text-bamboo-700">{{ index + 1 }}</span>{{ field }}</div>
            </div>
          </section>
          <section>
            <h3 class="text-sm font-semibold text-bamboo-950">生成结果</h3>
            <div class="mt-3 space-y-2">
              <div v-for="output in selected.outputs" :key="output" class="flex items-center gap-3 rounded-lg bg-bamboo-50 px-3 py-2.5 text-sm text-bamboo-900"><BookOpenCheck class="h-4 w-4 text-bamboo-700" />{{ output }}</div>
            </div>
          </section>
        </div>

        <div class="border-t border-cream-300 bg-cream-50 px-6 py-5">
          <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950"><BookOpenCheck class="h-4 w-4 text-bamboo-700" />资料引用规则</div>
          <p class="mt-2 text-sm leading-6 text-warm-600">后端会自动加入本店基础资料、房型挂牌价和有效知识。未接入 PMS、OTA、订单或实时房态，智能体不得虚构相关数据。</p>
          <button class="mt-3 text-sm font-semibold text-bamboo-700 hover:text-bamboo-900" @click="router.push('/knowledge')">管理本店知识 →</button>
        </div>
      </main>

      <aside class="create-center-history">
        <div class="mb-3 flex items-center justify-between"><h2 class="flex items-center gap-2 text-sm font-semibold text-bamboo-950"><History class="h-4 w-4" />最近生成</h2><button class="text-xs font-semibold text-bamboo-700" @click="router.push('/history')">全部历史</button></div>
        <div class="space-y-3">
          <button v-for="item in historyItems" :key="item.id" class="w-full rounded-xl border border-cream-300 bg-white p-4 text-left transition hover:border-bamboo-300" @click="router.push('/history')">
            <div class="flex items-center justify-between gap-3"><span class="truncate text-sm font-semibold text-bamboo-950">{{ item.title || selected.label }}</span><span class="shrink-0 text-[11px] text-warm-400">{{ formatTime(item.createdAt) }}</span></div>
            <p class="mt-2 line-clamp-2 text-xs leading-5 text-warm-500">{{ item.prompt || item.outputContent || '查看本次生成内容' }}</p>
          </button>
          <div v-if="!loadingHistory && !historyItems.length" class="rounded-xl border border-dashed border-cream-300 bg-cream-50 px-4 py-10 text-center text-sm text-warm-500">该功能暂无生成记录</div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.create-center-layout {
  grid-template-columns: minmax(0, 1fr);
}

@media (min-width: 1100px) {
  .create-center-layout {
    grid-template-columns: 320px minmax(0, 1fr);
  }

  .create-center-history {
    grid-column: 1 / -1;
  }
}

@media (min-width: 1500px) {
  .create-center-layout {
    grid-template-columns: 360px minmax(0, 1fr) 320px;
  }

  .create-center-history {
    grid-column: auto;
  }
}
</style>
