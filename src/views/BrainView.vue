<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { useHotelStore } from '@/stores/hotel'
import { brainChat } from '@/api/content'
import {
  Brain, ArrowUp, User, CloudRain, Sparkles,
  Instagram, Coins, Building2, MapPin, LayoutGrid,
  CalendarDays
} from 'lucide-vue-next'

const hotel = useHotelStore()

// ====== 聊天状态 ======
interface Message {
  role: 'user' | 'ai'
  content: string
  actions?: string[]
}

const messages = ref<Message[]>([
  {
    role: 'ai',
    content: `你好！我是 **${hotel.config.name}** 的**运营智慧大脑**，已加载你的民宿所有运营上下文——房态、天气、节假日、客群特征、竞争环境。

你可以直接问我任何运营问题，比如：
<span class="tag tg">今天怎么卖房</span>
<span class="tag to">端午定价策略</span>
<span class="tag tp">小红书内容方向</span>
<span class="tag tb">差评怎么回</span>
<span class="tag tr">暑假如何备战</span>

说出你现在最头疼的运营问题吧。`,
  },
])

const input = ref('')
const sending = ref(false)
const typing = ref(false)
const chatContainer = ref<HTMLElement | null>(null)

// ====== 系统提示词 ======
const systemPrompt = computed(() => {
  const cfg = hotel.config
  const rts = hotel.roomTypes
  const occ = hotel.occupancyRate
  const free = hotel.totalRooms - hotel.totalSold
  const sold = hotel.totalSold

  let roomsDesc = rts.map(r => `${r.name}（¥${r.basePrice}基础价，${r.count}间）`).join('、')

  return `你是"${cfg.name}"的运营智慧大脑，一个专为民宿运营打造的AI智能体。

【民宿基础信息】
- 名称：${cfg.name}（${cfg.type}）
- 位置：${cfg.city}
- 核心特色：${cfg.tags}
- 目标客群：${cfg.targetAudience}
- 周边资源：${cfg.nearby}
- 房型：${roomsDesc}

【今日实时数据】
- 今日出租率：${occ}%，已售${sold}间，空余${free}间，维修${cfg.totalRooms - sold - free}间
- RevPAR：¥${hotel.revpar}
- 日期：周四

【你的角色与能力】
你是一个深度理解酒店/民宿运营的AI智能体，能够：
1. 基于实时房态、天气、节假日给出即时运营策略
2. 提供各平台（小红书、抖音、朋友圈、美团/携程）的内容创作指导
3. 给出动态定价建议和收益管理策略
4. 分析营销机会，制定推广方案
5. 提供客户服务话术、好评引导策略
6. 识别运营风险并给出预案

【回答风格】
- 语言简洁、实操性强，直接给出可执行的动作
- 结合民宿的具体特色给出个性化建议
- 每次回答聚焦在3-5个核心要点
- 用中文回复，语气专业但亲切
- 回答结构清晰，适当使用换行和要点罗列
- 不超过300字，精炼有力`
})

