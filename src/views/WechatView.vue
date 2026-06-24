<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  MessageCircleHeart, Copy, RefreshCw, Zap, Building2,
  CloudRain, CalendarDays, Sparkles, LayoutGrid, CheckCircle2,
  Clock, Loader2, Image, Download
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { generateContent, getTaskResult } from '@/api/content'

const hotel = useHotelStore()
const router = useRouter()

// ====== 状态 ======
interface SlotOutput {
  id: string
  label: string
  time: string
  typeLabel: string
  typeClass: string
  content: string
  imageUrl?: string
}

const slots = reactive({
  morning: true,
  noon: true,
  evening: true,
})

const style = ref('auto')
const length = ref('mid')
const withImage = ref(true)
const imageSize = ref('1:1')
const note = ref('')
const generating = ref(false)
const generated = ref(false)
const loadingStep = ref(0)
const toast = ref('')
let loadingTimer: ReturnType<typeof setInterval> | null = null

const outputs = ref<SlotOutput[]>([
  { id: 'morning', label: '早间', time: '08:00', typeLabel: '种草引流型', typeClass: 'bg-amber-50 text-amber-700', content: '' },
  { id: 'noon', label: '午间', time: '12:00', typeLabel: '互动留客型', typeClass: 'bg-blue-50 text-blue-600', content: '' },
  { id: 'evening', label: '晚间', time: '20:30', typeLabel: '凡尔赛满房型', typeClass: 'bg-purple-50 text-purple-700', content: '' },
])

// ====== 计算 ======
const enabledSlots = computed(() => outputs.value.filter(o => slots[o.id as keyof typeof slots]))

const activeSlots = computed(() => Object.keys(slots).filter(k => slots[k as keyof typeof slots]))

const freeCount = computed(() => {
  let c = 0
  for (const rs of hotel.roomStatuses) {
    c += rs.rooms.filter(r => r.status === 'free').length
  }
  return c
})

// ====== 风格标签 ======
const styleLabels: Record<string, string> = {
  auto: '基于房态自动判断',
  grass: '种草引流型',
  interact: '互动留客型',
  flex: '凡尔赛满房型',
  holiday: '节日特惠型',
  weather: '天气借势型',
}

function slotTypeClass(slot: string): string {
  const map: Record<string, string> = { morning: 'bg-amber-50 text-amber-700', noon: 'bg-blue-50 text-blue-600', evening: 'bg-purple-50 text-purple-700' }
  return map[slot] || 'bg-cream-50 text-warm-600'
}

// ====== 模拟文案 ======
const templates: Record<string, Record<string, string>> = {
  morning: {
    grass: `清晨的莫干山，竹叶上还挂着雨珠🌿 
推开窗，雾气从山谷里漫上来，整座竹林都在轻轻呼吸。
端午假期，给自己三天时间，在山间做一个不被定义的梦——温泉、茶香、竹影，都在等你。🏡
#莫干山民宿 #端午出行`,
    interact: `今天灶间多炖了一锅竹荪鸡汤🍲
林叔一大早去后山挖的笋，阿姨说这锅汤得煨够三个钟头才入味。
想喝的朋友评论区举手，端午入住免费送一碗～
#民宿美食 #莫干山`,
    flex: `清晨被竹林鸟鸣唤醒，私汤池边已经备好了有机早餐。
端午还剩2间房，住过的客人都说"像住在画里"。
今天，这座山间小院等你来🍃
#莫干山民宿 #端午节`,
    default: `莫干山雨后清晨，竹叶上的露珠闪烁微光🌿
推开窗，远山如黛，云雾缭绕。泡一壶老白茶，听雨滴敲打竹叶的声音。
在这个安静的早晨，把时间留给自己。
#莫干山民宿 #山居生活`,
  },
  noon: {
    grass: `午后的莫干山，阳光洒在无边泳池上☀️
泳池边的躺椅、竹林的微风、手边的冰镇梅子酒。
端午假期，来这里过一个25°C的夏天。
#莫干山 #无边泳池 #端午出游`,
    interact: `阿姨刚蒸好的青团，软糯糯的，咬一口是春天的味道🍃
下午三点大堂吧有香包手作体验，住客免费参加。
昨天入住的小朋友做了满满一桌，超可爱的～
#民宿体验 #手作 #端午节`,
    flex: `端午期间今天已经满房啦🎉
感谢各位小主的厚爱！还没订到房间的朋友，端午后第一周有特惠套餐上线。
关注我们，第一时间锁定优惠～
#满房日记 #莫干山`,
    default: `午间的阳光正好，泳池边的躺椅已经备好了浴巾☀️
约上三五好友，泡一壶茶，聊聊最近的生活。
山里的日子，慢下来就好。
#莫干山民宿 #端午假期`,
  },
  evening: {
    grass: `夜幕低垂，山里的星星比城市多好多✨
私汤池边点几盏灯，泡在温泉里看星空。
这一刻，所有的忙碌都有了意义。
晚安，莫干山🌙
#莫干山民宿 #私汤温泉 #山居夜话`,
    interact: `今晚私汤最后2个名额，群里已经接龙到第6位了🌙
泡完汤大堂还有现煮姜茶和手工饼干。
来晚了只能在竹林下听虫鸣啦～
#民宿生活 #温泉之夜`,
    flex: `今日满房，感谢每一位选择我们的客人🌙
明早竹林瑜伽还有名额，住客群接龙继续～
晚安，山里的星星比昨天更亮✨
#满房日记 #民宿日常`,
    default: `山里的夜晚真安静，虫鸣是最好的安眠曲🌙
泡一壶茶，坐在院子的藤椅上，看满天繁星。
明天又是新的一天，晚安。
#莫干山民宿 #山居夜话`,
  },
}

