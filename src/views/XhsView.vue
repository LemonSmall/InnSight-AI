<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Instagram, Zap, Building2, CloudRain, CalendarDays,
  Sparkles, LayoutGrid, CheckCircle2, Loader2,
  Copy, Image, Download
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { generateContent, getTaskResult } from '@/api/content'

const hotel = useHotelStore()
const router = useRouter()

// ====== 状态 ======
const topics = [
  { val: 'rain', label: '🌧️ 雨天竹林' },
  { val: 'festival', label: '🎋 端午氛围' },
  { val: 'couple', label: '💑 情侣度假' },
  { val: 'family', label: '👨‍👩‍👧 亲子出行' },
  { val: 'hotspring', label: '♨️ 私汤温泉' },
  { val: 'breakfast', label: '🍳 有机早餐' },
  { val: 'vlog', label: '📸 日常 vlog' },
  { val: 'escape', label: '🌿 周末逃离' },
]

const tones = [
  { val: 'emotional', label: '情绪种草' },
  { val: 'guide', label: '攻略干货' },
  { val: 'deal', label: '限时特惠' },
  { val: 'review', label: '探店测评' },
]

const selectedTopics = ref<Set<string>>(new Set(['rain']))
const selectedTone = ref('emotional')
const style = ref('warm')
const note = ref('')
const withImage = ref(true)
const imageSize = ref('3:4')
const imageCount = ref(6)
const generating = ref(false)
const generated = ref(false)
const loadingStep = ref(0)
const xhsImageUrl = ref('')
const toast = ref('')
const customTopic = ref('')
let loadingTimer: ReturnType<typeof setInterval> | null = null

// ====== 输出内容 ======
const title = ref('')
const body = ref('')
const tags = ref<string[]>([])

const bodyCount = computed(() => body.value.length)

// 正文+标签合并，一键复制用
const bodyWithTags = computed(() => {
  if (!tags.value.length) return body.value
  const tagStr = tags.value.map(t => '#' + t).join(' ')
  return body.value + '\n\n' + tagStr
})

function toggleTopic(v: string) {
  const s = new Set(selectedTopics.value)
  if (s.has(v)) s.delete(v); else s.add(v)
  selectedTopics.value = s
}

function isTopicOn(v: string) { return selectedTopics.value.has(v) }

// ====== 上下文 ======
const freeCount = computed(() => {
  let c = 0
  for (const rs of hotel.roomStatuses) { c += rs.rooms.filter(r => r.status === 'free').length }
  return c
})

// ====== 封面建议 ======
const coverAdvice = computed(() => {
  const vals = [...selectedTopics.value]
  if (vals.includes('rain')) return '雨天竹林大片（趁薄雾拍）'
  if (vals.includes('festival')) return '节日氛围布景图'
  if (vals.includes('couple')) return '情侣打卡双人照'
  return '民宿环境全景图'
})

const pubTip = computed(() => {
  const vals = [...selectedTopics.value]
  if (vals.includes('rain')) return '☔ 雨天竹林内容在小红书搜索量比晴天高 40%，今日发布时机极佳，建议 12:00 抢先发。'
  if (vals.includes('festival')) return '🎋 端午节前3天是搜索量爆发期，今日发布可卡住流量红利窗口，建议 20:00 发布。'
  return '📊 建议今日 20:00 发布，配合 ' + imageCount.value + ' 张竖版图效果最佳，封面图选最有视觉冲击力的。'
})

// ====== 模拟生成 ======
const mockTitles: Record<string, string[]> = {
  rain: [
    '雨天莫干山｜竹林深处的治愈民宿，住进去就不想走了🌿',
    '江浙沪周末逃离计划！下雨天的莫干山才是正确打开方式✨',
    '莫干山这家民宿，雨天比晴天还惊艳！私汤+竹林绝了♨️',
  ],
  festival: [
    '端午去哪玩？莫干山这份避世攻略请收好🎋',
    '端午不加价！莫干山这家民宿藏着不一样的节日仪式感🏡',
    '2026端午节｜江浙沪3小时度假圈，莫干山民宿实测推荐🌿',
  ],
  couple: [
    '情侣必住！莫干山这家竹林私汤民宿，浪漫到不想出门💑',
    '和TA一起躲进莫干山｜3天2夜情侣度假攻略♨️',
    '仪式感满分！莫干山这家民宿的私汤，情侣都沦陷了🌙',
  ],
  default: [
    '莫干山民宿推荐｜住进竹林里，泡着温泉听雨声🌿',
    '周末去哪玩？莫干山这家民宿我住了3次还想来🏡',
    '江浙沪周边游！莫干山这家私汤民宿，治愈了整个夏天✨',
  ],
}

