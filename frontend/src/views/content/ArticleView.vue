<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { collectStreamContentWithFile } from '@/api/content'
import { useHotelStore } from '@/stores/hotel'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { buildContentAiParams } from '@/utils/aiContextParams'
import { extractArticleDisplayText, extractDisplayText, parseJsonLike } from '@/utils/aiContentRender'
import AiPolishControl from '@/components/ai/AiPolishControl.vue'
import {
  CheckCircle2, Clock, Copy, FileText, Image, Loader2, Monitor,
  Newspaper, Smartphone, Sparkles, Upload, X, Zap,
} from 'lucide-vue-next'

type Step = 'config' | 'generating' | 'done'
type PreviewMode = 'phone' | 'desktop'

interface ArticleSection {
  heading: string
  paragraphs: string[]
  image: string
}

const router = useRouter()
const hotel = useHotelStore()
const pageStateKey = 'article'

const step = ref<Step>('config')
const uploadedFile = ref<File | null>(null)
const selectedStyle = ref('teal_tech')
const selectedLength = ref('medium')
const withImage = ref(true)
const imageCount = ref(3)
const articleTitle = ref('')
const articleImageUrl = ref('')
const sections = ref<ArticleSection[]>([])
const ending = ref('')
const toast = ref('')
const progress = ref(0)
const currentStep = ref('')
const previewMode = ref<PreviewMode>('desktop')

const styles = [
  { id: 'teal_tech', label: '青绿技术型', desc: '渐变背景，适合知识分享', bg: 'from-emerald-600 to-teal-500', accent: '#0D9488' },
  { id: 'wechat_green', label: '微信绿专业型', desc: '经典公众号风格', bg: 'from-green-600 to-green-500', accent: '#16A34A' },
  { id: 'restrained_pro', label: '克制专业型', desc: '极简留白，适合深度长文', bg: 'from-slate-600 to-slate-500', accent: '#475569' },
  { id: 'checklist', label: '选型清单型', desc: '分点清晰，适合攻略', bg: 'from-amber-500 to-orange-400', accent: '#D97706' },
  { id: 'oral_quote', label: '口语金句型', desc: '大段引述，适合人物访谈', bg: 'from-rose-500 to-pink-400', accent: '#E11D48' },
]

const lengths = [
  { id: 'short', label: '短文', desc: '约800字' },
  { id: 'medium', label: '中篇', desc: '约1500字' },
  { id: 'long', label: '长文', desc: '约2500字' },
]

const progressSteps = [
  '读取上传参数',
  '提取视频语音',
  '整理转写文本',
  '重构公众号文章',
  '生成配图建议',
  '渲染排版预览',
  '质量检查中',
]

const selectedStyleMeta = computed(() => styles.find(item => item.id === selectedStyle.value) || styles[0])
const selectedLengthMeta = computed(() => lengths.find(item => item.id === selectedLength.value) || lengths[1])
const hasResult = computed(() => sections.value.length > 0)
const uploadedFileSize = computed(() => formatFileSize(uploadedFile.value?.size || 0))

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadedFile.value = file
  persistState()
}

function triggerUpload() {
  document.getElementById('article-file-input')?.click()
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  const file = e.dataTransfer?.files?.[0]
  if (!file) return
  uploadedFile.value = file
  persistState()
}

function removeVideo() {
  uploadedFile.value = null
  const input = document.getElementById('article-file-input') as HTMLInputElement | null
  if (input) input.value = ''
  persistState()
}