function pickStyle(slot: string): string {
  const s = style.value
  if (s !== 'auto') return s
  const occ = hotel.occupancyRate
  if (slot === 'morning') return occ >= 80 ? 'flex' : 'grass'
  if (slot === 'noon') return occ >= 50 ? 'interact' : 'grass'
  if (slot === 'evening') return occ >= 80 ? 'flex' : occ >= 50 ? 'interact' : 'grass'
  return 'grass'
}

// ====== 生成（对接后端 API） ======
async function generate() {
  if (!activeSlots.value.length) return

  generating.value = true
  generated.value = false
  loadingStep.value = 0

  outputs.value.forEach(o => { o.content = '' })

  const steps = ['读取今日房态与天气数据', '分析节假日营销节点', '匹配最佳文案风格', '生成朋友圈文案']
  loadingTimer = setInterval(() => {
    if (loadingStep.value < steps.length) {
      loadingStep.value++
    } else {
      if (loadingTimer) clearInterval(loadingTimer)
    }
  }, 600)

  try {
    const slots = activeSlots.value.join(',')
    const params: Record<string, any> = {
      slots: slots,
      style: style.value,
      length: length.value,
      note: note.value,
      withImage: withImage.value,
    }
    const { data: res } = await generateContent('wechat', params)
    const d = res.data || res
    const taskId = d.taskId

    // 轮询任务结果
    let attempts = 0
    while (attempts < 30) {
      await new Promise(r => setTimeout(r, 1000))
      const { data: tr } = await getTaskResult(taskId)
      const task = tr.data || tr
      if (task.status === 'done') {
        if (task.content) {
          // 内容可能是 JSON（含配图）或纯文本
          let textContent = task.content
          let imgUrl = ''
          try {
            const parsed = JSON.parse(task.content)
            if (parsed.text) { textContent = parsed.text; imgUrl = parsed.imageUrl || '' }
          } catch { /* 纯文本，直接使用 */ }

          activeSlots.value.forEach(slot => {
            const out = outputs.value.find(o => o.id === slot)
            if (out) {
              out.content = textContent
              out.imageUrl = imgUrl
              out.typeLabel = styleLabels[style.value] || 'AI生成'
              out.typeClass = slotTypeClass(slot)
            }
          })
        }
        break
      }
      if (task.status === 'failed') break
      attempts++
    }
  } catch {
    // 回退到本地生成
    activeSlots.value.forEach(slot => {
      const s = pickStyle(slot)
      const pool = templates[slot] || templates.morning
      const content = pool[s] || pool.default
      const out = outputs.value.find(o => o.id === slot)
      if (out) {
        out.content = content
        out.typeLabel = styleLabels[s] || s
        out.typeClass = slotTypeClass(slot)
      }
    })
  }

  if (loadingTimer) clearInterval(loadingTimer)
  loadingStep.value = steps.length
  generating.value = false
  generated.value = true
}

