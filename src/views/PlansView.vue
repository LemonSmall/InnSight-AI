<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlanStore, type MarketingPlan } from '@/stores/plan'
import { Plus, FileText, Copy, Trash2, Search, Calendar, TrendingUp, CheckCircle2, Clock, ArrowLeft } from 'lucide-vue-next'

const store = usePlanStore()
const router = useRouter()

onMounted(() => { store.loadFromApi() })

const showNew = ref(false)
const newName = ref('')
const newFestival = ref('')
const searchQuery = ref('')

function filteredPlans(): MarketingPlan[] {
  if (!searchQuery.value.trim()) return store.plans
  const q = searchQuery.value.toLowerCase()
  return store.plans.filter(p =>
    p.name.toLowerCase().includes(q) ||
    p.festival.toLowerCase().includes(q) ||
    p.hotelName.toLowerCase().includes(q)
  )
}

function openPlan(plan: MarketingPlan) {
  router.push({ path: '/plan', query: { id: plan.id } })
}

async function doCreate() {
  if (!newName.value.trim() || !newFestival.value.trim()) return
  const plan = await store.create(newName.value.trim(), newFestival.value.trim())
  showNew.value = false
  newName.value = ''
  newFestival.value = ''
  router.push({ path: '/plan', query: { id: plan.id } })
}

async function doDuplicate(id: string) {
  const clone = await store.duplicate(id)
  if (clone) router.push({ path: '/plan', query: { id: clone.id } })
}

function doRemove(id: string, name: string) {
  if (confirm(`确定删除「${name}」吗？此操作不可恢复。`)) {
    store.remove(id)
    // 如果当前正在查看被删除的方案，回到列表
    if (router.currentRoute.value.path === '/plan') {
      router.push('/plans')
    }
  }
}

function statusBadge(status: string): string {
  switch (status) {
    case 'active': return 'badge-green'
    case 'completed': return 'badge-blue'
    case 'draft': return ''
  }
  return ''
}

function statusText(status: string): string {
  switch (status) {
    case 'active': return '进行中'
    case 'completed': return '已完成'
    case 'draft': return '草稿'
  }
  return ''
}

const festivals = ['春节', '清明', '五一', '端午', '中秋', '国庆', '元旦', '暑假', '寒假', '周末']

function selectFestival(f: string) {
  newFestival.value = f
}
</script>