const mockBodies: Record<string, string[]> = {
  rain: [
    `雨天的莫干山，才是真正的江南。…（保留原版）…点赞收藏，下次来莫干山不迷路✨`,
    `我宣布！雨天才是莫干山最美的打开方式☔️…在茶室煮了一壶九曲红梅，林叔给我们拿来了刚采的枇杷…这个周末，不如逃到山里来，听一场雨，泡一汤泉。📍莫干山·松间山野民宿✨`,
  ],
  festival: [
    `端午假期倒计时3天！还没想好去哪的朋友看过来👋…还剩最后几间房，赶紧戳主页预订吧～📍莫干山·松间山野民宿`,
    `在山里过端午是什么体验？🎋…没有高速堵车，没有人挤人…晚上泡汤时月亮刚好升到竹梢…端午就来山里吧，我们给你留了粽子和温泉。📍莫干山·松间山野`,
  ],
  default: [
    `周末去哪儿？我推荐莫干山这家竹林民宿🌿…两天一晚，人均不到500，收藏这条笔记～`,
    `江浙沪的朋友们，这家藏在莫干山的民宿我替你们试过了！🏡…从杭州出发2小时…无边泳池正对着山谷…趁端午还没到，人少景美，赶紧安排！📍莫干山·松间山野民宿`,
  ],
}

const defaultTags = ['莫干山民宿', '竹林民宿', '端午去哪玩', '雨天打卡', '民宿推荐', '浙江旅游', '私汤温泉', '情侣出行', '莫干山旅游攻略']

// ====== 生成 ======
async function generate() {
  if (!selectedTopics.value.size) return

  generating.value = true
  generated.value = false
  loadingStep.value = 0
  title.value = ''
  body.value = ''
  tags.value = []

  const steps = ['分析今日天气与节假日节点', '匹配最佳内容主题与方向', '撰写小红书图文']
  loadingTimer = setInterval(() => {
    if (loadingStep.value < steps.length) {
      loadingStep.value++
    } else { if (loadingTimer) clearInterval(loadingTimer) }
  }, 800)

  try {
    const vals = [...selectedTopics.value]
    const { data: res } = await generateContent('xhs', {
      topics: vals.join(','),
      tone: selectedTone.value,
      style: style.value,
      note: note.value,
      withImage: withImage.value,
      imageCount: imageCount.value,
    })
    const d = res.data || res
    const taskId = d.taskId

    let attempts = 0
    while (attempts < 30) {
      await new Promise(r => setTimeout(r, 1000))
      const { data: tr } = await getTaskResult(taskId)
      const task = (tr as any).data || tr
      if (task.status === 'done' && task.content) {
        // 内容可能是 JSON（含配图）或纯文本
        let textContent = task.content
        try {
          const parsed = JSON.parse(task.content)
          if (parsed.text) { textContent = parsed.text; xhsImageUrl.value = parsed.imageUrl || '' }
        } catch { /* 纯文本 */ }
        body.value = textContent
        tags.value = defaultTags
        if (vals.includes('rain')) { tags.value.unshift('雨天拍照', '雨中竹林') }
        if (vals.includes('festival')) { tags.value.unshift('端午节', '粽子') }
        break
      }
      if (task.status === 'failed') break
      attempts++
    }
  } catch {
    // 回退
    const vals = [...selectedTopics.value]
    const key = vals.includes('rain') ? 'rain' : vals.includes('festival') ? 'festival' : vals.includes('couple') ? 'couple' : 'default'
    const pool = mockTitles[key] || mockTitles.default
    title.value = pool[Math.floor(Math.random() * pool.length)]
    const bodyPool = mockBodies[key] || mockBodies.default
    body.value = bodyPool[Math.floor(Math.random() * bodyPool.length)]
    tags.value = [...defaultTags]
  }

  if (loadingTimer) clearInterval(loadingTimer)
  loadingStep.value = steps.length
  generating.value = false
  generated.value = true
}

// ====== 复制 ======
async function copyBodyWithTags() {
  try { await navigator.clipboard.writeText(bodyWithTags.value); flashToast('文案已复制') } catch { flashToast('复制失败') }
}
async function copyComplete() {
  const full = `${title.value}\n\n${bodyWithTags.value}`
  try { await navigator.clipboard.writeText(full); flashToast('完整图文已复制') } catch { flashToast('复制失败') }
}

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1500)
}
</script>