// ====== 换一版 ======
function regen(slot: string) {
  // 简单切换风格
  const s = style.value
  const alt: Record<string, string> = { grass: 'interact', interact: 'flex', flex: 'grass' }
  const key = s === 'auto' ? pickStyle(slot) : s
  const altKey = alt[key] || key
  const pool = (templates as any)[slot]
  const content = pool ? (pool[altKey] || pool.default) : ''
  const out = outputs.value.find(o => o.id === slot)
  if (out) {
    out.content = content
    out.typeLabel = styleLabels[altKey] || altKey
  }
  flashToast('已换一版')
}

// ====== 复制 ======
async function copySlot(slot: string) {
  const out = outputs.value.find(o => o.id === slot)
  if (!out?.content) return
  try {
    await navigator.clipboard.writeText(out.content)
    flashToast('已复制')
  } catch {
    flashToast('复制失败')
  }
}

async function copyAll() {
  const texts = activeSlots.value.map(slot => {
    const out = outputs.value.find(o => o.id === slot)
    return out?.content || ''
  }).filter(Boolean).join('\n\n---\n\n')
  if (!texts) return
  try {
    await navigator.clipboard.writeText(texts)
    flashToast('已复制全部')
  } catch {
    flashToast('复制失败')
  }
}

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1500)
}

function countChars(slot: string): number {
  const out = outputs.value.find(o => o.id === slot)
  return out?.content?.length || 0
}

function onContentInput(slot: string, e: Event) {
  const out = outputs.value.find(o => o.id === slot)
  if (out) {
    out.content = (e.target as HTMLTextAreaElement).value
  }
}
</script>

