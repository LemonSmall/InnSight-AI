<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { generateContent, getTaskResult } from '@/api/content'
import {
  Newspaper, Zap, Upload, Copy, Loader2, CheckCircle2,
  Sparkles, FileText, Image, X, Monitor, Smartphone,
  Clock
} from 'lucide-vue-next'

const router = useRouter()

// ====== 步骤 ======
const step = ref<'config' | 'generating' | 'done'>('config')

// ====== 上传 ======
const uploadedFile = ref<File | null>(null)
const uploadedPreview = ref(false)

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadedFile.value = file
  uploadedPreview.value = true
}

function triggerUpload() {
  document.getElementById('article-file-input')?.click()
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  const file = e.dataTransfer?.files?.[0]
  if (!file) return
  const input = document.getElementById('article-file-input') as HTMLInputElement
  if (!input) return
  const dt = new DataTransfer()
  dt.items.add(file)
  input.files = dt.files
  handleFileChange({ target: input } as unknown as Event)
}

// ====== 配置 ======
const selectedStyle = ref('teal_tech')
const selectedLength = ref('medium')
const withImage = ref(true)
const imageCount = ref(3)
const articleImageUrl = ref('')
const articleTitle = ref('')

const styles = [
  {
    id: 'teal_tech',
    label: '青绿技术型',
    desc: '渐变背景，适合知识分享',
    bg: 'from-emerald-600 to-teal-500',
    accent: '#0D9488',
  },
  {
    id: 'wechat_green',
    label: '微信绿专业型',
    desc: '经典公众号风格',
    bg: 'from-green-600 to-green-500',
    accent: '#16A34A',
  },
  {
    id: 'restrained_pro',
    label: '克制专业型',
    desc: '极简留白，适合深度长文',
    bg: 'from-slate-600 to-slate-500',
    accent: '#475569',
  },
  {
    id: 'checklist',
    label: '选型清单型',
    desc: '分点清晰，适合攻略',
    bg: 'from-amber-500 to-orange-400',
    accent: '#D97706',
  },
  {
    id: 'oral_quote',
    label: '口语金句型',
    desc: '大段引述，适合人物访谈',
    bg: 'from-rose-500 to-pink-400',
    accent: '#E11D48',
  },
]

const lengths = [
  { id: 'short', label: '短文', desc: '约800字' },
  { id: 'medium', label: '中篇', desc: '约1500字' },
  { id: 'long', label: '长文', desc: '约2500字' },
]

const styleMeta = computed(() => styles.find(s => s.id === selectedStyle.value))

// ====== 模拟生成 ======
const progress = ref(0)
const currentStep = ref('')
const progressSteps = [
  '上传中',
  '音频处理中',
  '语音转写中',
  '文章生成中',
  '配图生成中',
  '排版渲染中',
  '质量检查中',
]

async function startGenerate() {
  step.value = 'generating'
  progress.value = 0

  try {
    const { data: res } = await generateContent('article', {
      style: selectedStyle.value,
      length: selectedLength.value,
      withImage: withImage.value,
      imageCount: imageCount.value,
      title: articleTitle.value,
      topic: '莫干山民宿运营',
    })
    const d = res.data || res
    const taskId = d.taskId

    let attempts = 0
    while (attempts < 30) {
      await new Promise(r => setTimeout(r, 1000))
      currentStep.value = progressSteps[Math.min(attempts, progressSteps.length - 1)]
      progress.value = Math.round((attempts + 1) / 30 * 100)
      const { data: tr } = await getTaskResult(taskId)
      const task = (tr as any).data || tr
      if (task.status === 'done') {
        progress.value = 100
        // 解析配图
        if (task.content) {
          try {
            const parsed = JSON.parse(task.content)
            if (parsed.imageUrl) articleImageUrl.value = parsed.imageUrl
          } catch { /* 纯文本，忽略 */ }
        }
        step.value = 'done'
        return
      }
      if (task.status === 'failed') break
      attempts++
    }
  } catch { /* fallback */ }

  for (let i = 0; i < progressSteps.length; i++) {
    currentStep.value = progressSteps[i]
    await new Promise(r => setTimeout(r, 600 + Math.random() * 400))
    progress.value = Math.round(((i + 1) / progressSteps.length) * 100)
  }

  step.value = 'done'
}