<template>
  <div class="h-full flex flex-col">
    <!-- Toast -->
    <div v-if="toast" class="fixed top-6 right-6 z-50 bg-rose-500 text-white px-5 py-3 rounded-lg shadow-lg text-sm transition-all">
      {{ toast }}
    </div>

    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-start gap-3">
        <Instagram class="w-5 h-5 text-rose-500 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">小红书图文</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">标题 × 3 · 正文 · 话题标签 · 发布建议，一次生成完整笔记</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <Zap class="w-3 h-3" />算力流水
        </button>
        <span class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2 py-1 rounded-full">消耗 10 算力 / 次</span>
        <span class="text-[10px] font-medium bg-bamboo-50 text-bamboo-800 px-2.5 py-1 rounded-full flex items-center gap-1">
          <Zap class="w-3 h-3" />1,240 算力
        </span>
      </div>
    </div>

    <!-- Body: Two Columns -->
    <div class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- ========== 左栏：配置 ========== -->
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto flex flex-col gap-3">
        <!-- 上下文卡片 -->
        <div class="bg-cream-50 rounded-lg px-3 py-2.5 border border-cream-200/60">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">今日上下文</div>
          <div class="space-y-1.5">
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500 flex items-center gap-1"><Building2 class="w-3 h-3" />酒店</span>
              <span class="font-medium text-warm-800">{{ hotel.config.name }}</span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500 flex items-center gap-1"><CloudRain class="w-3 h-3" />天气</span>
              <span class="font-medium text-warm-800">小雨 · 19°C</span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500 flex items-center gap-1"><CalendarDays class="w-3 h-3" />今日</span>
              <span class="font-medium text-warm-800">周四</span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500 flex items-center gap-1"><Sparkles class="w-3 h-3" />节点</span>
              <span class="font-medium text-amber-700">距端午 3 天</span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-warm-500 flex items-center gap-1"><LayoutGrid class="w-3 h-3" />出租率</span>
              <span class="font-medium text-bamboo-700">{{ hotel.occupancyRate }}% · 空余 {{ freeCount }} 间</span>
            </div>
          </div>
        </div>

        <!-- 内容主题 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">内容主题（可多选）</div>
          <div class="grid grid-cols-2 gap-1.5">
            <button
              v-for="t in topics" :key="t.val"
              @click="toggleTopic(t.val)"
              :class="[
                'px-2 py-1.5 rounded-md text-[11px] transition-colors border text-center',
                isTopicOn(t.val)
                  ? 'bg-rose-50 border-rose-400 text-rose-600 font-medium'
                  : 'bg-white border-cream-200 text-warm-600 hover:border-rose-300 hover:text-rose-500'
              ]"
            >
              {{ t.label }}
            </button>
          </div>
          <input
            v-model="customTopic"
            type="text"
            placeholder="或自定义输入主题..."
            class="w-full text-[12px] px-2.5 py-1.5 mt-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 placeholder:text-warm-400 focus:outline-none focus:border-rose-400"
          />
        </div>

        <!-- 内容方向 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">内容方向</div>
          <div class="flex gap-1.5 flex-wrap">
            <button
              v-for="t in tones" :key="t.val"
              @click="selectedTone = t.val"
              :class="[
                'px-2.5 py-1 rounded-full text-[11px] transition-colors border',
                selectedTone === t.val
                  ? 'bg-rose-50 border-rose-400 text-rose-600 font-medium'
                  : 'bg-white border-cream-200 text-warm-600 hover:border-rose-300'
              ]"
            >
              {{ t.label }}
            </button>
          </div>
        </div>

        <!-- 写作风格 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">写作风格</div>
          <select v-model="style" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-rose-400">
            <option value="warm">治愈温暖（主流种草风）</option>
            <option value="young">活泼元气（年轻客群）</option>
            <option value="luxury">轻奢精致（高端调性）</option>
            <option value="story">故事叙事（沉浸体验）</option>
          </select>
        </div>

        <!-- 是否配图 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">是否配图</div>
          <div class="flex gap-1.5">
            <button @click="withImage = true" :class="['px-2.5 py-1 rounded-full text-[11px] transition-colors border', withImage ? 'bg-rose-400 text-white border-rose-400' : 'bg-white text-warm-500 border-cream-300 hover:border-rose-300']">
              <Image class="w-3 h-3 inline mr-1" />配图
            </button>
            <button @click="withImage = false" :class="['px-2.5 py-1 rounded-full text-[11px] transition-colors border', !withImage ? 'bg-rose-400 text-white border-rose-400' : 'bg-white text-warm-500 border-cream-300 hover:border-rose-300']">
              纯文字
            </button>
          </div>
        </div>

        <!-- 图片尺寸 -->
        <div v-if="withImage">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">图片尺寸</div>
          <div class="flex gap-1.5 flex-wrap">
            <button v-for="s in [{v:'1:1',l:'1:1 方形'},{v:'4:3',l:'4:3 横图'},{v:'3:4',l:'3:4 竖图'},{v:'16:9',l:'16:9 宽屏'}]" :key="s.v"
              @click="imageSize = s.v"
              :class="['px-2.5 py-1 rounded-full text-[11px] transition-colors border', imageSize === s.v ? 'bg-rose-400 text-white border-rose-400' : 'bg-white text-warm-500 border-cream-300 hover:border-rose-300']">
              {{ s.l }}
            </button>
          </div>
        </div>

        <!-- 图片数量 -->
        <div v-if="withImage">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">图片数量</div>
          <div class="flex gap-1.5">
            <button v-for="n in [1,3,6,9]" :key="n"
              @click="imageCount = n"
              :class="['px-3 py-1 rounded-full text-[11px] transition-colors border', imageCount === n ? 'bg-rose-400 text-white border-rose-400' : 'bg-white text-warm-500 border-cream-300 hover:border-rose-300']">
              {{ n }} 张
            </button>
          </div>
        </div>

        <!-- 额外备注 -->
        <div>
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-1.5">
            额外备注 <span class="font-normal text-warm-400 normal-case">（选填）</span>
          </div>
          <textarea v-model="note" rows="2" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-rose-400 resize-none" placeholder="今天竹林有薄雾效果很好、刚换了新早餐菜单..." />
        </div>

        <!-- 提示 -->
        <div class="border-l-3 border-rose-400 bg-rose-50 rounded-r-lg p-2.5 text-[11px] text-rose-800 leading-relaxed">
          <strong>📌 小红书算法提示：</strong>封面前5字决定点击率；结尾必须引导收藏/关注；12:00和20:00是最佳发布窗口；话题标签覆盖「地名+场景+人群」三维度才能最大化搜索覆盖。
        </div>

        <!-- 生成按钮 -->
        <button @click="generate" :disabled="generating || !selectedTopics.size"
          class="w-full py-2.5 rounded-lg bg-rose-400 text-white text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-rose-500 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors mt-auto">
          <Sparkles class="w-4 h-4" />AI 生成小红书图文
        </button>
      </div>

      <!-- ========== 右栏：输出 ========== -->
      <div class="bg-cream-50 overflow-y-auto flex flex-col">
        <!-- 空状态 -->
        <div v-if="!generating && !generated" class="flex flex-col items-center justify-center flex-1 min-h-[400px] gap-3 text-warm-500">
          <Instagram class="w-9 h-9 opacity-25" />
          <p class="text-[13px]">选择主题后点击生成</p>
          <p class="text-[11px] opacity-70 text-center">AI 将结合今日天气、节假日、酒店特色<br>一次生成完整图文笔记</p>
        </div>

        <!-- Loading -->
        <div v-if="generating" class="flex flex-col items-center justify-center flex-1 min-h-[400px] gap-4">
          <Loader2 class="w-7 h-7 text-rose-400 animate-spin" />
          <div class="space-y-1.5">
            <div v-for="(s, i) in ['分析今日天气与节假日节点','匹配最佳内容主题与方向','撰写小红书图文']" :key="i"
              :class="['text-[11px] flex items-center gap-2 transition-colors', i < loadingStep ? 'text-rose-400' : i === loadingStep ? 'text-rose-500' : 'text-warm-400']">
              <CheckCircle2 v-if="i < loadingStep" class="w-3 h-3 text-rose-400" />
              <Loader2 v-else-if="i === loadingStep" class="w-3 h-3 animate-spin" />
              <span v-else class="w-3 h-3 rounded-full border border-warm-300" />
              {{ s }}
            </div>
          </div>
        </div>

        <!-- 输出区 -->
        <div v-if="generated" class="flex-1 overflow-auto p-4 space-y-3">
          <!-- 发布建议 -->
            <div class="bg-white border border-cream-200 rounded-lg p-3">
              <div class="text-[10px] font-semibold text-warm-600 mb-2">📋 今日发布建议</div>
              <div class="grid grid-cols-2 gap-2">
                <div class="bg-cream-50 rounded-md p-2">
                  <div class="text-[9px] text-warm-500">最佳发布时间</div>
                  <div class="text-[11px] font-medium text-warm-800">今日 20:00</div>
                </div>
                <div class="bg-cream-50 rounded-md p-2">
                  <div class="text-[9px] text-warm-500">推荐图片数量</div>
                  <div class="text-[11px] font-medium text-warm-800">{{ withImage ? imageCount + ' 张' : '纯文字' }}</div>
                </div>
                <div class="bg-cream-50 rounded-md p-2">
                  <div class="text-[9px] text-warm-500">封面图建议</div>
                  <div class="text-[11px] font-medium text-warm-800">{{ coverAdvice }}</div>
                </div>
                <div class="bg-cream-50 rounded-md p-2">
                  <div class="text-[9px] text-warm-500">预计曝光效果</div>
                  <div class="text-[11px] font-medium text-rose-500">⭐⭐⭐⭐</div>
                </div>
              </div>
              <div class="border-t border-cream-100 mt-2 pt-2">
                <div class="text-[10px] text-warm-600 leading-relaxed">{{ pubTip }}</div>
              </div>
            </div>

          <!-- 配图预览 -->
          <div v-if="withImage" class="bg-white border border-cream-200 rounded-lg p-3">
            <div class="text-[10px] font-semibold text-warm-600 mb-2">📷 配图预览</div>
            <div :class="[
              'border border-cream-200 rounded-lg overflow-hidden bg-cream-100 mx-auto',
              imageSize === '1:1' ? 'aspect-square max-w-[180px]' :
              imageSize === '4:3' ? 'aspect-[4/3] max-w-[240px]' :
              imageSize === '3:4' ? 'aspect-[3/4] max-w-[140px]' :
              'aspect-video max-w-[280px]'
            ]">
              <img v-if="xhsImageUrl" :src="xhsImageUrl" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full flex flex-col items-center justify-center text-warm-400 gap-1">
                <Image class="w-5 h-5" />
                <span class="text-[10px]">封面配图区域</span>
                <span class="text-[9px] text-warm-500">{{ imageSize }} · 小红书竖图推荐 3:4</span>
                <button class="mt-1 text-[10px] px-2 py-0.5 rounded-full bg-rose-400 text-white hover:bg-rose-500 transition-colors flex items-center gap-1">
                  <Download class="w-3 h-3" />下载素材
                </button>
              </div>
            </div>
          </div>

          <!-- 标题 -->
          <div class="bg-white border border-cream-200 rounded-lg p-4">
            <div class="flex items-center gap-2 text-[12px] font-medium text-rose-700 mb-3">
              <span class="text-base">📝</span> 标题
            </div>
            <textarea
              v-model="title"
              rows="2"
              readonly
              class="w-full text-[13px] font-medium leading-relaxed px-3 py-2 rounded-lg border border-cream-200 bg-cream-50 text-warm-800 resize-none"
            />
          </div>

          <!-- 正文 + 话题标签 -->
          <div class="bg-white border border-cream-200 rounded-lg p-4">
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-2 text-[12px] font-medium text-rose-700">
                <span class="text-base">✍️</span> 正文 · 含话题标签
              </div>
              <button @click="copyBodyWithTags" class="text-[11px] px-2 py-1 rounded-md border border-cream-300 bg-cream-50 text-warm-600 hover:bg-rose-50 hover:text-rose-600 hover:border-rose-300 transition-colors flex items-center gap-1">
                <Copy class="w-3 h-3" />一键复制
              </button>
            </div>
            <textarea
              v-model="body"
              rows="11"
              readonly
              class="w-full text-[12px] leading-relaxed px-3 py-2.5 rounded-lg border border-cream-200 bg-cream-50 text-warm-800 resize-none"
            />
            <!-- 话题标签展示 -->
            <div v-if="tags.length" class="flex flex-wrap gap-1 mt-2">
              <span
                v-for="tag in tags" :key="tag"
                class="text-[10px] px-2 py-0.5 rounded-full bg-rose-50 text-rose-500 border border-rose-200"
              >#{{ tag }}</span>
            </div>
            <div class="flex items-center justify-between mt-1.5">
              <span class="text-[10px] text-warm-500">{{ bodyCount }} 字</span>
              <span class="text-[10px] text-warm-500">建议 300–500 字，结尾引导收藏关注</span>
            </div>
          </div>

          <!-- 底部操作栏 -->
          <div class="bg-white border border-cream-200 rounded-lg px-4 py-3 flex items-center justify-between gap-3">
            <span class="text-[11px] text-warm-600">已生成完整图文 · 可直接复制使用</span>
            <button @click="copyComplete" class="text-[12px] px-3 py-1.5 rounded-lg bg-rose-400 text-white hover:bg-rose-500 transition-colors flex items-center gap-1.5">
              <Copy class="w-3.5 h-3.5" />复制完整图文
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