<template>
  <div class="max-w-5xl mx-auto">
    <!-- 页面标题 + 新建 -->
    <div class="flex items-center justify-between mb-5">
      <div class="flex items-center gap-3">
        <button @click="router.push('/strategy')" class="w-7 h-7 flex items-center justify-center rounded-lg border border-cream-300 text-warm-600 hover:bg-bamboo-50 hover:text-bamboo-800 hover:border-bamboo-400 transition-colors">
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-lg font-serif font-semibold text-bamboo-800 flex items-center gap-2">
            <FileText class="w-5 h-5 text-bamboo-600" />
            营销方案记录
          </h1>
          <p class="text-xs text-warm-600 mt-0.5">管理所有节日/活动的营销方案，支持新建、编辑、归档</p>
        </div>
      </div>
      <button @click="showNew = true" class="btn-primary">
        <Plus class="w-4 h-4" />
        新建方案
      </button>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-3 gap-3 mb-5">
      <div class="card text-center">
        <div class="text-2xl font-bold text-bamboo-700">{{ store.activePlans.length }}</div>
        <div class="text-xs text-warm-600 flex items-center justify-center gap-1 mt-1">
          <TrendingUp class="w-3 h-3" /> 进行中
        </div>
      </div>
      <div class="card text-center">
        <div class="text-2xl font-bold text-blue-600">{{ store.completedPlans.length }}</div>
        <div class="text-xs text-warm-600 flex items-center justify-center gap-1 mt-1">
          <CheckCircle2 class="w-3 h-3" /> 已完成
        </div>
      </div>
      <div class="card text-center">
        <div class="text-2xl font-bold text-warm-600">{{ store.draftPlans.length }}</div>
        <div class="text-xs text-warm-600 flex items-center justify-center gap-1 mt-1">
          <Clock class="w-3 h-3" /> 草稿
        </div>
      </div>
    </div>

    <!-- 搜索 -->
    <div class="relative mb-4">
      <Search class="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-warm-600/40" />
      <input
        v-model="searchQuery"
        type="text"
        placeholder="搜索方案名称、节日或酒店..."
        class="w-full pl-9 pr-4 py-2 rounded-lg border border-cream-300 text-sm bg-white focus:outline-none focus:border-bamboo-400"
      />
    </div>

    <!-- 方案列表 -->
    <div v-if="filteredPlans().length === 0" class="card text-center py-10 text-warm-600 text-sm">
      {{ searchQuery ? '没有匹配的方案' : '暂无方案记录，点击上方「新建方案」创建第一个' }}
    </div>

    <div class="space-y-3">
      <div
        v-for="plan in filteredPlans()"
        :key="plan.id"
        class="card hover:shadow-md transition-all cursor-pointer group"
        @click="openPlan(plan)"
      >
        <div class="flex items-start justify-between">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap mb-1">
              <span class="font-medium text-sm text-bamboo-800">{{ plan.name }}</span>
              <span :class="['badge text-[10px]', statusBadge(plan.status)]">{{ statusText(plan.status) }}</span>
            </div>
            <div class="flex items-center gap-3 text-xs text-warm-600 flex-wrap">
              <span class="flex items-center gap-1">
                <Calendar class="w-3 h-3" /> {{ plan.period || '未设置周期' }}
              </span>
              <span>{{ plan.hotelName }}</span>
            </div>
            <div v-if="plan.tags.length" class="flex gap-1.5 mt-2 flex-wrap">
              <span
                v-for="t in plan.tags"
                :key="t"
                class="text-[10px] px-2 py-0.5 rounded-full bg-cream-200 text-warm-600"
              >{{ t }}</span>
            </div>
            <div class="text-xs text-warm-600/60 mt-2">
              创建 {{ plan.createdAt }} · 更新 {{ plan.updatedAt }}
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0 ml-3" @click.stop>
            <button
              @click.stop="doDuplicate(plan.id)"
              class="btn-ghost !px-2 !py-1 text-xs"
              title="复制方案"
            >
              <Copy class="w-3.5 h-3.5" />
            </button>
            <button
              @click.stop="doRemove(plan.id, plan.name)"
              class="btn-ghost !px-2 !py-1 text-xs text-rose-400 hover:text-rose-500"
              title="删除"
            >
              <Trash2 class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建弹窗 -->
    <div v-if="showNew" class="fixed inset-0 bg-bamboo-950/40 flex items-center justify-center z-50 p-4" @click.self="showNew = false">
      <div class="bg-white rounded-2xl p-6 w-full max-w-md shadow-xl">
        <div class="text-base font-serif font-semibold text-bamboo-800 mb-5">新建营销方案</div>

        <div class="mb-4">
          <label class="label">方案名称</label>
          <input v-model="newName" class="input-field" placeholder="如：端午爆单全案 v2" @keyup.enter="doCreate" />
        </div>

        <div class="mb-5">
          <label class="label">节日/活动</label>
          <div class="flex flex-wrap gap-2 mb-2">
            <button
              v-for="f in festivals"
              :key="f"
              @click="selectFestival(f)"
              :class="[
                'text-xs px-3 py-1 rounded-full border transition-all',
                newFestival === f
                  ? 'bg-bamboo-700 text-white border-bamboo-700'
                  : 'border-cream-300 text-warm-600 hover:border-bamboo-300'
              ]"
            >{{ f }}</button>
          </div>
          <input v-model="newFestival" class="input-field" placeholder="或手动输入" />
        </div>

        <div class="flex gap-3">
          <button @click="doCreate" class="btn-primary flex-1 justify-center" :disabled="!newName || !newFestival">
            创建并编辑
          </button>
          <button @click="showNew = false" class="btn-secondary">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>
