<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePlanStore, type MarketingPlan } from '@/stores/plan'
import {
  CalendarDays, LayoutGrid, Coins, Gift, Pencil, Instagram, Music,
  MessageCircleHeart, Building, Clock, Package, Camera, Star, ArrowLeft,
  CheckCircle2, TrendingUp, FileText, Plus
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const store = usePlanStore()

const plan = ref<MarketingPlan | null>(null)
const activePhase = ref('all')
const editing = ref(false)
const editName = ref('')
const editFestival = ref('')

function loadPlan() {
  const id = (route.query.id as string) || store.plans[0]?.id
  if (id) {
    plan.value = store.getById(id) || store.plans[0] || null
  } else {
    plan.value = null
  }
}

onMounted(loadPlan)
watch(() => route.query.id, loadPlan)

const badgeClassMap: Record<string, string> = {
  green: 'badge-green',
  amber: 'badge-amber',
  rose: 'badge-rose',
  blue: 'badge-blue',
  purple: 'badge-purple',
}

function statusBadge(status: string): string {
  switch (status) {
    case 'active': return 'badge-green'
    case 'completed': return 'badge-blue'
    default: return ''
  }
}

function statusText(status: string): string {
  switch (status) {
    case 'active': return '进行中'
    case 'completed': return '已完成'
    case 'draft': return '草稿'
  }
  return ''
}

function setPhase(phase: string) {
  activePhase.value = phase
}

function startEdit() {
  if (!plan.value) return
  editName.value = plan.value.name
  editFestival.value = plan.value.festival
  editing.value = true
}

function saveEdit() {
  if (!plan.value) return
  store.update(plan.value.id, { name: editName.value, festival: editFestival.value })
  plan.value = store.getById(plan.value.id) || plan.value
  editing.value = false
}

function setStatus(status: 'draft' | 'active' | 'completed') {
  if (!plan.value) return
  store.update(plan.value.id, { status })
  plan.value = store.getById(plan.value.id) || plan.value
}

function cpLine(el: HTMLElement) {
  const text = el.childNodes[0].textContent?.trim() || ''
  navigator.clipboard.writeText(text).then(() => {
    const orig = el.innerHTML
    el.style.background = '#EAF3DE'
    el.style.color = '#27500A'
    el.innerHTML = text + ' <span style="font-size:10px;color:#27500A">已复制</span>'
    setTimeout(() => { el.innerHTML = orig; el.style.background = ''; el.style.color = '' }, 1500)
  }).catch(() => {})
}

function cp(id: string) {
  const el = document.getElementById(id) as HTMLTextAreaElement
  if (!el) return
  const text = el.value || el.textContent
  navigator.clipboard.writeText(text).then(() => {
    const b = document.querySelector(`[data-cp="${id}"]`)
    if (b) {
      const orig = b.innerHTML
      b.innerHTML = '已复制'
      setTimeout(() => b.innerHTML = orig, 1500)
    }
  }).catch(() => {})
}
</script>

<template>
  <div class="max-w-5xl mx-auto" v-if="plan">
    <!-- 返回 + 头部 -->
    <div class="flex items-center gap-3 mb-5">
      <button @click="router.push('/plans')" class="btn-ghost !px-2">
        <ArrowLeft class="w-4 h-4" />
      </button>
      <div class="flex-1">
        <!-- 编辑模式 -->
        <div v-if="editing" class="flex items-center gap-3">
          <input v-model="editName" class="input-field text-lg font-serif font-semibold max-w-xs" />
          <input v-model="editFestival" class="input-field max-w-[120px]" placeholder="节日" />
          <button @click="saveEdit" class="btn-primary text-xs">保存</button>
          <button @click="editing = false" class="btn-ghost text-xs">取消</button>
        </div>
        <!-- 查看模式 -->
        <div v-else class="flex items-center gap-3">
          <h1 class="text-lg font-serif font-semibold text-bamboo-800">{{ plan.name }}</h1>
          <span :class="['badge text-[10px]', statusBadge(plan.status)]">{{ statusText(plan.status) }}</span>
          <button @click="startEdit" class="btn-ghost !px-2 !py-1">
            <Pencil class="w-3 h-3" />
          </button>
        </div>
        <p class="text-xs text-warm-600 mt-0.5">
          执行周期：{{ plan.period }} ｜ 目标：{{ plan.target }}
        </p>
      </div>
      <!-- 状态切换 -->
      <div class="flex gap-1.5">
        <button
          v-for="s in (['draft','active','completed'] as const)"
          :key="s"
          @click="setStatus(s)"
          :class="[
            'text-[11px] px-2 py-1 rounded-full border transition-all',
            plan.status === s
              ? 'bg-bamboo-700 text-white border-bamboo-700'
              : 'border-cream-300 text-warm-600 hover:border-bamboo-300'
          ]"
        >
          {{ s === 'draft' ? '草稿' : s === 'active' ? '进行中' : '已完成' }}
        </button>
      </div>
    </div>

    <!-- 标签 -->
    <div class="flex gap-2 flex-wrap mb-5" v-if="plan.tags.length">
      <span v-for="t in plan.tags" :key="t" class="badge badge-green">{{ t }}</span>
    </div>

    <!-- KPIs -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
      <div v-for="kpi in plan.kpis" :key="kpi.label" class="card text-center">
        <div class="text-2xl font-bold" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        <div class="text-xs text-warm-600 mt-1">{{ kpi.label }}</div>
      </div>
    </div>

    <!-- 时间线 -->
    <div class="card mb-5">
      <div class="flex items-center gap-2 mb-4">
        <CalendarDays class="w-5 h-5 text-purple-600" />
        <span class="font-medium text-sm">执行时间表 · 四阶段作战</span>
      </div>
      <div class="flex gap-2 flex-wrap mb-4">
        <button
          :class="['px-3 py-1.5 rounded-full text-xs font-medium transition-all', activePhase === 'all' ? 'bg-bamboo-700 text-white' : 'bg-cream-200 text-warm-600 hover:bg-cream-300']"
          @click="setPhase('all')"
        >全部</button>
        <button
          v-for="ph in plan.phases"
          :key="ph.id"
          :class="['px-3 py-1.5 rounded-full text-xs font-medium transition-all', activePhase === ph.id ? 'bg-bamboo-700 text-white' : 'bg-cream-200 text-warm-600 hover:bg-cream-300']"
          @click="setPhase(ph.id)"
        >{{ ph.emoji }} {{ ph.title.replace('第', '').replace('阶段：', '') }}</button>
      </div>
      <div class="space-y-4">
        <div
          v-for="ph in plan.phases"
          :key="ph.id"
          v-show="activePhase === 'all' || activePhase === ph.id"
          class="flex gap-3 pb-4 border-b border-cream-300/60 last:border-0"
        >
          <div class="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0" :style="{ background: ph.dotBg }">
            <span>{{ ph.emoji }}</span>
          </div>
          <div class="flex-1">
            <div class="flex items-center gap-2 flex-wrap mb-2">
              <span class="font-medium text-sm">{{ ph.title }}</span>
              <span class="text-xs text-warm-600 bg-cream-200 px-2 py-0.5 rounded-full">{{ ph.dateRange }}</span>
              <span :class="['badge', badgeClassMap[ph.badgeClass] || '']">{{ ph.badgeLabel }}</span>
            </div>
            <div class="space-y-2">
              <div v-for="(t, ti) in ph.tasks" :key="ti" class="bg-cream-100 rounded-lg p-2 text-xs text-warm-600">
                <strong :style="{ color: t.channelColor }">{{ t.channel }}：</strong>{{ t.content }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 渠道 -->
    <div class="card mb-5">
      <div class="flex items-center gap-2 mb-4">
        <LayoutGrid class="w-5 h-5 text-rose-500" />
        <span class="font-medium text-sm">各渠道内容计划</span>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div v-for="ch in plan.channels" :key="ch.name" class="border border-cream-300 rounded-xl p-4">
          <div class="flex items-center gap-3 mb-3">
            <div class="w-8 h-8 rounded-lg flex items-center justify-center" :style="{ background: ch.iconBg }">
              <component :is="(ch.icon === 'Instagram' ? Instagram : ch.icon === 'Music' ? Music : ch.icon === 'MessageCircleHeart' ? MessageCircleHeart : Building)" class="w-4 h-4" :style="{ color: ch.iconColor }" />
            </div>
            <div>
              <div class="font-medium text-sm">{{ ch.name }}</div>
              <div class="text-xs text-warm-600">{{ ch.sub }}</div>
            </div>
          </div>
          <div class="space-y-1">
            <div v-for="item in ch.items" :key="item" class="text-xs text-warm-600 pl-3 relative before:content-['·'] before:absolute before:left-0">
              {{ item }}
            </div>
          </div>
          <div class="flex gap-2 mt-3">
            <span v-for="(tag, ti) in ch.tags" :key="ti" :class="['badge', badgeClassMap[tag.badgeClass] || '']">{{ tag.label }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 定价 -->
    <div class="card mb-5 overflow-x-auto">
      <div class="flex items-center gap-2 mb-4">
        <Coins class="w-5 h-5 text-warm-500" />
        <span class="font-medium text-sm">定价策略 · 四阶段动态定价</span>
      </div>
      <table class="w-full text-xs">
        <thead>
          <tr class="bg-cream-100">
            <th class="px-3 py-2 text-left text-warm-600">阶段</th>
            <th class="px-3 py-2 text-left text-warm-600">竹语大床（基础¥888）</th>
            <th class="px-3 py-2 text-left text-warm-600">山景套房（基础¥1388）</th>
            <th class="px-3 py-2 text-left text-warm-600">亲子家庭（基础¥1688）</th>
            <th class="px-3 py-2 text-left text-warm-600">定价逻辑</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="pr in plan.pricings" :key="pr.stage" class="border-b border-cream-300/60 last:border-0">
            <td>
              <span :class="['badge', badgeClassMap[pr.stageBadge] || '']" v-html="pr.stage" />
            </td>
            <td v-for="(p, pi) in pr.prices" :key="pi">
              <span class="font-bold text-bamboo-600" v-html="p" />
            </td>
            <td class="text-warm-600">{{ pr.logic }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="plan.alertNote" class="mt-4 text-xs text-warm-600 bg-cream-100 rounded-lg p-3">
        {{ plan.alertNote }}
      </div>
    </div>

    <!-- 活动 -->
    <div class="card mb-5">
      <div class="flex items-center gap-2 mb-4">
        <Gift class="w-5 h-5 text-rose-500" />
        <span class="font-medium text-sm">活动设计 · 四套组合拳</span>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div v-for="act in plan.activities" :key="act.title" class="border border-cream-300 rounded-xl p-4">
          <div class="flex items-center gap-2 mb-2">
            <component :is="act.icon === 'Clock' ? Clock : act.icon === 'Package' ? Package : act.icon === 'Camera' ? Camera : Star" class="w-4 h-4" :style="{ color: act.iconColor }" />
            <span class="font-medium text-sm">{{ act.title }}</span>
          </div>
          <div class="text-xs text-warm-600 mb-2">{{ act.desc }}</div>
          <div class="text-xs text-warm-600">{{ act.goal }}</div>
          <div class="mt-2">
            <span :class="['badge', badgeClassMap[act.badgeClass] || '']">{{ act.tag }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 文案示例 -->
    <div class="card mb-5">
      <div class="flex items-center gap-2 mb-4">
        <Pencil class="w-5 h-5 text-purple-600" />
        <span class="font-medium text-sm">核心文案示例 · 点击复制</span>
      </div>
      <div class="mb-4">
        <div class="text-xs font-medium text-warm-600 uppercase tracking-wide mb-2">小红书爆款标题备选</div>
        <div class="space-y-2">
          <div
            v-for="(line, li) in ['烟雨端午，我在莫干山竹林里等你','端午去哪儿？莫干山这家竹林民宿仅剩3间','不想人挤人？端午躲进莫干山竹林才是正解','莫干山私汤民宿｜端午3天，竹林雨夜只有我们']"
            :key="li"
            class="bg-cream-100 rounded-lg px-3 py-2 text-sm cursor-pointer hover:bg-bamboo-50 hover:text-bamboo-800 transition-colors"
            @click="cpLine($event.target as HTMLElement)"
          >
            {{ line }} <span class="text-xs text-warm-600 ml-2">点击复制</span>
          </div>
        </div>
      </div>
      <div class="mb-4">
        <div class="text-xs font-medium text-warm-600 uppercase tracking-wide mb-2">朋友圈 · 冲刺期晚间版</div>
        <div class="relative">
          <textarea id="wc1" rows="4" class="w-full text-xs p-3 rounded-lg border border-cream-300 bg-cream-100 resize-none" readonly>端午还有最后2间全景竹房

窗外是整片竹海，私汤一泡，龙舟节也跟我没关系了。

今天订，可以把端午3天的竹林都圈给你——文末扣1，直接给老粉价，比平台便宜。</textarea>
          <button data-cp="wc1" class="btn-ghost absolute bottom-2 right-2 text-xs" @click="cp('wc1')">复制</button>
        </div>
      </div>
      <div>
        <div class="text-xs font-medium text-warm-600 uppercase tracking-wide mb-2">抖音口播 · 冲刺期</div>
        <div class="relative">
          <textarea id="dy1" rows="5" class="w-full text-xs p-3 rounded-lg border border-cream-300 bg-cream-100 resize-none" readonly>【镜头：推开竹林小径，雨滴打在竹叶上】

总要来趟莫干山吧——

下雨天的竹林，才是这里最好看的样子。端午还有最后2间竹景房，喜欢这种感觉的家人私信我，粉丝专属价。

刷到这个视频的，就是有缘人。</textarea>
          <button data-cp="dy1" class="btn-ghost absolute bottom-2 right-2 text-xs" @click="cp('dy1')">复制</button>
        </div>
      </div>
    </div>

    <!-- 提醒 -->
    <div class="space-y-3 mb-5">
      <div
        v-for="(al, ai) in plan.alerts"
        :key="ai"
        :class="[
          'border-l-3 rounded-r-lg p-3 text-sm',
          al.bgClass === 'bamboo' ? 'bg-bamboo-50 border-bamboo-600 text-bamboo-800' :
          al.bgClass === 'amber' ? 'bg-amber-50 border-amber-500 text-amber-800' :
          'bg-purple-50 border-purple-500 text-purple-800'
        ]"
        v-html="al.html"
      />
    </div>

    <!-- 操作按钮 -->
    <div class="flex flex-wrap gap-3">
      <button class="btn-primary">生成礼包物料清单 ↗</button>
      <button class="btn-secondary">生成小红书内容套装 ↗</button>
      <button class="btn-secondary">生成活动话术脚本 ↗</button>
    </div>
  </div>

  <!-- 空状态 -->
  <div v-else class="card text-center py-16">
    <FileText class="w-10 h-10 text-warm-600/30 mx-auto mb-3" />
    <p class="text-warm-600 mb-3">暂无方案数据</p>
    <button @click="router.push('/plans')" class="btn-primary">
      <Plus class="w-4 h-4" />
      去新建方案
    </button>
  </div>
</template>
