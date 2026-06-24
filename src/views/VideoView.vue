<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Mic, Zap, Copy, Loader2, CheckCircle2,
  Sparkles
} from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { generateContent, getTaskResult } from '@/api/content'

const hotel = useHotelStore()
const router = useRouter()

// ====== 状态 ======
const generating = ref(false)
const generated = ref(false)
const loadingStep = ref(0)
const toast = ref('')
let loadingTimer: ReturnType<typeof setInterval> | null = null

// ====== 配置 ======
// 创作视角
const selectedView = ref('商家老板')
const views = [
  { val: '商家老板', hint: '用老板本人口吻介绍自家店铺，文案适合「门店账号」或「老板个人账号」' },
  { val: '探店达人', hint: '以探店博主视角真实体验分享，内容更有可信度，适合达人合作或账号种草' },
  { val: '消费者', hint: '以真实住客口吻分享体验，强调真实感，适合UGC式内容和好评截图' },
  { val: '其他', hint: '自定义视角，AI将根据卖点自动匹配最适合的叙述角度' },
]

const viewHint = computed(() => views.find(v => v.val === selectedView.value)?.hint || '')

// 卖点
const sellingPoints = ref('所在城市：浙江·莫干山\n行业类别：精品民宿\n主推产品：竹语大床房 / 山景套房\n产品卖点：竹林私汤温泉、有机早餐、距莫干山景区5分钟')
const pointCount = computed(() => sellingPoints.value.length)

// 文案风格
const selectedStyle = ref('沉浸式体验')
const styles = [
  { icon: '🌿', val: '沉浸式体验' },
  { icon: '👯', val: '闺蜜式分享' },
  { icon: '📚', val: '知识分享' },
  { icon: '😄', val: '幽默搞笑' },
  { icon: '🔥', val: '薅羊毛型' },
  { icon: '🎯', val: '避坑指南' },
  { icon: '🎥', val: '轻松Vlog式' },
  { icon: '💫', val: '情绪共鸣' },
]

// 营销目的
const selectedGoal = ref('引流涨粉')
const goals = [
  { icon: '📈', val: '引流涨粉', desc: '增加主页关注，扩大账号影响力' },
  { icon: '🛒', val: '直接转化预订', desc: '引导私信/评论，推动立即下单' },
  { icon: '🌱', val: '品牌种草', desc: '提升民宿知名度，建立品牌形象' },
  { icon: '🎁', val: '活动推广', desc: '推广端午/暑假等限时活动' },
]

// 视频时长
const selectedDuration = ref('30')
const durations = [
  { val: '15', label: '⚡ 15秒', sub: '约60字' },
  { val: '30', label: '🎯 30秒', sub: '约150字' },
  { val: '60', label: '📖 60秒', sub: '约300字' },
]

// 生成条数
const generateCount = ref(3)

// ====== 输出内容 ======
interface ScriptVersion {
  num: number
  label: string
  badge: string
  badgeClass: string
  text: string
}

const versions = ref<ScriptVersion[]>([])

// ====== 生成 ======
const progressSteps = ['理解卖点与创作视角', '匹配最佳文案风格', '生成多版本口播脚本', '推荐BGM与发布建议']

async function generate() {
  if (!sellingPoints.value.trim()) return

  generating.value = true
  generated.value = false
  loadingStep.value = 0
  versions.value = []

  loadingTimer = setInterval(() => {
    if (loadingStep.value < progressSteps.length) {
      loadingStep.value++
    } else { if (loadingTimer) clearInterval(loadingTimer) }
  }, 700)

  try {
    const { data: res } = await generateContent('video', {
      sellingPoints: sellingPoints.value,
      view: selectedView.value,
      style: selectedStyle.value,
      goal: selectedGoal.value,
      duration: selectedDuration.value,
      count: generateCount.value,
    })
    const d = res.data || res
    const taskId = d.taskId

    let attempts = 0
    while (attempts < 30) {
      await new Promise(r => setTimeout(r, 1000))
      const { data: tr } = await getTaskResult(taskId)
      const task = (tr as any).data || tr
      if (task.status === 'done' && task.content) {
        const count = generateCount.value
        for (let i = 0; i < count; i++) {
          versions.value.push({
            num: i + 1, label: i === 0 ? 'AI生成版' : `版本${i + 1}`,
            badge: i === 0 ? '推荐' : '备选',
            badgeClass: i === 0 ? 'bg-bamboo-50 text-bamboo-700' : 'bg-blue-50 text-blue-600',
            text: task.content,
          })
        }
        break
      }
      if (task.status === 'failed') break
      attempts++
    }
  } catch {
    // 回退
    versions.value = [
      { num: 1, label: '沉浸种草版', badge: '推荐', badgeClass: 'bg-bamboo-50 text-bamboo-700', text: '（后端未连接，使用示例文案）\n\n你想在莫干山藏着一片只有当地人知道的竹林吗？🌿\n\n推开后门走进野竹林，私汤就建在竹林旁，泡着温泉看竹影摇曳。\n\n端午还剩最后几间房，左下角戳一下！❤️' },
    ]
  }

  if (loadingTimer) clearInterval(loadingTimer)
  loadingStep.value = progressSteps.length
  generating.value = false
  generated.value = true
}