function buildArticleRequestParams() {
  const file = uploadedFile.value
  const style = selectedStyleMeta.value
  const length = selectedLengthMeta.value
  const title = articleTitle.value.trim()

  return {
    sourceType: 'video_to_wechat_article',
    publishPlatform: 'wechat_mp',
    title,
    topic: title || '视频转公众号文章',
    theme: title || '视频内容整理为公众号推文',
    message: [
      '请把用户上传的视频内容转写、提炼并改写成可发布的微信公众号文章。',
      title ? `文章主题：${title}` : '文章主题由视频内容自动提炼。',
      `排版预设：${style.label}（${style.desc}）。`,
      `文章长度：${length.label}（${length.desc}）。`,
      withImage.value ? `需要正文配图，数量 ${imageCount.value} 张。` : '只生成纯文字文章，不需要配图。',
      file ? `上传视频：${file.name}，类型 ${file.type || '未知'}，大小 ${formatFileSize(file.size)}。` : '',
    ].filter(Boolean).join('\n'),
    style: selectedStyle.value,
    styleLabel: style.label,
    styleDescription: style.desc,
    length: selectedLength.value,
    lengthLabel: length.label,
    lengthDescription: length.desc,
    withImage: withImage.value,
    imageCount: withImage.value ? imageCount.value : 0,
    fileName: file?.name || '',
    videoFileName: file?.name || '',
    videoFileType: file?.type || '',
    videoFileSize: file?.size || 0,
    uploadProvided: Boolean(file),
    outputFormat: 'json',
    schema: {
      title: '公众号文章标题',
      summary: '摘要',
      content: '公众号正文，按段落输出',
      imageUrl: '头图链接，可为空',
      imageSuggestions: ['正文配图建议'],
    },
  }
}

async function startGenerate() {
  if (!uploadedFile.value || step.value === 'generating') return
  step.value = 'generating'
  progress.value = 5
  currentStep.value = progressSteps[0]
  sections.value = []
  ending.value = ''
  persistState()

  const timer = window.setInterval(() => {
    const index = Math.min(progressSteps.length - 1, Math.floor(progress.value / 16))
    currentStep.value = progressSteps[index]
    progress.value = Math.min(92, progress.value + 3)
  }, 700)

  try {
    const content = await collectStreamContentWithFile('article', buildContentAiParams(hotel, 'article', buildArticleRequestParams()), uploadedFile.value, {
      onChunk(_chunk, content) {
        currentStep.value = '重构公众号文章'
        progress.value = Math.min(95, progress.value + 2)
        applyArticleContent(content)
      },
    })
    if (content) applyArticleContent(content)
    progress.value = 100
    currentStep.value = '质量检查中'
    step.value = hasResult.value ? 'done' : 'config'
    if (!hasResult.value) flashToast('AI 未返回可展示正文，请检查智能体输出 JSON')
  } catch {
    step.value = 'config'
    flashToast('AI 调用失败，请稍后重试')
  } finally {
    window.clearInterval(timer)
    persistState()
  }
}

function applyArticleContent(raw: string) {
  const parsed = parseArticleJson(raw) as Record<string, any> | null
  if (parsed?.imageUrl) articleImageUrl.value = String(parsed.imageUrl)
  if (parsed?.title) articleTitle.value = String(parsed.title)

  const imageSuggestions = Array.isArray(parsed?.imageSuggestions)
    ? parsed.imageSuggestions.map((item: unknown) => String(item)).filter(Boolean)
    : []

  const sourceText = parsed ? extractArticleBody(parsed) : extractArticleDisplayText(raw)
  const nextSections = buildSections(sourceText, imageSuggestions)
  if (!nextSections.length) return
  sections.value = nextSections
  if (!ending.value) ending.value = '以上内容可直接复制到公众号编辑器，再结合门店真实图片发布。'
  step.value = 'done'
  persistState()
}

function parseArticleJson(raw: string) {
  const value = String(raw || '').trim()
  if (!value) return null
  return parseJsonLike(value) || parseJsonLike(value.startsWith('{') ? value : `{${value}}`)
}