// ====== 结果 ======
const sections = ref([
  {
    heading: '为什么要来莫干山',
    paragraphs: [
      '莫干山，位于浙江德清，是中国四大避暑胜地之一。每年夏天，这里绿意盎然的竹海和凉爽宜人的气候吸引了无数游客。但对于民宿经营者来说，莫干山不仅是一个风景区，更是一块需要用心经营的品牌阵地。',
      '尤其是节假日前的蓄水期，如何通过公众号推文精准触达目标客群、传递民宿的独特气质，是每位民宿主都在思考的问题。',
    ],
    image: '竹林全景',
  },
  {
    heading: '雨天是最佳卖点',
    paragraphs: [
      '很多人觉得雨天会影响出游，但在莫干山，雨天反而是一张王牌。雨中的竹林雾气缭绕，私汤温泉蒸汽升腾——这种画面感，恰恰是公众号推文最需要的「视觉钩子」。',
      '我们建议将「雨天」打造为内容主线，用一个充满情绪的画面、一段细腻的文字，唤起读者对山居生活的向往。配上高质量实拍图片，点击率可以提升40%以上。',
    ],
    image: '雨中私汤',
  },
  {
    heading: '端午蓄水三件事',
    paragraphs: [
      '第一，提前7天发布预热推文，用「倒计时」+「限量房源」制造紧迫感；第二，结合端午节令元素（粽子、艾草、龙舟），推出限定套餐的图文介绍；第三，在推文末尾嵌入小程序预订入口，缩短转化路径。',
      '数据显示，带有小程序入口的推文平均转化率比纯图文推文高出3倍。建议使用微信原生模板消息配合推文，形成「内容种草 → 私域触达 → 一键预订」的完整链路。',
    ],
    image: '端午布置',
  },
])

const ending = ref('这个端午，来莫干山，泡一池私汤，听一夜竹雨。我们在松间等你。')

// ====== 复制 ======
const toast = ref('')
const fullHtml = computed(() => {
  const style = styleMeta.value
  const accent = style?.accent || '#0D9488'

  let html = `<div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif; max-width: 640px; margin: 0 auto; color: #333; line-height: 1.8; font-size: 15px;">\n\n`

  // 头部
  html += `<div style="background: linear-gradient(135deg, ${accent}22, ${accent}44); padding: 32px 24px 24px; text-align: center; border-radius: 0 0 24px 24px;">\n`
  html += `  <p style="font-size: 12px; color: ${accent}; letter-spacing: 2px; margin-bottom: 8px;">${new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })} · 莫干山</p>\n`
  html += `  <h1 style="font-size: 22px; font-weight: 700; color: #1F2937; margin: 0 0 8px; line-height: 1.4;">${articleTitle.value || '莫干山民宿 · 运营手记'}</h1>\n`
  html += `  <p style="font-size: 13px; color: #6B7280; margin: 0;">${styles.find(s => s.id === selectedStyle.value)?.label}排版 · ${lengths.find(l => l.id === selectedLength.value)?.label}</p>\n`
  html += `</div>\n\n`

  // 导语
  html += `<div style="padding: 24px 24px 16px;">\n`
  html += `  <div style="padding: 16px 20px; background: #f0fdf4; border-left: 4px solid ${accent}; border-radius: 0 8px 8px 0; font-size: 14px; color: #444;">\n`
  html += `    暑期将至，端午在即。整理了一份关于莫干山民宿运营的思考与行动清单，分享给同样在路上的你。\n`
  html += `  </div>\n`
  html += `</div>\n\n`

  // 章节
  sections.value.forEach((sec, i) => {
    html += `<div style="padding: 8px 24px 0;">\n`
    html += `  <h2 style="font-size: 18px; font-weight: 700; color: #1F2937; margin: 20px 0 12px; padding-bottom: 8px; border-bottom: 3px solid ${accent}; display: inline-block;">${sec.heading}</h2>\n`
    sec.paragraphs.forEach(p => {
      html += `  <p style="margin: 0 0 12px; font-size: 15px; color: #4B5563; letter-spacing: 0.3px;">${p}</p>\n`
    })
    if (withImage.value && i < imageCount.value) {
      html += `  <div style="margin: 12px 0 16px; text-align: center; padding: 20px; background: #f9fafb; border-radius: 12px; color: #9CA3AF; font-size: 12px;">[ 配图${i + 1}：${sec.image} · ${articleTitle.value || '莫干山民宿'} ]</div>\n`
    }
    html += `</div>\n\n`
  })

  // 结尾
  html += `<div style="padding: 16px 24px 24px;">\n`
  html += `  <div style="text-align: center; padding: 24px 20px; background: linear-gradient(135deg, ${accent}15, ${accent}08); border-radius: 16px; margin-top: 16px;">\n`
  html += `    <p style="font-size: 16px; color: ${accent}; font-weight: 600; margin: 0 0 12px;">${ending.value}</p>\n`
  html += `    <p style="font-size: 12px; color: #9CA3AF; margin: 0;">📍 莫干山 · 松间山野民宿</p>\n`
  html += `    <p style="font-size: 11px; color: #9CA3AF; margin: 4px 0 0;">扫码预订 →</p>\n`
  html += `  </div>\n`
  html += `</div>\n\n`

  html += `</div>`
  return html
})

