<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api'
import { Bolt, Loader2, Plus, RefreshCw, X } from 'lucide-vue-next'

interface BillingRule {
  id?: number
  moduleKey: string
  moduleName: string
  board: string
  cost: number
  enabled: boolean
  sortOrder?: number
}

const rules = ref<BillingRule[]>([])
const loading = ref(true)
const toast = ref('')
const showModal = ref(false)
const editingId = ref<number | null>(null)
const formLoading = ref(false)

const form = reactive<BillingRule>({
  moduleKey: '',
  moduleName: '',
  board: '内容生成',
  cost: 0,
  enabled: true,
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/api/admin/billing-rules')
    rules.value = data.data || []
  } catch {
    flash('加载失败')
  } finally {
    loading.value = false
  }
}

function openNew() {
  editingId.value = null
  Object.assign(form, {
    moduleKey: '',
    moduleName: '',
    board: '内容生成',
    cost: 0,
    enabled: true,
  })
  showModal.value = true
}

function openEdit(rule: BillingRule) {
  editingId.value = rule.id || null
  Object.assign(form, {
    moduleKey: rule.moduleKey || '',
    moduleName: rule.moduleName || '',
    board: rule.board || '内容生成',
    cost: Number(rule.cost || 0),
    enabled: rule.enabled !== false,
  })
  showModal.value = true
}

async function save(event: Event) {
  event.preventDefault()
  if (!form.moduleKey.trim() || !form.moduleName.trim()) {
    flash('请填写模块信息')
    return
  }
  formLoading.value = true
  try {
    if (editingId.value) {
      await api.put(`/api/admin/billing-rules/${editingId.value}`, { ...form })
    } else {
      await api.post('/api/admin/billing-rules', { ...form })
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

async function removeRule(id?: number) {
  if (!id || !confirm('确定删除这条计费规则？')) return
  try {
    await api.delete(`/api/admin/billing-rules/${id}`)
    flash('删除成功')
    await load()
  } catch {
    flash('删除失败')
  }
}

async function toggleEnable(rule: BillingRule) {
  const oldValue = rule.enabled
  rule.enabled = !rule.enabled
  try {
    await api.put(`/api/admin/billing-rules/${rule.id}`, { ...rule })
  } catch {
    rule.enabled = oldValue
    flash('保存失败')
  }
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
          <Bolt class="w-4 h-4 text-amber-400" />
          模块计费规则
        </h1>
        <p class="text-xs text-gray-500 mt-1">定义每个 AI 功能生成一次需要消耗多少算力。</p>
      </div>
      <div class="flex gap-2">
        <button @click="load" class="px-3 py-2 rounded-lg bg-gray-900 border border-gray-800 text-xs text-gray-300 hover:bg-gray-800 flex items-center gap-2">
          <RefreshCw class="w-3.5 h-3.5" />
          刷新
        </button>
        <button @click="openNew" class="px-3 py-2 rounded-lg bg-indigo-600 text-white text-xs hover:bg-indigo-500 flex items-center gap-2">
          <Plus class="w-3.5 h-3.5" />
          新增规则
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-24">
      <Loader2 class="w-6 h-6 animate-spin text-gray-600" />
    </div>

    <section v-else class="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
      <table class="w-full text-xs">
        <thead>
          <tr class="border-b border-gray-800 text-gray-500 text-left">
            <th class="py-3 px-5">功能模块</th>
            <th class="py-3 px-5">moduleKey</th>
            <th class="py-3 px-5">分类</th>
            <th class="py-3 px-5">单次消耗</th>
            <th class="py-3 px-5">状态</th>
            <th class="py-3 px-5"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="rule in rules" :key="rule.id" class="border-b border-gray-800/60 hover:bg-gray-800/30">
            <td class="py-3 px-5 text-gray-100 font-medium">{{ rule.moduleName }}</td>
            <td class="py-3 px-5 text-gray-400 font-mono">{{ rule.moduleKey }}</td>
            <td class="py-3 px-5 text-gray-500">{{ rule.board || '-' }}</td>
            <td class="py-3 px-5 text-amber-300 font-mono">{{ rule.cost || 0 }} 算力</td>
            <td class="py-3 px-5">
              <button
                @click="toggleEnable(rule)"
                class="px-2 py-1 rounded text-[11px]"
                :class="rule.enabled !== false ? 'bg-emerald-500/10 text-emerald-300' : 'bg-gray-800 text-gray-500'"
              >
                {{ rule.enabled !== false ? '启用' : '停用' }}
              </button>
            </td>
            <td class="py-3 px-5 text-right space-x-3">
              <button @click="openEdit(rule)" class="text-indigo-300 hover:text-indigo-200">编辑</button>
              <button @click="removeRule(rule.id)" class="text-red-300 hover:text-red-200">删除</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="rules.length === 0" class="py-12 text-center text-xs text-gray-500">
        暂无计费规则
      </div>
    </section>

    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" @click.self="showModal = false">
      <div class="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-md">
        <div class="flex items-center justify-between mb-5">
          <h2 class="text-sm font-medium text-gray-100">{{ editingId ? '编辑计费规则' : '新增计费规则' }}</h2>
          <button @click="showModal = false" class="p-1 rounded hover:bg-gray-800 text-gray-400">
            <X class="w-4 h-4" />
          </button>
        </div>

        <form @submit="save" class="space-y-4">
          <label class="block space-y-1.5">
            <span class="text-xs text-gray-400">moduleKey</span>
            <input v-model="form.moduleKey" placeholder="xhs" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs text-gray-400">功能名称</span>
            <input v-model="form.moduleName" placeholder="小红书图文" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs text-gray-400">分类</span>
            <input v-model="form.board" placeholder="内容生成" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs text-gray-400">单次消耗算力</span>
            <input v-model.number="form.cost" type="number" min="0" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
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