// ====== 模拟 AI 响应 ======
const responses: Record<string, string> = {
  default: `根据 **${hotel.config.name}** 当前情况，以下是我的运营建议：

**1. 即时行动**
今天出租率 **${hotel.occupancyRate}%**，还有 **${hotel.totalRooms - hotel.totalSold}** 间空房。建议对空房做限时折扣，通过朋友圈和民宿社群发布"今夜特价"。

**2. 内容营销**
结合 **${hotel.config.tags?.split('、').slice(0, 2).join('、')}** 的特色，拍摄短视频发小红书，配上真实客人体验。

**3. 客户运营**
对即将到店的客人提前发送天气提醒和出行攻略，提升入住体验和好评率。

**4. 数据关注**
持续监控未来7天预订趋势，周末房源紧张时适当提价，平日做促销补量。`,

  rain: `今天**下雨天**，是转化犹豫客户的好时机：

**1. 雨天专属优惠**
推出"听雨·私汤"套餐——温泉+有机茶歇+竹林雨景拍照，定价 ¥598/人，限时3小时。

**2. 内容角度**
- 小红书："莫干山最美雨天，竹林听雨泡私汤"
- 朋友圈：发民宿窗景雨滴特写 + 温馨内景对比

**3. 到店服务**
大堂备好姜茶、烘干机、雨伞，给客人"被照顾到"的惊喜感。

**4. 定价调整**
雨天退订率上升，今晚空房建议降价20%走量，保证入住率。`,

  festival: `围绕**端午假期的营销优先级**，建议如下：

**🔥 第一优先级：渠道冲量**
- 携程/美团：端午专题页上线，首图突出"粽享山野"
- 投放"端午不加价"标签吸引比价用户

**📱 第二优先级：内容种草**
- 小红书：发布"端午莫干山避暑攻略"，植入民宿
- 抖音：15秒短视频"包粽子体验+无边泳池"

**🎯 第三优先级：私域转化**
- 朋友圈3天倒计时海报："端午还有X间"
- 老客群发：端午专属折扣码，限量5张

**💰 定价策略**
端午假期（5.31-6.2）基础价上浮30%，提前7天预付优惠15%。`,

  xhs: `针对 **${hotel.config.name}** 的小红书差异化打法：

**1. 差异化定位**
不打"最美民宿"这种通用卖点。聚焦 **${hotel.config.tags?.split('、')[0]}** 这个独特标签，打造"莫干山唯一竹林私汤民宿"心智。

**2. 内容矩阵**
- 攻略型："莫干山2天1夜怎么玩"（软植入）
- 体验型："在竹林里泡温泉是什么体验"（沉浸感）
- 知识型："民宿老板教你选房型"（专业度）

**3. 视觉风格**
走"侘寂+竹林"调性——低饱和度、自然光影、留白构图。区别于竞争民宿的网红ins风。

**4. KOC策略**
邀请5位生活方式博主免费体验，产出真实探店内容，比头部KOL投放更高效。`,

  pricing: `针对今晚空房的**动态定价建议**：

**当前状态**
出租率 ${hotel.occupancyRate}%，空${hotel.totalRooms - hotel.totalSold}间

**定价策略**
${hotel.occupancyRate >= 90 ? '出租率已超90%，剩余房间维持原价，可搭配升级服务提升客单价。' : hotel.occupancyRate >= 70 ? '出租率中位，建议剩余房间降价15-20%做闪购，晚上8点前未售出再加码。' : '出租率偏低，立即启动"今夜特价"策略，价格下调30%，覆盖基础成本即可。'}

**执行动作**
1. 在携程/美团开启今夜特价标签
2. 朋友圈发布"今晚最后一间"文案
3. 民宿社群推送限时折扣码

**注意**
不要低于日常价的50%，以免伤害品牌定位。`,
}

function getAIResponse(q: string): string {
  const ql = q.toLowerCase()
  if (ql.includes('雨') || ql.includes('空房') || ql.includes('天气')) return responses.rain
  if (ql.includes('端午') || ql.includes('节日') || ql.includes('营销优先级') || ql.includes('假期')) return responses.festival
  if (ql.includes('小红书') || ql.includes('差异化') || ql.includes('打法') || ql.includes('内容')) return responses.xhs
  if (ql.includes('定价') || ql.includes('价格') || ql.includes('空房') && ql.includes('今晚')) return responses.pricing
  return responses.default
}

// ====== 快捷问题 ======
const quickQuestions = [
  { icon: CloudRain, title: '雨天空房怎么办', desc: '结合今日天气给出即时策略', q: '今天雨天空了2间房，我现在应该怎么做？' },
  { icon: Sparkles, title: '端午营销优先级', desc: '节假日作战顺序规划', q: '端午节3天我应该重点做哪些营销动作？优先级怎么排？' },
  { icon: Instagram, title: '小红书差异化打法', desc: '基于自身特色的内容策略', q: '我们民宿的竞争优势是什么？怎么在小红书上差异化打法？' },
  { icon: Coins, title: '今晚空房定价建议', desc: '实时房态驱动定价决策', q: `现在出租率${hotel.occupancyRate}%，今晚还有${hotel.totalRooms - hotel.totalSold}间空房，定价应该怎么调？` },
]