function extractArticleBody(value: unknown, seen = new Set<unknown>()): string {
  if (!value) return ''
  if (typeof value === 'string') return extractArticleDisplayText(value) || extractDisplayText(value)
  if (typeof value !== 'object') return ''
  if (seen.has(value)) return ''
  seen.add(value)

  if (Array.isArray(value)) {
    return value.map(item => extractArticleBody(item, seen)).filter(Boolean).join('\n\n')
  }

  const record = value as Record<string, unknown>
  const keys = [
    'content',
    'body',
    'article',
    'articleContent',
    'article_content',
    '正文',
    'text',
    'answer',
    'output',
    'result',
    'outputs',
    'data',
  ]

  for (const key of keys) {
    const text = extractArticleBody(record[key], seen)
    if (text) return text
  }

  const sectionsValue = record.sections || record.paragraphs || record.blocks
  if (Array.isArray(sectionsValue)) {
    return sectionsValue.map(item => {
      if (!item || typeof item !== 'object') return extractArticleBody(item, seen)
      const section = item as Record<string, unknown>
      const heading = String(section.heading || section.title || '').trim()
      const body = extractArticleBody(section.paragraphs || section.content || section.body || section.text, seen)
      return [heading, body].filter(Boolean).join('\n')
    }).filter(Boolean).join('\n\n')
  }

  return ''
}

