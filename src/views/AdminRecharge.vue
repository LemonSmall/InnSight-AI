<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api'
import { CreditCard, Loader2, Plus, RefreshCw, X } from 'lucide-vue-next'

interface RechargePackage {
  id?: number
  name: string
  credits: number
  priceRmb: number
  applicableTiers: string
  enabled: boolean
}

const packages = ref<RechargePackage[]>([])
const loading = ref(true)
const toast = ref('')
const showModal = ref(false)
const editingId = ref<number | null>(null)
const formLoading = ref(false)

const form = reactive<RechargePackage>({
  name: '',
  credits: 0,
  priceRmb: 0,
  applicableTiers: 'basic,pro',
  enabled: true,
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/api/admin/packages')
    packages.value = data.data || []
  } catch {
    flash('加载失败')
  } finally {
    loading.value = false
  }
}

function openNew() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    credits: 0,
    priceRmb: 0,
    applicableTiers: 'basic,pro',
    enabled: true,
  })
  showModal.value = true
}

function openEdit(pkg: RechargePackage) {
  editingId.value = pkg.id || null
  Object.assign(form, {
    name: pkg.name || '',
    credits: Number(pkg.credits || 0),
    priceRmb: Number(pkg.priceRmb || 0),
    applicableTiers: pkg.applicableTiers || 'basic,pro',
    enabled: pkg.enabled !== false,
  })
  showModal.value = true
}

async function save(event: Event) {
  event.preventDefault()
  if (!form.name.trim()) {
    flash('请填写套餐名称')
    return
  }
  formLoading.value = true
  try {
    if (editingId.value) {
      await api.put(`/api/admin/packages/${editingId.value}`, { ...form })
    } else {
      await api.post('/api/admin/packages', { ...form })
    }
    showModal.value = false
    flash('保存成功')
    await load()
  } catch {
    flash('保存失败')
  } finally {
    formLoading.value = false
  }
}

async function removePackage(id?: number) {
  if (!id || !confirm('确定删除这个充值套餐？')) return
  try {
    await api.delete(`/api/admin/packages/${id}`)
    flash('删除成功')
    await load()
  } catch {
    flash('删除失败')
  }
}

async function toggleEnable(pkg: RechargePackage) {
  const oldValue = pkg.enabled
  pkg.enabled = !pkg.enabled
  try {
    await api.put(`/api/admin/packages/${pkg.id}`, { ...pkg })
  } catch {
    pkg.enabled = oldValue
    flash('保存失败')
  }
}

function tierText(value: string) {
  if (!value) return '全部套餐'
  return value
    .split(',')
    .map(item => ({ trial: '试用版', basic: '基础版', pro: '专业版', chain: '连锁版', flagship: '旗舰版' }[item] || item))
    .join('、')
}

function flash(message: string) {
  toast.value = message
  setTimeout(() => { toast.value = '' }, 2200)
}
</script>