<template>
  <div class="h-full flex flex-col">
    <!-- Toast -->
    <div
      v-if="toast"
      class="fixed top-6 right-6 z-50 bg-bamboo-800 text-cream-100 px-5 py-3 rounded-lg shadow-lg text-sm transition-all"
    >
      {{ toast }}
    </div>

    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-start gap-3">
        <MessageCircleHeart class="w-5 h-5 text-bamboo-700 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">朋友圈文案</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">早 / 中 / 晚三档，基于今日房态 + 天气 + 节假日自动适配语气风格</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <Zap class="w-3 h-3" />算力流水
        </button>
        <span class="text-[10px] font-medium bg-amber-50 text-amber-700 px-2 py-1 rounded-full">消耗 8 算力 / 次</span>
        <span class="text-[10px] font-medium bg-bamboo-50 text-bamboo-800 px-2.5 py-1 rounded-full flex items-center gap-1">
          <Zap class="w-3 h-3" />
          1,240 算力
        </span>
      </div>
    </div>

    <!-- Body: Two Columns -->
    <div class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- ========== 左栏：配置 ========== -->
      <div class="border-r border-cream-200/60 p-4 overflow-y-auto">
        <!-- 上下文卡片 -->
        <div class="bg-cream-50 rounded-lg px-3 py-2.5 mb-4 border border-cream-200/60">
          <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">今日上下文 · 自动读取</div>
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

        <div class="text-[10px] font-semibold text-warm-500 tracking-wider uppercase mb-2">生成设置</div>

        <!-- 时段选择 -->
        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">发布时段</div>
          <div class="flex gap-1.5">
            <button
              v-for="o in outputs"
              :key="o.id"
              @click="(slots as any)[o.id] = !(slots as any)[o.id]"
              :class="[
                'px-2.5 py-1 rounded-full text-[11px] transition-colors border',
                (slots as any)[o.id]
                  ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800'
                  : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400'
              ]"
            >
              {{ o.label }} {{ o.time }}
            </button>
          </div>
        </div>

        <!-- 内容风格 -->
        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">内容风格</div>
          <select v-model="style" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400">
            <option value="auto">基于房态自动判断（推荐）</option>
            <option value="grass">种草引流型</option>
            <option value="interact">互动留客型</option>
            <option value="flex">凡尔赛满房型</option>
            <option value="holiday">节日特惠型</option>
            <option value="weather">天气借势型</option>
          </select>
        </div>

        <!-- 文案长度 -->
        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">文案长度</div>
          <select v-model="length" class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400">
            <option value="short">简短精练（60字以内）</option>
            <option value="mid">适中自然（80-120字）</option>
            <option value="long">详细丰富（150字以内）</option>
          </select>
        </div>

        <!-- 是否配图 -->
        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">是否配图</div>
          <div class="flex gap-1.5">
            <button
              @click="withImage = true"
              :class="[
                'px-3 py-1 rounded-full text-[11px] transition-colors border',
                withImage
                  ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800'
                  : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400'
              ]"
            >
              <Image class="w-3 h-3 inline mr-1" />配图
            </button>
            <button
              @click="withImage = false"
              :class="[
                'px-3 py-1 rounded-full text-[11px] transition-colors border',
                !withImage
                  ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800'
                  : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400'
              ]"
            >
              纯文字
            </button>
          </div>
        </div>

        <!-- 图片尺寸 -->
        <div v-if="withImage" class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">图片尺寸</div>
          <div class="flex gap-1.5">
            <button
              v-for="size in [
                { v: '1:1', label: '1:1 方形' },
                { v: '4:3', label: '4:3 横图' },
                { v: '3:4', label: '3:4 竖图' },
                { v: '16:9', label: '16:9 宽屏' },
              ]"
              :key="size.v"
              @click="imageSize = size.v"
              :class="[
                'px-2.5 py-1 rounded-full text-[11px] transition-colors border',
                imageSize === size.v
                  ? 'bg-bamboo-800 text-bamboo-100 border-bamboo-800'
                  : 'bg-white text-warm-500 border-cream-300 hover:border-bamboo-400'
              ]"
            >
              {{ size.label }}
            </button>
          </div>
        </div>

        <!-- 额外备注 -->
        <div class="mb-3">
          <div class="text-[11px] font-medium text-warm-600 mb-1.5">
            额外备注
            <span class="font-normal text-warm-400">（选填）</span>
          </div>
          <textarea
            v-model="note"
            rows="3"
            class="w-full text-[12px] px-2.5 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400 resize-none"
            placeholder="今天有客人打卡竹林拍了很好看的照片、下午有香包手作活动、刚上了新的早餐菜单..."
          />
        </div>

        <!-- 运营提示 -->
        <div class="border-l-3 border-amber-400 bg-amber-50 rounded-r-lg p-2.5 mb-3 text-[11px] text-amber-800 leading-relaxed">
          <strong>运营提示：</strong>全员朋友圈建议在同一时段±15分钟内发布，三档同步形成刷屏感效果最好。「仅剩X间」数字需与实时房态保持一致。
        </div>

        <!-- 生成按钮 -->
        <button
          @click="generate"
          :disabled="generating || !activeSlots.length"
          class="w-full py-2.5 rounded-lg bg-bamboo-800 text-bamboo-100 text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors"
        >
          <Sparkles class="w-4 h-4" />
          AI 生成朋友圈文案
        </button>
      </div>

      <!-- ========== 右栏：输出 ========== -->
      <div class="bg-cream-50 p-4 overflow-y-auto">
        <!-- 空状态 -->
        <div v-if="!generating && !generated" class="flex flex-col items-center justify-center h-full min-h-[360px] gap-3 text-warm-500">
          <MessageCircleHeart class="w-8 h-8 opacity-30" />
          <p class="text-[13px]">配置好参数后点击生成</p>
          <p class="text-[11px] opacity-70">AI 将结合今日天气、节假日、出租率自动适配风格</p>
        </div>

        <!-- Loading -->
        <div v-if="generating" class="flex flex-col items-center justify-center min-h-[360px] gap-4">
          <Loader2 class="w-6 h-6 text-bamboo-800 animate-spin" />
          <div class="space-y-2">
            <div
              v-for="(s, i) in ['读取今日房态与天气数据','分析节假日营销节点','匹配最佳文案风格','生成朋友圈文案']"
              :key="i"
              :class="[
                'text-[11px] flex items-center gap-2 transition-colors',
                i < loadingStep ? 'text-bamboo-600' : i === loadingStep ? 'text-bamboo-800' : 'text-warm-400'
              ]"
            >
              <CheckCircle2 v-if="i < loadingStep" class="w-3 h-3 text-bamboo-600" />
              <Loader2 v-else-if="i === loadingStep" class="w-3 h-3 animate-spin" />
              <span v-else class="w-3 h-3 rounded-full border border-warm-300" />
              {{ s }}
            </div>
          </div>
        </div>

        <!-- 输出区 -->
        <div v-if="generated" class="space-y-3">
          <template v-for="o in enabledSlots" :key="o.id">
            <div v-if="o.content" class="bg-white border border-cream-300/60 rounded-lg p-4">
              <!-- 头部 -->
              <div class="flex items-center justify-between mb-3">
                <div class="flex items-center gap-2">
                  <span class="text-xs font-medium text-warm-800">{{ o.label }} {{ o.time }}</span>
                  <span :class="['text-[10px] px-2 py-0.5 rounded-full font-medium', o.typeClass]">
                    {{ o.typeLabel }}
                  </span>
                </div>
                <div class="flex gap-1.5">
                  <button @click="regen(o.id)" class="text-[11px] px-2 py-1 rounded-md border border-cream-300 bg-cream-50 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 transition-colors flex items-center gap-1">
                    <RefreshCw class="w-3 h-3" />换一版
                  </button>
                  <button @click="copySlot(o.id)" class="text-[11px] px-2 py-1 rounded-md border border-cream-300 bg-cream-50 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 transition-colors flex items-center gap-1">
                    <Copy class="w-3 h-3" />复制
                  </button>
                </div>
              </div>
              <!-- 内容 -->
              <textarea
                :value="o.content"
                @input="(e) => onContentInput(o.id, e)"
                rows="5"
                class="w-full text-[13px] leading-relaxed px-3 py-2 rounded-lg border border-cream-200 bg-cream-50 text-warm-800 resize-none focus:outline-none focus:border-bamboo-400 focus:bg-white"
              />
              <!-- 配图预览 -->
              <div v-if="withImage" class="mt-3 border border-cream-200 rounded-lg overflow-hidden bg-cream-100" :class="{
                'aspect-square max-w-[180px]': imageSize === '1:1',
                'aspect-[4/3] max-w-[240px]': imageSize === '4:3',
                'aspect-[3/4] max-w-[140px]': imageSize === '3:4',
                'aspect-video max-w-[280px]': imageSize === '16:9',
              }">
                <template v-if="o.imageUrl">
                  <img :src="o.imageUrl" class="w-full h-full object-cover" />
                </template>
                <div v-else class="w-full h-full flex flex-col items-center justify-center text-warm-400 gap-1.5">
                  <Image class="w-5 h-5" />
                  <span class="text-[10px]">配图区域</span>
                  <span class="text-[9px] text-warm-500">{{ imageSize }} 尺寸</span>
                  <button class="mt-1 text-[10px] px-2 py-0.5 rounded-full bg-bamboo-800 text-bamboo-100 hover:bg-bamboo-900 transition-colors flex items-center gap-1">
                    <Download class="w-3 h-3" />
                    下载素材
                  </button>
                </div>
              </div>
              <!-- 底部 -->
              <div class="flex items-center justify-between mt-2">
                <span class="text-[10px] text-warm-500">{{ countChars(o.id) }} 字</span>
                <span class="text-[10px] text-warm-500 flex items-center gap-1">
                  <Clock class="w-3 h-3" />
                  建议 {{ o.time }} 发布
                </span>
              </div>
            </div>
          </template>

          <!-- 底部操作栏 -->
          <div class="bg-white border border-cream-300/60 rounded-lg px-4 py-3 flex items-center justify-between gap-3">
            <span class="text-[11px] text-warm-600">已生成 {{ enabledSlots.filter(o => o.content).length }} 档文案，可直接编辑后发布</span>
            <div class="flex gap-2">
              <button @click="copyAll" class="text-[12px] px-3 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-700 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
                <Copy class="w-3.5 h-3.5" />一键复制全部
              </button>
              <button @click="generate" class="text-[12px] px-3 py-1.5 rounded-lg bg-bamboo-800 text-bamboo-100 hover:bg-bamboo-900 transition-colors flex items-center gap-1.5">
                <RefreshCw class="w-3.5 h-3.5" />重新生成
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