// ====== 发送消息 ======
async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return

  input.value = ''
  sending.value = true

  // 添加用户消息
  messages.value.push({ role: 'user', content: text })

  typing.value = true
  await nextTick()
  scrollToBottom()

  // 调用后端 AI
  try {
    const { data: res } = await brainChat(text)
    const d = res.data || res
    typing.value = false
    const reply = d.content || getAIResponse(text)
    const suggestions = d.suggestions || getSuggestions(text, reply)
    messages.value.push({ role: 'ai', content: reply, actions: suggestions })
  } catch {
    typing.value = false
    const reply = getAIResponse(text)
    const suggestions = getSuggestions(text, reply)
    messages.value.push({ role: 'ai', content: reply, actions: suggestions })
  }

  await nextTick()
  scrollToBottom()
  sending.value = false
}

function askQ(q: string) {
  input.value = q
  send()
}

// ====== 建议追问 ======
function getSuggestions(q: string, reply: string): string[] {
  const pool = [
    '帮我写今天的朋友圈文案',
    '端午海报文案怎么写',
    '今晚空房如何定价',
    '小红书选题推荐',
    '怎么引导客人好评',
    '暑假营销怎么准备',
    '差评该怎么回复',
    '抖音口播文案生成',
    '如何设计端午套餐',
    '私域客户怎么运营',
  ]
  return pool
    .filter(s => !q.includes(s.slice(0, 4)) && !reply.includes(s.slice(0, 4)))
    .slice(0, 3)
}

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

// ====== 格式化文本 ======
function formatContent(content: string): string {
  return content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}
</script>