<template>
  <div class="p-6 space-y-5">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2 rounded-lg text-xs shadow-lg">
      {{ toast }}
    </div>

    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-base font-semibold text-gray-100 flex items-center gap-2">
          <CreditCard class="w-4 h-4 text-emerald-400" />
          充值套餐
        </h1>
        <p class="text-xs text-gray-500 mt-1">配置租户购买算力的套餐，并记录平台充值与消耗流水。</p>
      </div>
      <div class="flex gap-2">
        <button @click="load" class="px-3 py-2 rounded-lg bg-gray-900 border border-gray-800 text-xs text-gray-300 hover:bg-gray-800 flex items-center gap-2">
          <RefreshCw class="w-3.5 h-3.5" />
          刷新
        </button>
        <button @click="openNew" class="px-3 py-2 rounded-lg bg-indigo-600 text-white text-xs hover:bg-indigo-500 flex items-center gap-2">
          <Plus class="w-3.5 h-3.5" />
          新增套餐
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-24">
      <Loader2 class="w-6 h-6 animate-spin text-gray-600" />
    </div>

    <template v-else>
      <div v-if="packages.length === 0" class="bg-gray-900 border border-gray-800 rounded-xl py-12 text-center text-xs text-gray-500">
        暂无充值套餐
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
        <div
          v-for="pkg in packages"
          :key="pkg.id"
          class="bg-gray-900 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-colors"
        >
          <div class="flex items-start justify-between gap-3">
            <div>
              <h2 class="text-sm font-semibold text-gray-100">{{ pkg.name }}</h2>
              <p class="text-[11px] text-gray-500 mt-1">{{ tierText(pkg.applicableTiers) }}</p>
            </div>
            <button
              @click="toggleEnable(pkg)"
              class="px-2 py-1 rounded text-[11px]"
              :class="pkg.enabled !== false ? 'bg-emerald-500/10 text-emerald-300' : 'bg-gray-800 text-gray-500'"
            >
              {{ pkg.enabled !== false ? '启用' : '停用' }}
            </button>
          </div>

          <div class="mt-5">
            <div class="text-3xl font-semibold text-white">{{ Number(pkg.credits || 0).toLocaleString() }}</div>
            <div class="text-xs text-gray-500 mt-1">算力额度</div>
          </div>

          <div class="mt-5 flex items-end justify-between">
            <div>
              <div class="text-xs text-gray-500">售价</div>
              <div class="text-xl font-semibold text-indigo-300">¥{{ Number(pkg.priceRmb || 0).toLocaleString() }}</div>
            </div>
            <div class="space-x-3 text-xs">
              <button @click="openEdit(pkg)" class="text-indigo-300 hover:text-indigo-200">编辑</button>
              <button @click="removePackage(pkg.id)" class="text-red-300 hover:text-red-200">删除</button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" @click.self="showModal = false">
      <div class="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-md">
        <div class="flex items-center justify-between mb-5">
          <h2 class="text-sm font-medium text-gray-100">{{ editingId ? '编辑充值套餐' : '新增充值套餐' }}</h2>
          <button @click="showModal = false" class="p-1 rounded hover:bg-gray-800 text-gray-400">
            <X class="w-4 h-4" />
          </button>
        </div>

        <form @submit="save" class="space-y-4">
          <label class="block space-y-1.5">
            <span class="text-xs text-gray-400">套餐名称</span>
            <input v-model="form.name" placeholder="标准包" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
          </label>
          <div class="grid grid-cols-2 gap-4">
            <label class="block space-y-1.5">
              <span class="text-xs text-gray-400">算力数量</span>
              <input v-model.number="form.credits" type="number" min="0" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
            </label>
            <label class="block space-y-1.5">
              <span class="text-xs text-gray-400">价格</span>
              <input v-model.number="form.priceRmb" type="number" min="0" step="0.01" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
            </label>
          </div>
          <label class="block space-y-1.5">
            <span class="text-xs text-gray-400">适用版本</span>
            <select v-model="form.applicableTiers" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500">
              <option value="trial">试用版</option>
              <option value="basic,pro">基础版、专业版</option>
              <option value="pro,chain">专业版、连锁版</option>
              <option value="basic,pro,chain">全部正式版本</option>
            </select>
          </label>
          <label class="flex items-center justify-between rounded-lg bg-gray-800 border border-gray-700 px-3 py-2">
            <span class="text-xs text-gray-300">启用</span>
            <input v-model="form.enabled" type="checkbox" class="w-4 h-4 accent-indigo-600" />
          </label>

          <div class="flex justify-end gap-2 pt-2">
            <button type="button" @click="showModal = false" class="px-4 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-300 text-xs hover:bg-gray-700">
              取消
            </button>
            <button type="submit" :disabled="formLoading" class="px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs hover:bg-indigo-500 disabled:bg-gray-700 flex items-center gap-2">
              <Loader2 v-if="formLoading" class="w-3.5 h-3.5 animate-spin" />
              保存
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