function buildSections(text: string, imageSuggestions: string[]): ArticleSection[] {
  const lines = String(text || '')
    .replace(/\r/g, '')
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)

  if (!lines.length) return []

  const result: ArticleSection[] = []
  let current: ArticleSection | null = null

  for (const line of lines) {
    const headingMatch = line.match(/^(?:#{1,3}\s*|[一二三四五六七八九十]+[、.]|[0-9]+[、.])\s*(.+)$/)
    const looksLikeHeading = headingMatch || (line.length <= 24 && !/[。！？；，,]/.test(line))
    if (looksLikeHeading) {
      current = { heading: (headingMatch?.[1] || line).replace(/^["'“”]+|["'“”]+$/g, ''), paragraphs: [], image: '' }
      result.push(current)
      continue
    }
    if (!current) {
      current = { heading: articleTitle.value || '视频内容精编', paragraphs: [], image: '' }
      result.push(current)
    }
    current.paragraphs.push(line)
  }

  if (result.length === 1 && result[0].paragraphs.length > 5) {
    const paragraphs = result[0].paragraphs
    const chunked: ArticleSection[] = []
    for (let i = 0; i < paragraphs.length; i += 3) {
      chunked.push({
        heading: i === 0 ? result[0].heading : `正文段落 ${Math.floor(i / 3) + 1}`,
        paragraphs: paragraphs.slice(i, i + 3),
        image: '',
      })
    }
    return attachImages(chunked, imageSuggestions)
  }

  return attachImages(result.filter(item => item.paragraphs.length), imageSuggestions)
}

function attachImages(items: ArticleSection[], suggestions: string[]) {
  return items.map((item, index) => ({
    ...item,
    image: suggestions[index] || (withImage.value ? `${selectedStyleMeta.value.label}配图建议` : ''),
  }))
}

const fullHtml = computed(() => {
  const accent = selectedStyleMeta.value.accent
  let html = `<div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;max-width:640px;margin:0 auto;color:#333;line-height:1.8;font-size:15px;">\n`
  html += `<div style="background:linear-gradient(135deg,${accent}22,${accent}44);padding:32px 24px 24px;text-align:center;border-radius:0 0 24px 24px;">\n`
  html += `<p style="font-size:12px;color:${accent};letter-spacing:2px;margin:0 0 8px;">${new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })}</p>\n`
  html += `<h1 style="font-size:22px;font-weight:700;color:#1F2937;margin:0 0 8px;line-height:1.4;">${escapeHtml(articleTitle.value || '视频转公众号文章')}</h1>\n`
  html += `<p style="font-size:13px;color:#6B7280;margin:0;">${selectedStyleMeta.value.label}排版 · ${selectedLengthMeta.value.label}</p>\n</div>\n`

  sections.value.forEach((sec, index) => {
    html += `<div style="padding:8px 24px 0;">\n`
    html += `<h2 style="font-size:18px;font-weight:700;color:#1F2937;margin:20px 0 12px;padding-bottom:8px;border-bottom:3px solid ${accent};display:inline-block;">${escapeHtml(sec.heading)}</h2>\n`
    sec.paragraphs.forEach(paragraph => {
      html += `<p style="margin:0 0 12px;font-size:15px;color:#4B5563;letter-spacing:.3px;">${escapeHtml(paragraph)}</p>\n`
    })
    if (withImage.value && index < imageCount.value) {
      html += `<div style="margin:12px 0 16px;text-align:center;padding:20px;background:#f9fafb;border-radius:12px;color:#9CA3AF;font-size:12px;">[ 配图${index + 1}${sec.image ? `：${escapeHtml(sec.image)}` : ''} ]</div>\n`
    }
    html += `</div>\n`
  })

  if (ending.value) {
    html += `<div style="padding:16px 24px 24px;"><div style="text-align:center;padding:24px 20px;background:linear-gradient(135deg,${accent}15,${accent}08);border-radius:16px;margin-top:16px;"><p style="font-size:16px;color:${accent};font-weight:600;margin:0;">${escapeHtml(ending.value)}</p></div></div>\n`
  }
  html += `</div>`
  return html
})

const plainText = computed(() => {
  const lines: string[] = []
  if (articleTitle.value) lines.push(articleTitle.value)
  sections.value.forEach(sec => {
    lines.push(`【${sec.heading}】`)
    lines.push(...sec.paragraphs)
  })
  if (ending.value) lines.push(ending.value)
  return lines.join('\n\n')
})

function persistState() {
  saveAiPageState(pageStateKey, {
    step: step.value === 'generating' ? 'config' : step.value,
    selectedStyle: selectedStyle.value,
    selectedLength: selectedLength.value,
    withImage: withImage.value,
    imageCount: imageCount.value,
    articleImageUrl: articleImageUrl.value,
    articleTitle: articleTitle.value,
    uploadedFileName: uploadedFile.value?.name || '',
    uploadedFileType: uploadedFile.value?.type || '',
    uploadedFileSize: uploadedFile.value?.size || 0,
    sections: sections.value,
    ending: ending.value,
    previewMode: previewMode.value,
  })
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (!restored) return
  selectedStyle.value = restored.selectedStyle || selectedStyle.value
  selectedLength.value = restored.selectedLength || selectedLength.value
  withImage.value = restored.withImage ?? withImage.value
  imageCount.value = restored.imageCount || imageCount.value
  articleImageUrl.value = restored.articleImageUrl || ''
  articleTitle.value = restored.articleTitle || ''
  sections.value = Array.isArray(restored.sections) ? restored.sections : []
  ending.value = restored.ending || ''
  previewMode.value = 'desktop'
  step.value = sections.value.length ? 'done' : 'config'
}

function flashToast(msg: string) {
  toast.value = msg
  window.setTimeout(() => { toast.value = '' }, 1600)
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
    try {
      await navigator.clipboard.writeText(plainText.value)
      flashToast('已复制纯文本')
    } catch {
      flashToast('复制失败，请手动复制')
    }
  }
}

function formatFileSize(size: number) {
  if (!size) return '0 KB'
  if (size < 1024 * 1024) return `${Math.max(1, Math.round(size / 1024))} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function escapeHtml(value: string) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

onMounted(restoreState)
</script>

<template>
  <div class="h-full flex flex-col">
    <div v-if="toast" class="fixed top-6 right-6 z-50 rounded-lg bg-bamboo-800 px-5 py-3 text-sm text-cream-100 shadow-lg">
      {{ toast }}
    </div>

    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-start gap-3">
        <Newspaper class="mt-0.5 h-5 w-5 text-bamboo-700" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">公众号推文</h1>
          <p class="mt-0.5 text-[11px] text-warm-500">视频转文章 · 智能排版 · 一键复制到公众号</p>
        </div>
      </div>
      <div class="flex flex-shrink-0 items-center gap-2">
        <button @click="router.push('/history/article')" class="flex items-center gap-1 rounded-full border border-cream-300 bg-white px-2 py-1 text-[10px] text-warm-600 transition-colors hover:border-bamboo-400 hover:bg-bamboo-50 hover:text-bamboo-800">
          <FileText class="h-3 w-3" />生成记录
        </button>
        <button @click="router.push('/credits')" class="flex items-center gap-1 rounded-full border border-cream-300 bg-white px-2 py-1 text-[10px] text-warm-600 transition-colors hover:border-bamboo-400 hover:bg-bamboo-50 hover:text-bamboo-800">
          <Zap class="h-3 w-3" />算力流水
        </button>
        <span class="rounded-full bg-amber-50 px-2 py-1 text-[10px] font-medium text-amber-700">消耗 15 算力 / 次</span>
      </div>
    </div>

    <div v-if="step === 'config'" class="article-workspace grid min-h-0 flex-1 overflow-hidden rounded-lg border border-cream-300/60 bg-white">
      <div class="space-y-4 overflow-y-auto border-r border-cream-200/60 p-4">
        <div>
          <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">视频上传</div>
          <input id="article-file-input" type="file" accept="video/*" class="hidden" @change="handleFileChange" />
          <div
            @click="triggerUpload"
            @dragover.prevent
            @drop="onDrop"
            :class="[
              'mb-3 cursor-pointer rounded-lg border-2 border-dashed p-5 text-center transition-colors',
              uploadedFile ? 'border-bamboo-400 bg-bamboo-50/30' : 'border-cream-300 bg-cream-50 hover:border-bamboo-300'
            ]"
          >
            <div v-if="!uploadedFile" class="flex flex-col items-center gap-2 text-warm-400">
              <Upload class="h-7 w-7" />
              <span class="text-[12px]">拖拽视频到此处</span>
              <span class="text-[10px]">或点击上传 · MP4/MOV</span>
            </div>
            <div v-else class="flex items-center gap-3">
              <div class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-lg bg-bamboo-100">
                <FileText class="h-5 w-5 text-bamboo-700" />
              </div>
              <div class="min-w-0 flex-1 text-left">
                <div class="truncate text-[11px] font-medium text-bamboo-800">{{ uploadedFile.name }}</div>
                <div class="text-[10px] text-warm-500">已选择 · {{ uploadedFile.type || '视频文件' }} · {{ uploadedFileSize }}</div>
              </div>
              <button @click.stop="removeVideo" class="text-warm-400 hover:text-rose-500">
                <X class="h-4 w-4" />
              </button>
            </div>
          </div>
          <p class="text-[10px] leading-relaxed text-warm-500">支持抖音、口播、宣传片等视频，AI 按上传视频和下方参数转写并重构为公众号文章。</p>
        </div>

        <div>
          <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">排版预设</div>
          <div class="space-y-1.5">
            <button
              v-for="item in styles"
              :key="item.id"
              @click="selectedStyle = item.id"
              :class="[
                'w-full rounded-lg border px-3 py-2 text-left transition-colors',
                selectedStyle === item.id ? 'border-bamboo-400 bg-bamboo-50' : 'border-cream-200 bg-white hover:border-bamboo-200'
              ]"
            >
              <div class="flex items-center gap-2.5">
                <div :class="['h-6 w-6 flex-shrink-0 rounded-md bg-gradient-to-br', item.bg]" />
                <div class="min-w-0 flex-1">
                  <div class="text-[11px] font-medium" :class="selectedStyle === item.id ? 'text-bamboo-800' : 'text-warm-700'">{{ item.label }}</div>
                  <div class="text-[10px] text-warm-500">{{ item.desc }}</div>
                </div>
                <div v-if="selectedStyle === item.id" class="h-2.5 w-2.5 flex-shrink-0 rounded-full bg-bamboo-700" />
              </div>
            </button>
          </div>
        </div>

        <div>
          <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">文章长度</div>
          <div class="flex gap-1.5">
            <button
              v-for="item in lengths"
              :key="item.id"
              @click="selectedLength = item.id"
              :class="[
                'flex-1 rounded-lg border py-2 text-center text-[11px] transition-colors',
                selectedLength === item.id ? 'border-bamboo-400 bg-bamboo-50 font-medium text-bamboo-800' : 'border-cream-200 bg-white text-warm-600 hover:border-bamboo-200'
              ]"
            >
              {{ item.label }}<br><span class="text-[10px] opacity-60">{{ item.desc }}</span>
            </button>
          </div>
        </div>

        <div>
          <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">是否配图</div>
          <div class="flex gap-1.5">
            <button @click="withImage = true" :class="['rounded-full border px-3 py-1 text-[11px] transition-colors', withImage ? 'border-bamboo-800 bg-bamboo-800 text-bamboo-100' : 'border-cream-300 bg-white text-warm-500 hover:border-bamboo-400']">配图</button>
            <button @click="withImage = false" :class="['rounded-full border px-3 py-1 text-[11px] transition-colors', !withImage ? 'border-bamboo-800 bg-bamboo-800 text-bamboo-100' : 'border-cream-300 bg-white text-warm-500 hover:border-bamboo-400']">纯文字</button>
          </div>
        </div>

        <div v-if="withImage">
          <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">配图数量</div>
          <div class="flex gap-1.5">
            <button v-for="n in [1, 2, 3, 4, 5]" :key="n" @click="imageCount = n" :class="['rounded-full border px-3 py-1 text-[11px] transition-colors', imageCount === n ? 'border-bamboo-800 bg-bamboo-800 text-bamboo-100' : 'border-cream-300 bg-white text-warm-500 hover:border-bamboo-400']">
              {{ n }} 张
            </button>
          </div>
        </div>

        <div>
          <div class="mb-1.5 flex items-center justify-between">
            <div class="text-[10px] font-semibold uppercase tracking-wider text-warm-500">文章标题 <span class="font-normal normal-case">（选填，AI 可自动生成）</span></div>
            <AiPolishControl :source-text="articleTitle" scene="article" field="title" @accept="articleTitle = $event" />
          </div>
          <input v-model="articleTitle" type="text" class="w-full rounded-lg border border-cream-300 bg-white px-2.5 py-1.5 text-[12px] text-bamboo-950 focus:border-bamboo-400 focus:outline-none" placeholder="如：本周民宿运营复盘 / 新品房型推广文章" />
        </div>

        <button
          @click="startGenerate"
          :disabled="!uploadedFile"
          class="flex w-full items-center justify-center gap-2 rounded-lg bg-bamboo-800 py-2.5 text-[13px] font-medium text-bamboo-100 transition-colors hover:bg-bamboo-900 disabled:cursor-not-allowed disabled:bg-cream-200 disabled:text-warm-400"
        >
          <Sparkles class="h-4 w-4" />开始生成
        </button>
      </div>

      <div class="flex flex-col items-center justify-center gap-3 bg-cream-50 p-4">
        <Newspaper class="h-10 w-10 text-warm-300" />
        <p class="text-[13px] text-warm-500">上传视频、选择排版预设后</p>
        <p class="text-[12px] text-warm-400">点击「开始生成」即可产出公众号文章</p>
        <div class="mt-2 grid max-w-[260px] grid-cols-2 gap-2 text-[10px] text-warm-500">
          <div class="flex items-center gap-1"><Clock class="h-3 w-3" />自动语音转写</div>
          <div class="flex items-center gap-1"><FileText class="h-3 w-3" />智能文章重构</div>
          <div class="flex items-center gap-1"><Image class="h-3 w-3" />正文配图</div>
          <div class="flex items-center gap-1"><Copy class="h-3 w-3" />一键复制</div>
        </div>
      </div>
    </div>

    <div v-if="step === 'generating'" class="article-generation-loading rounded-lg border border-cream-300/60 bg-white">
      <div class="article-loading-orb">
        <Loader2 class="h-10 w-10 animate-spin text-bamboo-800" />
      </div>
      <div class="text-center">
        <p class="mb-1 text-sm font-medium text-bamboo-800">{{ currentStep }}</p>
        <p class="text-[11px] text-warm-500">{{ progress }}%</p>
      </div>
      <div class="h-1.5 w-64 overflow-hidden rounded-full bg-cream-200">
        <div class="h-full rounded-full bg-bamboo-700 transition-all duration-500" :style="{ width: progress + '%' }" />
      </div>
      <div class="space-y-1.5">
        <div v-for="(item, index) in progressSteps" :key="item" :class="['flex items-center gap-2 text-[11px] transition-colors', index < progressSteps.indexOf(currentStep) ? 'text-bamboo-600' : item === currentStep ? 'font-medium text-bamboo-800' : 'text-warm-400']">
          <CheckCircle2 v-if="index < progressSteps.indexOf(currentStep)" class="h-3.5 w-3.5 text-bamboo-600" />
          <Loader2 v-else-if="item === currentStep" class="h-3.5 w-3.5 animate-spin" />
          <span v-else class="h-3.5 w-3.5 rounded-full border border-cream-300" />
          {{ item }}
        </div>
      </div>
    </div>

    <div v-if="step === 'done'" class="article-workspace grid min-h-0 flex-1 overflow-hidden rounded-lg border border-cream-300/60 bg-white">
      <div class="space-y-4 overflow-y-auto border-r border-cream-200/60 p-4">
        <div class="flex items-center gap-2 text-bamboo-700">
          <CheckCircle2 class="h-4 w-4" />
          <span class="text-xs font-medium">生成完成</span>
        </div>

        <div>
          <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">文章信息</div>
          <div class="space-y-2 text-[11px]">
            <div class="flex justify-between"><span class="text-warm-500">排版</span><span class="font-medium text-bamboo-950">{{ selectedStyleMeta.label }}</span></div>
            <div class="flex justify-between"><span class="text-warm-500">长度</span><span class="font-medium text-bamboo-950">{{ selectedLengthMeta.label }}</span></div>
            <div class="flex justify-between"><span class="text-warm-500">章节</span><span class="font-medium text-bamboo-950">{{ sections.length }} 个</span></div>
            <div class="flex justify-between"><span class="text-warm-500">字数</span><span class="font-medium text-bamboo-950">约 {{ plainText.length }} 字</span></div>
          </div>
        </div>

        <div>
          <div class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-warm-500">导出选项</div>
          <button @click="copyHtml" class="flex w-full items-center justify-center gap-2 rounded-lg bg-bamboo-800 py-2 text-[12px] font-medium text-bamboo-100 transition-colors hover:bg-bamboo-900">
            <Copy class="h-3.5 w-3.5" />复制到公众号
          </button>
          <button @click="step = 'config'" class="mt-2 w-full rounded-lg border border-cream-300 bg-white py-2 text-[12px] font-medium text-warm-600 transition-colors hover:border-bamboo-300 hover:text-bamboo-800">
            返回调整参数
          </button>
          <p class="mt-1.5 text-center text-[10px] text-warm-500">复制后可直接粘贴到公众号编辑器</p>
        </div>
      </div>

      <div class="overflow-y-auto bg-cream-100">
        <div class="sticky top-0 z-10 flex items-center justify-between border-b border-cream-200 bg-white px-4 py-2">
          <span class="text-[10px] font-medium text-warm-600">预览</span>
          <div class="flex gap-1 rounded-md bg-cream-100 p-0.5">
            <button @click="previewMode = 'phone'" :class="['rounded p-1 text-[10px] transition-colors', previewMode === 'phone' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500']"><Smartphone class="h-3.5 w-3.5" /></button>
            <button @click="previewMode = 'desktop'" :class="['rounded p-1 text-[10px] transition-colors', previewMode === 'desktop' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500']"><Monitor class="h-3.5 w-3.5" /></button>
          </div>
        </div>

        <div :class="['mx-auto transition-all duration-300', previewMode === 'phone' ? 'max-w-[390px] p-4' : 'max-w-[840px] p-6']">
          <div :class="['mb-4 flex w-full flex-col justify-end rounded-2xl bg-gradient-to-br p-6', selectedStyleMeta.bg, previewMode === 'phone' ? 'h-40' : 'h-52']">
            <p class="mb-1 text-[10px] tracking-widest text-white/70">{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }) }}</p>
            <h2 class="font-bold leading-tight text-white" :class="previewMode === 'phone' ? 'text-lg' : 'text-2xl'">{{ articleTitle || '视频转公众号文章' }}</h2>
            <p class="mt-1 text-xs text-white/60">{{ selectedStyleMeta.label }}排版</p>
          </div>

          <div v-for="(sec, index) in sections" :key="`${sec.heading}-${index}`" class="mb-4">
            <h3 class="mb-2 inline-block border-b-2 pb-1.5 font-bold text-bamboo-950" :class="previewMode === 'phone' ? 'text-base' : 'text-lg'" :style="{ borderColor: selectedStyleMeta.accent }">
              {{ sec.heading }}
            </h3>
            <p v-for="(paragraph, pIndex) in sec.paragraphs" :key="pIndex" class="mb-2 leading-loose text-warm-600" :class="previewMode === 'phone' ? 'text-[13px]' : 'text-[14px]'">
              {{ paragraph }}
            </p>
            <div v-if="withImage && index < imageCount" class="my-3">
              <div v-if="index === 0 && articleImageUrl" class="overflow-hidden rounded-xl border border-cream-200">
                <img :src="articleImageUrl" class="w-full" />
              </div>
              <div v-else class="rounded-xl bg-cream-100 py-4 text-center text-[10px] text-warm-500">
                [ 配图{{ index + 1 }}：{{ sec.image }} ]
              </div>
            </div>
          </div>

          <div v-if="ending" class="mb-4 mt-6 rounded-2xl px-4 py-6 text-center" :style="{ background: `linear-gradient(135deg, ${selectedStyleMeta.accent}15, ${selectedStyleMeta.accent}08)` }">
            <p class="mb-2 font-semibold" :class="previewMode === 'phone' ? 'text-sm' : 'text-base'" :style="{ color: selectedStyleMeta.accent }">{{ ending }}</p>
            <p class="text-[11px] text-warm-500">{{ hotel.config.name || '酒店/民宿名称' }}</p>
          </div>

          <button @click="copyHtml" class="mb-6 flex w-full items-center justify-center gap-2 rounded-lg bg-bamboo-800 py-2.5 text-[12px] font-medium text-bamboo-100 transition-colors hover:bg-bamboo-900">
            <Copy class="h-3.5 w-3.5" />复制到公众号
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.article-workspace {
  grid-template-columns: minmax(380px, 420px) minmax(0, 1fr);
}

.article-generation-loading {
  display: flex;
  min-height: 560px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1.1rem;
  overflow: hidden;
  text-align: center;
}

.article-loading-orb {
  display: flex;
  height: 4.5rem;
  width: 4.5rem;
  align-items: center;
  justify-content: center;
  border-radius: 1.1rem;
  background: #f2f8ee;
  box-shadow: inset 0 0 0 1px #d9e7ce;
}

@media (max-width: 1180px) {
  .article-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .article-workspace > :first-child {
    border-right: 0;
    border-bottom: 1px solid #f0e7dc;
  }
}
</style>
