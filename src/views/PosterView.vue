<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Image, Sparkles, Zap, Upload, Download, Copy,
  ImagePlus, Wand2, Loader2, X
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { generateContent, getTaskResult } from '@/api/content'
import api from '@/api'

const hotel = useHotelStore()
const router = useRouter()

const mode = ref<'text2img' | 'beautify'>('text2img')

// ====== 文生图 ======
const t2iTheme = ref('')
const t2iContent = ref('')
const t2iStyle = ref('chinese')
const t2iGenerating = ref(false)
const t2iGenerated = ref(false)
const t2iImageUrl = ref('')
const t2iError = ref('')

const t2iStyles = [
  { v: 'chinese', label: '中式禅意' },
  { v: 'minimal', label: '轻奢简约' },
  { v: 'dark', label: '深夜极简' },
  { v: 'warm', label: '温暖治愈' },
]

const t2iPlaceholders: Record<string, string> = {
  chinese: '端午特惠 低至6折',
  minimal: '夏日新菜单 尝鲜特价',
  dark: '星空露营 限时预订',
  warm: '亲子度假 全家出行',
}

const t2iSizes = [
  { v: '3:4', label: '3:4 竖版' },
  { v: '1:1', label: '1:1 方形' },
  { v: '16:9', label: '16:9 横版' },
]
const t2iSize = ref('3:4')

// ====== 图片美化 ======
const beautifyDone = ref(false)
const uploadedFile = ref<File | null>(null)
const uploadedPreview = ref('')
const beautifyDesc = ref('')
const beautifySize = ref('3:4')
const beautifyGenerating = ref(false)
const beautifyResult = ref('')

function selectT2iStyle(v: string) {
  t2iStyle.value = v
  t2iTheme.value = t2iPlaceholders[v] || ''
}

// ====== 文生图 ======
async function generateT2I() {
  if (!t2iTheme.value.trim()) return
  t2iGenerating.value = true
  t2iGenerated.value = false
  t2iImageUrl.value = ''
  t2iError.value = ''

  try {
    const { data: res } = await generateContent('poster', {
      theme: t2iTheme.value,
      content: t2iContent.value,
      style: t2iStyle.value,
    })
    const d = res.data || res
    const taskId = d.taskId
    if (!taskId) throw new Error('未返回任务ID')

    // 轮询结果（图片生成可能需要 30-90 秒）
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 3000))
      const { data: tr } = await getTaskResult(taskId)
      const task = tr.data || tr
      if (task.status === 'done') {
        t2iImageUrl.value = task.content || ''
        t2iGenerated.value = true
        break
      }
      if (task.status === 'error') {
        t2iError.value = task.error || '生成失败'
        break
      }
    }
    if (!t2iGenerated.value && !t2iError.value) {
      t2iError.value = '生成超时，请稍后重试'
    }
  } catch (err: any) {
    t2iError.value = err.message || '生成失败'
  } finally {
    t2iGenerating.value = false
  }
}

// ====== 图片上传 ======
function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadedFile.value = file
  beautifyDone.value = false
  beautifyResult.value = ''

  const reader = new FileReader()
  reader.onload = (ev) => {
    uploadedPreview.value = ev.target?.result as string
  }
  reader.readAsDataURL(file)
}

function triggerUpload() {
  document.getElementById('beautify-file-input')?.click()
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  const file = e.dataTransfer?.files?.[0]
  if (!file) return
  const input = document.getElementById('beautify-file-input') as HTMLInputElement
  if (!input) return
  const dt = new DataTransfer()
  dt.items.add(file)
  input.files = dt.files
  handleFileChange({ target: input } as unknown as Event)
}

// ====== 开始美化 ======
async function startBeautify() {
  if (!uploadedFile.value || !uploadedPreview.value) return
  beautifyGenerating.value = true
  beautifyResult.value = ''

  try {
    const { data: res } = await api.post('/api/content/repair', {
      imageData: uploadedPreview.value,
      prompt: beautifyDesc.value,
      size: beautifySize.value,
    })
    const d = res.data || res
    beautifyResult.value = d.imageUrl || ''
    beautifyDone.value = true
  } catch (err: any) {
    flashToast('美化失败: ' + (err.message || '未知错误'))
    beautifyDone.value = false
  } finally {
    beautifyGenerating.value = false
  }
}

// ====== 复制/下载 ======
const toast = ref('')

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1500)
}
</script>