// ====== 复制 ======
function copyScript(text: string) {
  navigator.clipboard.writeText(text).then(() => flashToast('已复制'))
    .catch(() => flashToast('复制失败'))
}

function copyAll() {
  const all = versions.value.map(v => `【${v.label}】\n${v.text}`).join('\n\n---\n\n')
  navigator.clipboard.writeText(all).then(() => flashToast('已复制全部版本'))
    .catch(() => flashToast('复制失败'))
}

function flashToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = '' }, 1500)
}
</script>

<template>
  <div class="h-full flex flex-col">
    <!-- Toast -->
    <div v-if="toast" class="fixed top-6 right-6 z-50 bg-bamboo-800 text-white px-5 py-3 rounded-lg shadow-lg text-sm">
      {{ toast }}
    </div>

    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-start gap-3">
        <Mic class="w-5 h-5 text-bamboo-800 mt-0.5" />
        <div>
          <h1 class="text-sm font-semibold text-bamboo-900">短视频口播文案</h1>
          <p class="text-[11px] text-warm-500 mt-0.5">多视角 · 多风格 · 多目的，AI 生成专属口播脚本</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <button @click="router.push('/credits')" class="text-[10px] px-2 py-1 rounded-full border border-cream-300 bg-white text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors flex items-center gap-1">
          <Zap class="w-3 h-3" />算力流水
        </button>
        <span class="text-[10px] font-medium bg-bamboo-50 text-bamboo-700 px-2 py-1 rounded-full">消耗 12 算力 / 次</span>
      </div>
    </div>

    <!-- Body: Two Columns -->
    <div class="flex-1 min-h-0 grid grid-cols-[340px_1fr] gap-0 border border-cream-300/60 rounded-lg overflow-hidden bg-white">
      <!-- ========== 左栏：配置 ========== -->
      <div class="border-r border-cream-200/60 overflow-y-auto flex flex-col">
        <!-- 创作视角 -->
        <div class="px-4 pt-4">
          <div class="text-[12px] font-medium text-warm-800 mb-2">创作视角</div>
          <div class="flex gap-1.5 flex-wrap">
            <button
              v-for="v in views" :key="v.val"
              @click="selectedView = v.val"
              :class="[
                'px-3 py-1.5 rounded-full text-[11px] transition-colors border',
                selectedView === v.val
                  ? 'bg-bamboo-800 text-white border-bamboo-800'
                  : 'bg-white text-warm-600 border-cream-200 hover:border-bamboo-300'
              ]"
            >{{ v.val }}</button>
          </div>
          <div class="text-[10px] text-warm-500 bg-cream-50 rounded-lg px-3 py-2 mt-2 leading-relaxed min-h-[40px] transition-all">
            {{ viewHint }}
          </div>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 商家/卖点 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-warm-800 mb-2">
            商家 / 卖点 <span class="text-rose-400">*</span>
            <span class="text-[10px] font-normal text-warm-500 ml-1">（填写越详细，生成效果越好）</span>
          </div>
          <div class="border border-cream-200 rounded-lg overflow-hidden focus-within:border-bamboo-400 transition-colors">
            <textarea
              v-model="sellingPoints"
              rows="5"
              maxlength="1000"
              class="w-full text-[12px] leading-relaxed px-3 py-2.5 border-0 bg-white text-warm-800 resize-none focus:outline-none"
              placeholder="所在城市：&#10;行业类别：&#10;主推产品：&#10;产品卖点："
            />
            <div class="flex justify-end px-3 py-1.5 bg-cream-50 text-[10px] text-warm-500">
              {{ pointCount }}/1000
            </div>
          </div>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 文案风格 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-warm-800 mb-2">文案风格</div>
          <select v-model="selectedStyle" class="w-full text-[12px] px-2.5 py-2 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400">
            <option v-for="s in styles" :key="s.val" :value="s.val">{{ s.icon }} {{ s.val }}</option>
          </select>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 营销目的 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-warm-800 mb-2">营销目的</div>
          <select v-model="selectedGoal" class="w-full text-[12px] px-2.5 py-2 rounded-lg border border-cream-300 bg-white text-warm-800 focus:outline-none focus:border-bamboo-400">
            <option v-for="g in goals" :key="g.val" :value="g.val">{{ g.icon }} {{ g.val }} - {{ g.desc }}</option>
          </select>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 视频时长 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-warm-800 mb-2">内容字数 / 时长</div>
          <div class="flex gap-2">
            <button
              v-for="d in durations" :key="d.val"
              @click="selectedDuration = d.val"
              :class="[
                'flex-1 py-2.5 rounded-lg text-[11px] border text-center leading-tight transition-colors',
                selectedDuration === d.val
                  ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-700 font-medium'
                  : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-200'
              ]"
            >
              {{ d.label }}<br><span class="text-[9px] opacity-60">{{ d.sub }}</span>
            </button>
          </div>
        </div>

        <div class="mx-4 my-3 border-t border-cream-200" />

        <!-- 生成条数 -->
        <div class="px-4">
          <div class="text-[12px] font-medium text-warm-800 mb-2">生成条数</div>
          <div class="flex gap-2">
            <button v-for="n in [1,2,3]" :key="n"
              @click="generateCount = n"
              :class="['flex-1 py-2 rounded-lg text-[11px] border text-center transition-colors', generateCount === n ? 'bg-bamboo-50 border-bamboo-400 text-bamboo-700 font-medium' : 'bg-white border-cream-200 text-warm-600 hover:border-bamboo-200']">
              {{ n }} 条
            </button>
          </div>
        </div>

        <!-- 底部按钮 -->
        <div class="mt-auto px-4 py-3 border-t border-cream-200 bg-white flex-shrink-0 flex gap-2">
          <button @click="generate" :disabled="generating || !sellingPoints.trim()"
            class="flex-1 py-2.5 rounded-lg bg-bamboo-800 text-white text-[13px] font-medium flex items-center justify-center gap-2 hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 disabled:cursor-not-allowed transition-colors">
            <Sparkles class="w-4 h-4" />立即生成
          </button>
        </div>
      </div>

      <!-- ========== 右栏：输出 ========== -->
      <div class="bg-cream-50 overflow-y-auto flex flex-col min-h-0">
        <!-- 空状态 -->
        <div v-if="!generating && !generated" class="flex flex-col items-center justify-center flex-1 min-h-[400px] gap-3 text-warm-500">
          <Mic class="w-10 h-10 opacity-20" />
          <p class="text-[13px]">填写卖点并选择风格</p>
          <p class="text-[11px] opacity-70 text-center">点击「立即生成」获取专属口播脚本<br>AI 将结合酒店信息生成多版本脚本 + BGM 建议</p>
        </div>

        <!-- Loading -->
        <div v-if="generating" class="flex flex-col items-center justify-center flex-1 min-h-[400px] gap-5">
          <Loader2 class="w-8 h-8 text-bamboo-800 animate-spin" />
          <div class="space-y-2">
            <div
              v-for="(s, i) in progressSteps" :key="i"
              :class="[
                'text-[11px] flex items-center gap-2.5 transition-colors',
                i < loadingStep ? 'text-bamboo-400' : i === loadingStep ? 'text-bamboo-800 font-medium' : 'text-warm-400'
              ]"
            >
              <CheckCircle2 v-if="i < loadingStep" class="w-3.5 h-3.5 text-bamboo-400" />
              <Loader2 v-else-if="i === loadingStep" class="w-3.5 h-3.5 animate-spin" />
              <span v-else class="w-3.5 h-3.5 rounded-full border border-warm-300" />
              {{ s }}
            </div>
          </div>
        </div>

        <!-- 输出区 -->
        <div v-if="generated" class="flex-1 overflow-auto p-4 space-y-3">
          <!-- 多版本脚本 -->
          <div v-for="v in versions" :key="v.num" class="bg-white border border-cream-200 rounded-lg overflow-hidden">
            <div class="flex items-center justify-between px-4 py-2.5 border-b border-cream-100">
              <div class="flex items-center gap-2">
                <span class="w-5 h-5 rounded-full bg-bamboo-100 text-bamboo-700 flex items-center justify-center text-[10px] font-bold">{{ v.num }}</span>
                <span class="text-[11px] font-medium text-warm-800">{{ v.label }}</span>
                <span :class="['text-[9px] px-1.5 py-0.5 rounded-full font-medium', v.badgeClass]">{{ v.badge }}</span>
              </div>
              <button @click="copyScript(v.text)" class="text-[10px] px-2 py-1 rounded-md border border-cream-200 bg-cream-50 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-700 hover:border-bamboo-300 transition-colors flex items-center gap-0.5">
                <Copy class="w-3 h-3" />复制
              </button>
            </div>
            <div class="px-4 py-3">
              <textarea
                :value="v.text"
                rows="10"
                readonly
                class="w-full text-[12px] leading-relaxed px-3 py-2 rounded-lg border border-cream-200 bg-cream-50 text-warm-800 resize-none"
              />
              <div class="flex justify-between mt-1.5">
                <span class="text-[10px] text-warm-500">{{ v.text.length }} 字</span>
                <span class="text-[10px] text-warm-500">约 {{ Math.round(v.text.length / 4) }} 秒</span>
              </div>
            </div>
          </div>

          <!-- 底部操作栏 -->
          <div class="bg-white border border-cream-200 rounded-lg px-4 py-3 flex items-center justify-between gap-3">
            <span class="text-[11px] text-warm-600">已生成 {{ versions.length }} 个版本 · 可直接复制使用</span>
            <button @click="copyAll" class="text-[12px] px-3 py-1.5 rounded-lg border border-cream-300 bg-white text-warm-700 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
              <Copy class="w-3.5 h-3.5" />复制全部
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