<template>
  <div class="h-full flex flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3 mb-4">
      <div class="w-10 h-10 rounded-full bg-bamboo-50 flex items-center justify-center flex-shrink-0">
        <Brain class="w-5 h-5 text-bamboo-800" />
      </div>
      <div>
        <div class="text-sm font-medium text-warm-900">运营智慧大脑</div>
        <div class="text-[11px] text-bamboo-800 flex items-center gap-1 mt-0.5">
          <span class="w-1.5 h-1.5 rounded-full bg-bamboo-600 flex-shrink-0" />
          已加载 {{ hotel.config.name }} 运营知识库
        </div>
      </div>
    </div>

    <!-- 上下文栏 -->
    <div class="flex gap-2 flex-wrap mb-3 px-3 py-2.5 bg-white rounded-lg border border-cream-300/60 text-[11px] text-warm-600">
      <div class="flex items-center gap-1">
        <Building2 class="w-3 h-3" />
        {{ hotel.config.name }}
      </div>
      <span class="text-cream-400">·</span>
      <div class="flex items-center gap-1">
        <MapPin class="w-3 h-3" />
        {{ hotel.config.city?.replace('浙江·', '') }}
      </div>
      <span class="text-cream-400">·</span>
      <div class="flex items-center gap-1">
        <LayoutGrid class="w-3 h-3" />
        出租率 {{ hotel.occupancyRate }}%
      </div>
      <span class="text-cream-400">·</span>
      <div class="flex items-center gap-1">
        <CloudRain class="w-3 h-3" />
        今日小雨
      </div>
      <span class="text-cream-400">·</span>
      <div class="flex items-center gap-1">
        <CalendarDays class="w-3 h-3" />
        距端午 3 天
      </div>
    </div>

    <!-- 快捷问题 -->
    <div class="mb-3">
      <div class="text-[10px] font-semibold text-warm-500 tracking-wider mb-2 uppercase">快捷问题</div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <button
          v-for="qq in quickQuestions"
          :key="qq.title"
          @click="askQ(qq.q)"
          class="text-left bg-white border border-cream-300/60 rounded-lg px-3 py-2.5 hover:border-bamboo-400 hover:bg-bamboo-50 transition-all duration-150 group"
        >
          <component :is="qq.icon" class="w-4 h-4 text-warm-500 group-hover:text-bamboo-700 mb-1" />
          <div class="text-xs font-medium text-warm-800 group-hover:text-bamboo-900">{{ qq.title }}</div>
          <div class="text-[11px] text-warm-500 mt-0.5">{{ qq.desc }}</div>
        </button>
      </div>
    </div>

    <!-- 分割线 -->
    <div class="border-b border-cream-200/60 mb-3" />

    <!-- 聊天区域 -->
    <div ref="chatContainer" class="flex-1 overflow-y-auto space-y-3 min-h-0 pr-1">
      <div
        v-for="(msg, i) in messages"
        :key="i"
        :class="['flex gap-2.5 items-start', msg.role === 'user' && 'flex-row-reverse']"
      >
        <!-- 头像 -->
        <div
          :class="[
            'w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0',
            msg.role === 'ai' ? 'bg-bamboo-50 text-bamboo-800' : 'bg-indigo-50 text-indigo-700'
          ]"
        >
          <Brain v-if="msg.role === 'ai'" class="w-3.5 h-3.5" />
          <User v-else class="w-3.5 h-3.5" />
        </div>

        <!-- 气泡 -->
        <div
          :class="[
            'max-w-[82%] px-3 py-2.5 rounded-lg text-[13px] leading-relaxed',
            msg.role === 'ai'
              ? 'bg-white border border-cream-300/60 rounded-tl-sm text-warm-800'
              : 'bg-bamboo-50 border border-bamboo-200 rounded-tr-sm text-bamboo-950'
          ]"
        >
          <div v-html="formatContent(msg.content)" />

          <!-- 追问按钮 -->
          <div v-if="msg.actions && msg.actions.length" class="flex gap-1.5 flex-wrap mt-2 pt-2 border-t border-cream-200/60">
            <button
              v-for="act in msg.actions"
              :key="act"
              @click="askQ(act)"
              class="text-[11px] px-2.5 py-1 rounded-full border border-cream-300 bg-cream-50 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors"
            >
              {{ act }}
            </button>
          </div>
        </div>
      </div>

      <!-- 打字动画 -->
      <div v-if="typing" class="flex gap-2.5 items-start">
        <div class="w-7 h-7 rounded-full bg-bamboo-50 text-bamboo-800 flex items-center justify-center flex-shrink-0">
          <Brain class="w-3.5 h-3.5" />
        </div>
        <div class="bg-white border border-cream-300/60 rounded-lg rounded-tl-sm px-3 py-2.5 flex items-center gap-1.5">
          <span class="w-1.5 h-1.5 rounded-full bg-warm-400 animate-pulse" />
          <span class="w-1.5 h-1.5 rounded-full bg-warm-400 animate-pulse" style="animation-delay: 0.2s" />
          <span class="w-1.5 h-1.5 rounded-full bg-warm-400 animate-pulse" style="animation-delay: 0.4s" />
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="flex gap-2 pt-3 border-t border-cream-200/60 mt-3">
      <input
        v-model="input"
        type="text"
        placeholder="问我任何运营问题，比如：今天怎么促销空房？"
        class="flex-1 text-[13px] px-3 py-2 rounded-lg border border-cream-300 bg-white text-warm-800 placeholder:text-warm-400 focus:outline-none focus:border-bamboo-400 transition-colors"
        :disabled="sending"
        @keydown.enter="send"
      />
      <button
        @click="send"
        :disabled="sending || !input.trim()"
        class="px-4 py-2 rounded-lg bg-bamboo-800 text-bamboo-100 text-[13px] font-medium flex items-center gap-1.5 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors flex-shrink-0"
      >
        <ArrowUp class="w-3.5 h-3.5" />
        发送
      </button>
    </div>
  </div>
</template>