<template>
  <div class="h-full flex flex-col">
    <!-- Toast -->
    <div v-if="toast" class="fixed top-6 right-6 z-50 bg-bamboo-800 text-cream-100 px-5 py-3 rounded-lg shadow-lg text-sm">
      {{ toast }}
    </div>

    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-start gap-3">
        <Image class="w-5 h-5 text-bamboo-700 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">营销海报</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">文生图 / 图片美化</p>
        </div>
      </div>
      <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
        <Zap class="w-3 h-3" />算力流水
      </button>
    </div>

    <!-- Tab -->
    <div class="flex items-center gap-2 mb-4">
      <div class="flex gap-1 bg-cream-100 rounded-lg p-0.5 self-start">
        <button @click="mode = 'text2img'"
          :class="['flex items-center gap-1.5 px-4 py-1.5 rounded-md text-xs font-medium transition-colors', mode === 'text2img' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-warm-700']">
          <Sparkles class="w-3.5 h-3.5" />文生图
        </button>
        <button @click="mode = 'beautify'"
          :class="['flex items-center gap-1.5 px-4 py-1.5 rounded-md text-xs font-medium transition-colors', mode === 'beautify' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-warm-700']">
          <Wand2 class="w-3.5 h-3.5" />图片美化
        </button>
      </div>
      <span v-if="mode === 'text2img'" class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2 py-1 rounded-full">消耗 30 算力 / 次</span>
      <span v-if="mode === 'beautify'" class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2 py-1 rounded-full">消耗 20 算力 / 次</span>
    </div>

    <!-- ============================== 文生图 ============================== -->
    <div v-if="mode === 'text2img'" class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- 左栏 -->
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto space-y-4">
        <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase">生成设置</div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">海报风格</div>
          <div class="grid grid-cols-2 gap-1.5">
            <button v-for="s in t2iStyles" :key="s.v"
              @click="selectT2iStyle(s.v)"
              :class="['px-3 py-2 rounded-lg text-[11px] border text-center transition-colors', t2iStyle === s.v ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-800 font-medium' : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-300']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">图片尺寸</div>
          <div class="flex gap-1.5">
            <button v-for="s in t2iSizes" :key="s.v"
              @click="t2iSize = s.v"
              :class="['px-3 py-1 rounded-full text-[11px] border transition-colors', t2iSize === s.v ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">海报主题</div>
          <input
            v-model="t2iTheme"
            type="text"
            class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400"
            :placeholder="'输入海报主题，如：端午特惠 低至6折'"
          />
        </div>

        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">海报内容</div>
          <textarea
            v-model="t2iContent"
            rows="2"
            class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400 resize-none"
            placeholder="输入海报副标题或补充文案..."
          />
        </div>

        <button @click="generateT2I" :disabled="t2iGenerating || !t2iTheme.trim()"
          class="w-full py-2.5 rounded-lg bg-bamboo-800 text-bamboo-100 text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors">
          <Sparkles class="w-4 h-4" />AI 生成海报
        </button>
      </div>

      <!-- 右栏 -->
      <div class="bg-cream-50 p-4 overflow-y-auto flex items-center justify-center">
        <!-- 空状态 -->
        <div v-if="!t2iGenerating && !t2iGenerated" class="flex flex-col items-center gap-3 text-warm-400">
          <Image class="w-10 h-10 opacity-25" />
          <p class="text-[13px]">选择风格、输入主题后点击生成</p>
        </div>

        <!-- Loading -->
        <div v-if="t2iGenerating" class="flex flex-col items-center gap-3">
          <Loader2 class="w-7 h-7 text-bamboo-800 animate-spin" />
          <p class="text-[12px] text-warm-500">AI 正在生成海报...</p>
          <p class="text-[10px] text-warm-400">图片生成约需 30-90 秒，请耐心等待</p>
        </div>

        <!-- 错误 -->
        <div v-if="t2iError && !t2iGenerating" class="flex flex-col items-center gap-3 text-rose-500">
          <p class="text-[13px]">{{ t2iError }}</p>
          <button @click="generateT2I" class="text-[12px] px-4 py-1.5 rounded-lg border border-rose-200 text-rose-600 hover:bg-rose-50 transition-colors">重新生成</button>
        </div>

        <!-- 结果 -->
        <div v-if="t2iGenerated && t2iImageUrl" class="w-full max-w-sm">
          <div :class="[
            'border-2 border-cream-300 rounded-xl overflow-hidden shadow-lg mx-auto',
            t2iSize === '3:4' ? 'aspect-[3/4]' : t2iSize === '1:1' ? 'aspect-square' : 'aspect-video'
          ]">
            <img :src="t2iImageUrl" class="w-full h-full object-cover" />
          </div>
          <div class="flex gap-2 mt-4 justify-center">
            <button @click="flashToast('已复制')" class="text-[12px] px-3 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-700 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
              <Copy class="w-3.5 h-3.5" />复制海报
            </button>
            <a :href="t2iImageUrl" target="_blank" download class="text-[12px] px-3 py-1.5 rounded-lg bg-bamboo-800 text-bamboo-100 hover:bg-bamboo-900 transition-colors flex items-center gap-1.5">
              <Download class="w-3.5 h-3.5" />下载海报
            </a>
          </div>
        </div>
      </div>
    </div>

    <!-- ============================== 图片美化 ============================== -->
    <div v-if="mode === 'beautify'" class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- 左栏 -->
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto space-y-4">
        <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase">美化设置</div>

        <!-- 上传区 -->
        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">上传图片</div>
          <input id="beautify-file-input" type="file" accept="image/*" class="hidden" @change="handleFileChange" />
          <div
            @click="triggerUpload"
            @dragover.prevent
            @drop="onDrop"
            :class="[
              'border-2 border-dashed rounded-lg p-6 text-center cursor-pointer transition-colors',
              uploadedPreview
                ? 'border-bamboo-400 bg-bamboo-50/30'
                : 'border-cream-300 bg-cream-50 hover:border-bamboo-300 hover:bg-bamboo-50/20'
            ]"
          >
            <div v-if="!uploadedPreview" class="flex flex-col items-center gap-2 text-warm-400">
              <Upload class="w-6 h-6" />
              <span class="text-[11px]">拖拽图片到此处</span>
              <span class="text-[10px]">或点击上传</span>
            </div>
            <div v-else class="flex items-center gap-3">
              <img :src="uploadedPreview" class="w-16 h-16 object-cover rounded-lg" />
              <div class="text-left flex-1 min-w-0">
                <div class="text-[11px] font-medium text-bamboo-800 truncate">{{ uploadedFile?.name }}</div>
                <div class="text-[10px] text-warm-500">已上传，可开始美化</div>
              </div>
              <button @click.stop="uploadedPreview = ''; uploadedFile = null; beautifyDone = false" class="text-warm-400 hover:text-rose-500">
                <X class="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        <!-- 美化描述 -->
        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">美化描述</div>
          <textarea
            v-model="beautifyDesc"
            rows="2"
            class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400 resize-none"
            placeholder="描述想要的图片效果，如：提高亮度、增强绿色、暖色调..."
          />
        </div>

        <!-- 图片尺寸 -->
        <div>
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">图片尺寸</div>
          <div class="flex gap-1.5">
            <button v-for="s in t2iSizes" :key="s.v"
              @click="beautifySize = s.v"
              :class="['px-3 py-1 rounded-full text-[11px] border transition-colors', beautifySize === s.v ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']">
              {{ s.label }}
            </button>
          </div>
        </div>

        <button @click="startBeautify" :disabled="beautifyGenerating || !uploadedFile"
          class="w-full py-2.5 rounded-lg bg-bamboo-800 text-bamboo-100 text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors">
          <Wand2 class="w-4 h-4" />AI 美化图片
        </button>
      </div>

      <!-- 右栏 -->
      <div class="bg-cream-50 p-4 overflow-y-auto">
        <!-- 空状态 -->
        <div v-if="!beautifyGenerating && !beautifyDone" class="flex flex-col items-center justify-center h-full min-h-[360px] gap-3 text-warm-400">
          <ImagePlus class="w-10 h-10 opacity-25" />
          <p class="text-[13px]">上传图片后点击「AI 美化图片」</p>
          <p class="text-[11px] opacity-70">AI 将根据选择风格自动优化色彩、构图与氛围</p>
        </div>

        <!-- Loading -->
        <div v-if="beautifyGenerating" class="flex flex-col items-center justify-center h-full gap-3">
          <Loader2 class="w-7 h-7 text-bamboo-800 animate-spin" />
          <p class="text-[12px] text-warm-500">AI 正在美化图片...</p>
        </div>

        <!-- 美化结果（上下对比） -->
        <div v-if="beautifyDone" class="space-y-4">
          <div>
            <div class="text-[10px] font-semibold text-warm-500 tracking-wider mb-2">原图</div>
            <img :src="uploadedPreview" class="w-full rounded-lg border border-cream-200" />
          </div>
          <div>
            <div class="text-[10px] font-semibold text-warm-500 tracking-wider mb-2">
             美化效果
            </div>
            <div class="relative rounded-lg border border-cream-200 overflow-hidden">
              <img :src="beautifyResult || uploadedPreview" class="w-full" />
            </div>
          </div>
          <div class="flex gap-2 justify-center">
            <button @click="flashToast('已复制')" class="text-[12px] px-3 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-700 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
              <Copy class="w-3.5 h-3.5" />复制图片
            </button>
            <button class="text-[12px] px-3 py-1.5 rounded-lg bg-bamboo-800 text-bamboo-100 hover:bg-bamboo-900 transition-colors flex items-center gap-1.5">
              <Download class="w-3.5 h-3.5" />下载原图
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