// 纯文本版（用于公众号编辑器粘贴）
const plainText = computed(() => {
  let text = ``
  if (articleTitle.value) text += `${articleTitle.value}\n\n`
  text += `暑期将至，端午在即。整理了一份关于莫干山民宿运营的思考与行动清单，分享给同样在路上的你。\n\n`
  sections.value.forEach(sec => {
    text += `【${sec.heading}】\n\n`
    sec.paragraphs.forEach(p => { text += `${p}\n\n` })
  })
  text += ending.value
  return text
})

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1500)
}

async function copyHtml() {
  try {
    await navigator.clipboard.write([
      new ClipboardItem({
        'text/html': new Blob([fullHtml.value], { type: 'text/html' }),
        'text/plain': new Blob([plainText.value], { type: 'text/plain' }),
      }),
    ])
    flashToast('已复制到剪贴板，可直接粘贴到公众号编辑器')
  } catch {
    // fallback: plain text only
    try {
      await navigator.clipboard.writeText(plainText.value)
      flashToast('已复制纯文本')
    } catch {
      flashToast('复制失败，请手动复制')
    }
  }
}

// ====== 预览模式 ======
const previewMode = ref<'phone' | 'desktop'>('phone')
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
        <Newspaper class="w-5 h-5 text-bamboo-700 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">公众号推文</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">视频转文章 · 智能排版 · 一键复制到公众号</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <Zap class="w-3 h-3" />算力流水
        </button>
        <span class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2 py-1 rounded-full">消耗 15 算力 / 次</span>
      </div>
    </div>

    <!-- ============================== 配置阶段 ============================== -->
    <div v-if="step === 'config'" class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- 左栏 -->
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto space-y-4">
        <!-- 视频上传 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">视频上传</div>
          <input id="article-file-input" type="file" accept="video/*" class="hidden" @change="handleFileChange" />
          <div
            @click="triggerUpload"
            @dragover.prevent
            @drop="onDrop"
            :class="[
              'border-2 border-dashed rounded-lg p-5 text-center cursor-pointer transition-colors mb-3',
              uploadedPreview
                ? 'border-bamboo-400 bg-bamboo-50/30'
                : 'border-cream-300 bg-cream-50 hover:border-bamboo-300'
            ]"
          >
            <div v-if="!uploadedPreview" class="flex flex-col items-center gap-2 text-warm-400">
              <Upload class="w-7 h-7" />
              <span class="text-[12px]">拖拽视频到此处</span>
              <span class="text-[10px]">或点击上传 · MP4/MOV</span>
            </div>
            <div v-else class="flex items-center gap-3">
              <div class="w-12 h-12 rounded-lg bg-bamboo-100 flex items-center justify-center flex-shrink-0">
                <FileText class="w-5 h-5 text-bamboo-700" />
              </div>
              <div class="text-left flex-1 min-w-0">
                <div class="text-[11px] font-medium text-bamboo-800 truncate">{{ uploadedFile?.name }}</div>
                <div class="text-[10px] text-warm-500">已就绪</div>
              </div>
              <button @click.stop="uploadedPreview = false; uploadedFile = null" class="text-warm-400 hover:text-rose-500">
                <X class="w-4 h-4" />
              </button>
            </div>
          </div>
          <p class="text-[10px] text-warm-500">支持抖音/口播/宣传片等视频，AI 自动提取语音转文字并扩写为公众号文章</p>
        </div>

        <!-- 排版预设 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">排版预设</div>
          <div class="space-y-1.5">
            <button
              v-for="s in styles" :key="s.id"
              @click="selectedStyle = s.id"
              :class="[
                'w-full text-left px-3 py-2 rounded-lg border transition-colors',
                selectedStyle === s.id
                  ? 'border-bamboo-400 bg-bamboo-50'
                  : 'border-cream-200 bg-white hover:border-bamboo-200'
              ]"
            >
              <div class="flex items-center gap-2.5">
                <div :class="['w-6 h-6 rounded-md bg-gradient-to-br', s.bg, 'flex-shrink-0']" />
                <div class="flex-1 min-w-0">
                  <div class="text-[11px] font-medium" :class="selectedStyle === s.id ? 'text-bamboo-800' : 'text-warm-700'">
                    {{ s.label }}
                  </div>
                  <div class="text-[10px] text-warm-500">{{ s.desc }}</div>
                </div>
                <div v-if="selectedStyle === s.id" class="w-2.5 h-2.5 rounded-full bg-bamboo-700 flex-shrink-0" />
              </div>
            </button>
          </div>
        </div>

        <!-- 文章长度 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">文章长度</div>
          <div class="flex gap-1.5">
            <button
              v-for="l in lengths" :key="l.id"
              @click="selectedLength = l.id"
              :class="[
                'flex-1 py-2 rounded-lg text-[11px] text-center border transition-colors',
                selectedLength === l.id
                  ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-800 font-medium'
                  : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-200'
              ]"
            >
              {{ l.label }}<br><span class="text-[10px] opacity-60">{{ l.desc }}</span>
            </button>
          </div>
        </div>

        <!-- 是否配图 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">是否配图</div>
          <div class="flex gap-1.5">
            <button @click="withImage = true"
              :class="['px-3 py-1 rounded-full text-[11px] transition-colors border', withImage ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']">
              配图
            </button>
            <button @click="withImage = false"
              :class="['px-3 py-1 rounded-full text-[11px] transition-colors border', !withImage ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']">
              纯文字
            </button>
          </div>
        </div>

        <!-- 配图数量 -->
        <div v-if="withImage">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">配图数量</div>
          <div class="flex gap-1.5">
            <button v-for="n in [1,2,3,4,5]" :key="n"
              @click="imageCount = n"
              :class="['px-3 py-1 rounded-full text-[11px] transition-colors border', imageCount === n ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800' : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400']">
              {{ n }} 张
            </button>
          </div>
        </div>

        <!-- 文章标题 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">
            文章标题 <span class="font-normal normal-case">（选填，AI 自动生成）</span>
          </div>
          <input
            v-model="articleTitle"
            type="text"
            class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400"
            placeholder="如：莫干山民宿 · 端午运营手记"
          />
        </div>

        <!-- 生成按钮 -->
        <button
          @click="startGenerate"
          :disabled="!uploadedFile"
          class="w-full py-2.5 rounded-lg bg-bamboo-800 text-bamboo-100 text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors"
        >
          <Sparkles class="w-4 h-4" />开始生成
        </button>
      </div>

      <!-- 右栏：空状态 -->
      <div class="bg-cream-50 p-4 flex flex-col items-center justify-center gap-3">
        <Newspaper class="w-10 h-10 text-warm-300" />
        <p class="text-[13px] text-warm-500">上传视频、选择排版预设后</p>
        <p class="text-[12px] text-warm-400">点击「开始生成」即可自动产出公众号文章</p>
        <div class="mt-2 grid grid-cols-2 gap-2 text-[10px] text-warm-500 max-w-[260px]">
          <div class="flex items-center gap-1"><Clock class="w-3 h-3" />自动语音转写</div>
          <div class="flex items-center gap-1"><FileText class="w-3 h-3" />智能文章重构</div>
          <div class="flex items-center gap-1"><Image class="w-3 h-3" />正文配图</div>
          <div class="flex items-center gap-1"><Copy class="w-3 h-3" />一键复制</div>
        </div>
      </div>
    </div>

    <!-- ============================== 生成中 ============================== -->
    <div v-if="step === 'generating'" class="flex-1 min-h-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white flex flex-col items-center justify-center gap-6">
      <Loader2 class="w-10 h-10 text-bamboo-800 animate-spin" />
      <div class="text-center">
        <p class="text-sm font-medium text-bamboo-800 mb-1">{{ currentStep }}</p>
        <p class="text-[11px] text-warm-500">{{ progress }}%</p>
      </div>
      <div class="w-64 bg-cream-200 rounded-full h-1.5 overflow-hidden">
        <div class="bg-bamboo-700 h-full rounded-full transition-all duration-500" :style="{ width: progress + '%' }" />
      </div>
      <div class="space-y-1.5">
        <div
          v-for="(s, i) in progressSteps"
          :key="i"
          :class="[
            'text-[11px] flex items-center gap-2 transition-colors',
            i < progressSteps.indexOf(currentStep) ? 'text-bamboo-600' :
            s === currentStep ? 'text-bamboo-800 font-medium' : 'text-warm-400'
          ]"
        >
          <CheckCircle2 v-if="i < progressSteps.indexOf(currentStep)" class="w-3.5 h-3.5 text-bamboo-600" />
          <Loader2 v-else-if="s === currentStep" class="w-3.5 h-3.5 animate-spin" />
          <span v-else class="w-3.5 h-3.5 rounded-full border border-warm-300" />
          {{ s }}
        </div>
      </div>
    </div>

    <!-- ============================== 结果页 ============================== -->
    <div v-if="step === 'done'" class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- 左栏：信息 -->
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto space-y-4">
        <div>
          <div class="flex items-center gap-2 text-bamboo-700 mb-3">
            <CheckCircle2 class="w-4 h-4" />
            <span class="text-xs font-medium">生成完成</span>
          </div>
        </div>

        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">文章信息</div>
          <div class="space-y-2 text-[11px]">
            <div class="flex justify-between">
              <span class="text-warm-500">排版</span>
              <span class="text-warm-800 font-medium">{{ styles.find(s => s.id === selectedStyle)?.label }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-warm-500">长度</span>
              <span class="text-warm-800 font-medium">{{ lengths.find(l => l.id === selectedLength)?.label }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-warm-500">章节</span>
              <span class="text-warm-800 font-medium">{{ sections.length }} 个</span>
            </div>
            <div class="flex justify-between">
              <span class="text-warm-500">字数</span>
              <span class="text-warm-800 font-medium">约{{ plainText.length }} 字</span>
            </div>
          </div>
        </div>

        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">导出选项</div>
          <button @click="copyHtml" class="w-full py-2 rounded-lg bg-bamboo-800 text-bamboo-100 text-[12px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 transition-colors">
            <Copy class="w-3.5 h-3.5" />复制到公众号
          </button>
          <p class="text-[10px] text-warm-500 mt-1.5 text-center">复制后可直接粘贴到公众号编辑器</p>
        </div>
      </div>

      <!-- 右栏：预览 -->
      <div class="bg-cream-100 overflow-y-auto">
        <!-- 预览模式切换 -->
        <div class="sticky top-0 bg-white border-b border-cream-200 px-4 py-2 flex items-center justify-between z-10">
          <span class="text-[10px] font-medium text-warm-600">预览</span>
          <div class="flex gap-1 bg-cream-100 rounded-md p-0.5">
            <button @click="previewMode = 'phone'"
              :class="['p-1 rounded text-[10px] transition-colors', previewMode === 'phone' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500']">
              <Smartphone class="w-3.5 h-3.5" />
            </button>
            <button @click="previewMode = 'desktop'"
              :class="['p-1 rounded text-[10px] transition-colors', previewMode === 'desktop' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500']">
              <Monitor class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <!-- 预览内容 -->
        <div :class="[
          'mx-auto transition-all duration-300',
          previewMode === 'phone' ? 'max-w-[390px] p-4' : 'max-w-[680px] p-6'
        ]">
          <!-- 头图 -->
          <div :class="[
            'w-full rounded-2xl flex flex-col justify-end p-6 mb-4',
            selectedStyle === 'teal_tech' ? 'bg-gradient-to-br from-emerald-600 to-teal-500' :
            selectedStyle === 'wechat_green' ? 'bg-gradient-to-br from-green-600 to-green-500' :
            selectedStyle === 'restrained_pro' ? 'bg-gradient-to-br from-slate-600 to-slate-500' :
            selectedStyle === 'checklist' ? 'bg-gradient-to-br from-amber-500 to-orange-400' :
            'bg-gradient-to-br from-rose-500 to-pink-400',
            previewMode === 'phone' ? 'h-40' : 'h-52'
          ]">
            <div>
              <p class="text-[10px] text-white/70 tracking-widest mb-1">{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }) }} · 莫干山</p>
              <h2 class="text-white font-bold leading-tight" :class="previewMode === 'phone' ? 'text-lg' : 'text-2xl'">
                {{ articleTitle || '莫干山民宿 · 运营手记' }}
              </h2>
              <p class="text-white/60 text-xs mt-1">{{ styles.find(s => s.id === selectedStyle)?.label }}排版</p>
            </div>
          </div>

          <!-- 导语 -->
          <div class="mb-4">
            <div class="px-4 py-3 bg-green-50 border-l-3 border-green-400 rounded-r-lg text-[13px] leading-relaxed text-warm-700">
              暑期将至，端午在即。整理了一份关于莫干山民宿运营的思考与行动清单，分享给同样在路上的你。
            </div>
          </div>

          <!-- 章节 -->
          <div v-for="(sec, i) in sections" :key="i" class="mb-4">
            <h3 class="font-bold text-warm-900 mb-2 pb-1.5 border-b-2 inline-block" :class="previewMode === 'phone' ? 'text-base' : 'text-lg'"
              :style="{ borderColor: styles.find(s => s.id === selectedStyle)?.accent }">
              {{ sec.heading }}
            </h3>
            <p
              v-for="(p, j) in sec.paragraphs"
              :key="j"
              class="text-warm-600 leading-loose mb-2"
              :class="previewMode === 'phone' ? 'text-[13px]' : 'text-[14px]'"
            >{{ p }}</p>
            <!-- 配图占位 -->
            <div v-if="withImage && i < imageCount" class="my-3">
              <div v-if="i === 0 && articleImageUrl" class="rounded-xl overflow-hidden border border-cream-200">
                <img :src="articleImageUrl" class="w-full" />
              </div>
              <div v-else class="py-4 bg-cream-100 rounded-xl text-center text-[10px] text-warm-500">
                [ 配图{{ i + 1 }}：{{ sec.image }} ]
              </div>
            </div>
          </div>

          <!-- 结尾 -->
          <div class="text-center py-6 px-4 rounded-2xl mb-4 mt-6"
            :style="{ background: `linear-gradient(135deg, ${styles.find(s => s.id === selectedStyle)?.accent}15, ${styles.find(s => s.id === selectedStyle)?.accent}08)` }">
            <p class="font-semibold mb-2" :class="previewMode === 'phone' ? 'text-sm' : 'text-base'"
              :style="{ color: styles.find(s => s.id === selectedStyle)?.accent }">
              {{ ending }}
            </p>
            <p class="text-[11px] text-warm-500">📍 莫干山 · 松间山野民宿</p>
            <p class="text-[10px] text-warm-400 mt-0.5">扫码预订 →</p>
          </div>

          <!-- 底部复制按钮 -->
          <button @click="copyHtml" class="w-full py-2.5 rounded-lg bg-bamboo-800 text-bamboo-100 text-[12px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 transition-colors mb-6">
            <Copy class="w-3.5 h-3.5" />复制到公众号
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
